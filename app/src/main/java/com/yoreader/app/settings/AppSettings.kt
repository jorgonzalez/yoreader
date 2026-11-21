package com.yoreader.app.settings

import android.content.Context
import com.yoreader.app.data.ArticleRetention
import com.yoreader.app.tts.TtsLanguages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The user's choices from the Options screen, kept on the phone (SharedPreferences)
 * and offered as flows so screens update as soon as something changes.
 */
class AppSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _textZoom = MutableStateFlow(TextZoom.snap(prefs.getInt(KEY_TEXT_ZOOM, TextZoom.DEFAULT)))

    /** Size of article text in the reader and in cleaned pages, in percent (100 = normal). */
    val textZoom: StateFlow<Int> = _textZoom.asStateFlow()

    fun setTextZoom(percent: Int) {
        val value = TextZoom.snap(percent)
        if (value == _textZoom.value) return
        _textZoom.value = value
        prefs.edit().putInt(KEY_TEXT_ZOOM, value).apply()
    }

    private val _nostrAlsoNote = MutableStateFlow(prefs.getBoolean(KEY_NOSTR_ALSO_NOTE, false))

    /** When sharing on Nostr, also post a plain note (seen in every Nostr app). */
    val nostrAlsoNote: StateFlow<Boolean> = _nostrAlsoNote.asStateFlow()

    fun setNostrAlsoNote(on: Boolean) {
        _nostrAlsoNote.value = on
        prefs.edit().putBoolean(KEY_NOSTR_ALSO_NOTE, on).apply()
    }

    private val _keepReadDays = MutableStateFlow(
        prefs.getInt(KEY_KEEP_READ_DAYS, ArticleRetention.DEFAULT_DAYS)
            .takeIf { it in ArticleRetention.CHOICES } ?: ArticleRetention.DEFAULT_DAYS
    )

    /** Read articles older than this many days are removed (saved ones are kept); 0 = never. */
    val keepReadDays: StateFlow<Int> = _keepReadDays.asStateFlow()

    fun setKeepReadDays(days: Int) {
        if (days !in ArticleRetention.CHOICES || days == _keepReadDays.value) return
        _keepReadDays.value = days
        prefs.edit().putInt(KEY_KEEP_READ_DAYS, days).apply()
    }

    private val _ttsLanguages = MutableStateFlow(
        prefs.getStringSet(KEY_TTS_LANGUAGES, null)
            ?.filter { TtsLanguages.byId(it) != null }?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?: TtsLanguages.DEFAULT_ENABLED
    )

    /** Ids of the read-aloud languages turned on ([TtsLanguages]); never empty. */
    val ttsLanguages: StateFlow<Set<String>> = _ttsLanguages.asStateFlow()

    /** Turns a read-aloud language on or off; the last one left can't be turned off. */
    fun setTtsLanguage(id: String, on: Boolean) {
        if (TtsLanguages.byId(id) == null) return
        val now = _ttsLanguages.value
        val next = if (on) now + id else now - id
        if (next.isEmpty() || next == now) return
        _ttsLanguages.value = next
        prefs.edit().putStringSet(KEY_TTS_LANGUAGES, next).apply()
    }

    private companion object {
        const val PREFS = "settings"
        const val KEY_TEXT_ZOOM = "text_zoom"
        const val KEY_KEEP_READ_DAYS = "keep_read_days"
        const val KEY_TTS_LANGUAGES = "tts_languages"
        const val KEY_NOSTR_ALSO_NOTE = "nostr_also_note"
    }
}

/** Article text sizes offered: 80% to 150% in steps of 5. */
object TextZoom {
    const val MIN = 80
    const val MAX = 150
    const val STEP = 5
    const val DEFAULT = 100

    /** Number of stops between [MIN] and [MAX] (for a stepped slider). */
    const val STEPS_BETWEEN = (MAX - MIN) / STEP - 1

    /** The nearest offered size to [percent]. */
    fun snap(percent: Int): Int {
        val clamped = percent.coerceIn(MIN, MAX)
        return MIN + Math.round((clamped - MIN).toFloat() / STEP) * STEP
    }
}
