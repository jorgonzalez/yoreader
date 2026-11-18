package com.yoreader.app.tts

import java.util.Locale

/** A read-aloud language: [id] is stored in settings ("es", "pt-BR"), [label] shown on the button. */
data class TtsLanguage(
    val id: String,
    val name: String,
    val label: String,
    val locale: Locale,
    /** Must the phone have a voice for this exact country (Portugal vs Brazil)? */
    val needsCountry: Boolean = false,
) {
    /** Language without the country: what [LanguageGuess] detects ("pt" for both Portuguese). */
    val base: String get() = id.substringBefore('-')
}

/** The languages yoreader can read aloud, if the phone has a voice for them. */
object TtsLanguages {

    /** Sorted by name, as listed in Options. */
    val ALL: List<TtsLanguage> = listOf(
        TtsLanguage("cs", "Czech", "CS", Locale("cs", "CZ")),
        TtsLanguage("nl", "Dutch", "NL", Locale("nl", "NL")),
        TtsLanguage("en", "English", "EN", Locale.US),
        TtsLanguage("fi", "Finnish", "FI", Locale("fi", "FI")),
        TtsLanguage("fr", "French", "FR", Locale.FRANCE),
        TtsLanguage("de", "German", "DE", Locale.GERMANY),
        TtsLanguage("it", "Italian", "IT", Locale.ITALY),
        TtsLanguage("pl", "Polish", "PL", Locale("pl", "PL")),
        TtsLanguage("pt-BR", "Portuguese (Brazil)", "PT-BR", Locale("pt", "BR"), needsCountry = true),
        TtsLanguage("pt-PT", "Portuguese (Portugal)", "PT", Locale("pt", "PT"), needsCountry = true),
        TtsLanguage("ro", "Romanian", "RO", Locale("ro", "RO")),
        TtsLanguage("sk", "Slovak", "SK", Locale("sk", "SK")),
        TtsLanguage("es", "Spanish", "ES", Locale("es", "ES")),
        TtsLanguage("sv", "Swedish", "SV", Locale("sv", "SE")),
        TtsLanguage("tr", "Turkish", "TR", Locale("tr", "TR")),
    )

    /** On until the user changes the list: what yoreader read before languages were configurable. */
    val DEFAULT_ENABLED = setOf("es", "en")

    private val byId = ALL.associateBy { it.id }

    fun byId(id: String?): TtsLanguage? = id?.let { byId[it] }

    /**
     * The voice to read with, among [enabled] ids, for a [language] that is either a
     * voice id ("pt-BR") or a detected/declared base language ("pt", "es"). Portuguese
     * picks Portugal or Brazil from [text]; null when no enabled voice fits.
     */
    fun voiceFor(language: String?, enabled: Collection<String>, text: String = ""): String? {
        val lang = language?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        enabled.firstOrNull { it.equals(lang, ignoreCase = true) }?.let { return it }
        val base = lang.substringBefore('-').substringBefore('_').lowercase(Locale.ROOT)
        val matches = enabled.filter { it.substringBefore('-') == base }
        return when {
            matches.size <= 1 -> matches.firstOrNull()
            base == "pt" -> if (LanguageGuess.isBrazilian(text)) "pt-BR" else "pt-PT"
            else -> matches.first()
        }
    }
}
