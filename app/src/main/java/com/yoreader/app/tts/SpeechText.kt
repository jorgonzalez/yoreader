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

    /**
     * "es" or "en" from the most common function words in [text], or null when there
     * aren't enough of them to tell (short or other-language text).
     */
    fun guessLanguage(text: String): String? {
        var es = 0
        var en = 0
        for (match in WORD.findAll(text.take(5_000))) {
            val w = match.value.lowercase(Locale.ROOT)
            if (w in SPANISH) es++
            if (w in ENGLISH) en++
        }
        if (es + en < 8) return null
        return when {
            es > en * 1.5 -> "es"
            en > es * 1.5 -> "en"
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
