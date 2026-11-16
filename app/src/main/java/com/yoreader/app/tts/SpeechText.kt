package com.yoreader.app.tts

import java.util.Locale

/** Text preparation for read-aloud: language guess and chunking. */
object SpeechText {

    private val WORD = Regex("[\\p{L}']+")

    private val SPANISH = setOf(
        "de", "la", "que", "el", "en", "y", "los", "las", "del", "se", "por", "con",
        "para", "una", "un", "es", "al", "lo", "como", "más", "pero", "sus", "le", "ya",
        "o", "este", "esta", "entre", "cuando", "muy", "sin", "sobre", "también", "fue", "ha",
    )
    private val ENGLISH = setOf(
        "the", "of", "and", "to", "in", "is", "that", "for", "it", "with", "as", "on",
        "was", "are", "this", "be", "by", "not", "you", "at", "from", "have", "has", "but",
        "they", "his", "her", "which", "were", "their", "will", "would", "an", "or", "its",
    )

    /** Letters and marks that (almost) only Spanish uses among the two languages. */
    private val SPANISH_CHARS = setOf('ñ', 'á', 'é', 'í', 'ó', 'ú', '¿', '¡')

    /**
     * "es" or "en" for [text], or null when it can't tell. Counts the most common
     * function words of each language; Spanish-only characters (ñ, accents, ¿ ¡) add
     * strong evidence, so even a short summary is usually enough.
     */
    fun guessLanguage(text: String): String? {
        val sample = text.take(5_000)
        var es = 0.0
        var en = 0.0
        for (match in WORD.findAll(sample)) {
            val w = match.value.lowercase(Locale.ROOT)
            if (w in SPANISH) es++
            if (w in ENGLISH) en++
        }
        // Each Spanish character counts like half a function word, capped so a few
        // accented names in an English text don't flip it.
        val marks = sample.lowercase(Locale.ROOT).count { it in SPANISH_CHARS }
        es += minOf(marks * 0.5, maxOf(6.0, en))
        if (es + en < 4) return null
        return when {
            es > en * 1.3 -> "es"
            en > es * 1.3 -> "en"
            else -> null
        }
    }

    private val SENTENCE_END = Regex("(?<=[.!?…»”\"])\\s+")

    /**
     * Splits paragraphs into speakable chunks of at most [maxChars] (TTS engines cap
     * the input length). Paragraphs stay whole when they fit; longer ones are split at
     * sentence ends, and as a last resort at spaces.
     */
    fun chunks(paragraphs: List<String>, maxChars: Int = 3_000): List<String> {
        val out = mutableListOf<String>()
        for (p in paragraphs) {
            if (p.length <= maxChars) {
                out += p
                continue
            }
            val current = StringBuilder()
            for (sentence in p.split(SENTENCE_END).flatMap { hardSplit(it, maxChars) }) {
                if (current.isNotEmpty() && current.length + 1 + sentence.length > maxChars) {
                    out += current.toString()
                    current.clear()
                }
                if (current.isNotEmpty()) current.append(' ')
                current.append(sentence)
            }
            if (current.isNotEmpty()) out += current.toString()
        }
        return out
    }

    /**
     * Where to continue reading [text] after an interruption (speed change, pause) at
     * character [offset]: the start of the sentence being read, so the listener hears
     * it whole; but if that is more than [maxBack] characters back (a very long
     * sentence), the start of the current word instead.
     */
    fun resumePoint(text: String, offset: Int, maxBack: Int = 150): Int {
        val o = offset.coerceIn(0, text.length)
        var sentenceStart = 0
        for (m in SENTENCE_END.findAll(text)) {
            val next = m.range.last + 1
            if (next <= o) sentenceStart = next else break
        }
        if (o - sentenceStart <= maxBack) return sentenceStart
        var word = o
        while (word > 0 && !text[word - 1].isWhitespace()) word--
        return word
    }

    private fun hardSplit(s: String, max: Int): List<String> {
        if (s.length <= max) return listOf(s)
        val parts = mutableListOf<String>()
        var rest = s
        while (rest.length > max) {
            val cut = rest.lastIndexOf(' ', max).takeIf { it > 0 } ?: max
            parts += rest.substring(0, cut).trim()
            rest = rest.substring(cut).trim()
        }
        if (rest.isNotEmpty()) parts += rest
        return parts
    }
}
