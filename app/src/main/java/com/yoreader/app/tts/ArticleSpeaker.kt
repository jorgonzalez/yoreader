package com.yoreader.app.tts

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

/**
 * Reads one article at a time with Android's TextToSpeech. App-wide (lives in the
 * Application), so reading continues while the user moves around the app.
 */
class ArticleSpeaker(context: Context) {

    enum class Status { IDLE, PREPARING, SPEAKING, PAUSED }

    data class State(
        val status: Status = Status.IDLE,
        val articleId: Long? = null,
        val title: String = "",
        /** 0-based index of the chunk being read, and how many there are. */
        val position: Int = 0,
        val total: Int = 0,
        /** Shown once by the UI when something went wrong, e.g. no Spanish voice. */
        val error: String? = null,
        /** Set when reading a web page opened in the app rather than a stored article. */
        val pageUrl: String? = null,
    ) {
        val isActive get() = articleId != null && status != Status.IDLE
    }

    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _rate = MutableStateFlow(prefs.getFloat(KEY_RATE, SpeechRate.NORMAL))
    /** Speech speed, kept across articles and app restarts. */
    val rate: StateFlow<Float> = _rate

    private var tts: TextToSpeech? = null
    private var engineReady = false
    /** Runs once the engine has initialized. */
    private var pendingStart: (() -> Unit)? = null

    private var chunks: List<String> = emptyList()
    /**
     * Character offset, within the chunk being read, of the word being spoken (from
     * onRangeStart). Lets a speed change or pause continue near where the listener is,
     * instead of from the start of the chunk. Stays 0 on engines that don't report it.
     */
    @Volatile private var spokenOffset = 0
    /** Where to continue after a pause: chunk offset of the interrupted sentence/word. */
    private var resumeOffset = 0
    private var locale: Locale = Locale.getDefault()
    /** Bumped on every start/stop so callbacks from an older run are ignored. */
    @Volatile private var session = 0

    /**
     * Starts reading [paragraphs] (title first) in [language] ("es"/"en"/null for the
     * device default). Replaces whatever was being read. [pageUrl] marks a web page
     * (not a stored article), so the UI can link back to it.
     */
    fun play(
        articleId: Long,
        title: String,
        paragraphs: List<String>,
        language: String?,
        pageUrl: String? = null,
    ) {
        val text = SpeechText.chunks(listOf(title) + paragraphs)
        if (text.isEmpty()) return
        session++
        chunks = text
        locale = when (language) {
            "es" -> Locale("es", "ES")
            "en" -> Locale.US
            else -> Locale.getDefault()
        }
        _state.value = State(Status.PREPARING, articleId, title, 0, text.size, pageUrl = pageUrl)
        withEngine { speakFrom(0) }
    }

    fun pause() {
        val s = _state.value
        if (s.status != Status.SPEAKING) return
        session++ // the stop below triggers callbacks we don't want to act on
        tts?.stop()
        resumeOffset = currentResumePoint(s.position)
        _state.update { it.copy(status = Status.PAUSED) }
    }

    /** Continues from the sentence (or word) that was interrupted. */
    fun resume() {
        val s = _state.value
        if (s.status != Status.PAUSED) return
        session++
        _state.update { it.copy(status = Status.PREPARING) }
        val offset = resumeOffset
        withEngine { speakFrom(s.position, offset) }
    }

    fun stop() {
        session++
        pendingStart = null
        tts?.stop()
        _state.value = State()
    }

    fun faster() = setRate(SpeechRate.faster(_rate.value))

    fun slower() = setRate(SpeechRate.slower(_rate.value))

    private fun setRate(value: Float) {
        if (value == _rate.value) return
        _rate.value = value
        prefs.edit().putFloat(KEY_RATE, value).apply()
        // Engines apply a new rate only to text queued after it, so re-queue from the
        // sentence being read (or the word, in a very long sentence) at the new speed.
        val s = _state.value
        if (s.status == Status.SPEAKING && engineReady) {
            val offset = currentResumePoint(s.position)
            session++
            tts?.stop()
            speakFrom(s.position, offset)
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun withEngine(action: () -> Unit) {
        val engine = tts
        if (engine != null && engineReady) {
            action()
            return
        }
        pendingStart = action
        if (engine != null) return // still initializing
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engineReady = true
                tts?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                tts?.setOnUtteranceProgressListener(listener)
                pendingStart?.invoke()
                pendingStart = null
            } else {
                tts = null
                pendingStart = null
                fail("Text-to-speech isn't available on this device")
            }
        }
    }

    private fun currentResumePoint(index: Int): Int =
        chunks.getOrNull(index)?.let { SpeechText.resumePoint(it, spokenOffset) } ?: 0

    /** Queues chunk [index] from character [offset], then all following chunks. */
    private fun speakFrom(index: Int, offset: Int = 0) {
        val engine = tts ?: return
        val result = engine.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            val name = locale.getDisplayLanguage(Locale.ENGLISH)
            fail("No $name voice installed. Add one in Android Settings › Text-to-speech.")
            return
        }
        engine.setSpeechRate(_rate.value)
        val run = session
        // Nothing left in this chunk (interrupted on its last word): start at the next.
        var first = index
        var start = offset
        if (chunks.getOrNull(first)?.substring(start.coerceAtMost(chunks[first].length))?.isBlank() == true) {
            first++
            start = 0
        }
        if (first >= chunks.size) {
            session++
            _state.value = State()
            return
        }
        spokenOffset = start
        Log.d(TAG, "Reading part ${first + 1}/${chunks.size} from character $start of ${chunks[first].length}")
        for (i in first until chunks.size) {
            val mode = if (i == first) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val from = if (i == first) start else 0
            engine.speak(chunks[i].substring(from), mode, null, "$run:$i:$from")
        }
        _state.update { it.copy(status = Status.SPEAKING, position = first) }
    }

    private fun fail(message: String) {
        session++
        _state.value = State(error = message)
    }

    private companion object {
        const val TAG = "ArticleSpeaker"
        const val PREFS = "speech"
        const val KEY_RATE = "rate"
    }

    /** Utterance ids are "<session>:<chunk index>:<offset of the spoken text in the chunk>". */
    private data class Utterance(val run: Int, val index: Int, val offset: Int)

    private fun parse(id: String?): Utterance? {
        val parts = id?.split(':') ?: return null
        val run = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val index = parts.getOrNull(1)?.toIntOrNull() ?: return null
        return Utterance(run, index, parts.getOrNull(2)?.toIntOrNull() ?: 0)
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            val (run, index, offset) = parse(utteranceId) ?: return
            if (run != session) return
            spokenOffset = offset
            _state.update { it.copy(status = Status.SPEAKING, position = index) }
        }

        /** The engine is about to speak text[start, end) of this utterance. */
        override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
            val (run, _, offset) = parse(utteranceId) ?: return
            if (run != session) return
            spokenOffset = offset + start
        }

        override fun onDone(utteranceId: String?) {
            val (run, index, _) = parse(utteranceId) ?: return
            if (run != session) return
            if (index == chunks.lastIndex) {
                session++
                _state.value = State() // finished the article
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            val (run, _, _) = parse(utteranceId) ?: return
            if (run == session) fail("Text-to-speech stopped with an error")
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            val (run, _, _) = parse(utteranceId) ?: return
            if (run == session) fail("Text-to-speech stopped with an error ($errorCode)")
        }
    }
}
