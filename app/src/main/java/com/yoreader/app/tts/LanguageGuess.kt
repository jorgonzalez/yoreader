package com.yoreader.app.tts

import java.util.Locale

/**
 * Guesses the language of an article among the read-aloud languages turned on, from
 * its most common short words and from letters that only one of those languages uses.
 */
object LanguageGuess {

    private val WORD = Regex("[\\p{L}']+")

    /**
     * Frequent words of each language, mostly leaving out ones that are as frequent in
     * another language here (e.g. "de", "en"), so most words point one way; a few
     * shared ones ("la") count for each language that uses them a lot.
     */
    private val WORDS: Map<String, Set<String>> = mapOf(
        "en" to words(
            "the of and to in is that for it with as on was are this be by not you at from " +
                "have has but they his her which were their will would an or its"
        ),
        "es" to words(
            "de la que el en y los las del se por con para una un es al lo como más pero sus " +
                "ya este esta entre cuando muy sin sobre también fue ha"
        ),
        "pt" to words(
            "o do da em um é com não uma os mais dos mas foi ao ele das tem à seu sua ou ser " +
                "quando muito há nos já está também isso pelo pela"
        ),
        "fr" to words(
            "le la les des est et une du dans pour pas qui sur au avec ce sont par plus ne elle mais " +
                "été cette nous leur aux"
        ),
        "it" to words(
            "il di che per non sono della è gli anche più ma come nel alla dei delle questo " +
                "questa ci essere stato"
        ),
        "de" to words(
            "der die und den von zu das mit sich auf für ist im dem nicht ein eine als auch " +
                "werden aus er hat dass sie nach wird bei noch wie"
        ),
        "nl" to words(
            "het een van dat op te zijn voor met niet aan om ook bij maar dan nog wordt door " +
                "naar zich deze heeft"
        ),
        "sv" to words(
            "och att det som på är av för med till har inte ett jag sig från också eller kan " +
                "efter vid när"
        ),
        "fi" to words(
            "ja ei että hän oli ovat kuin mutta myös tai joka sen ole mukaan vain jo nyt kun " +
                "hänen tämä niin siitä olla ollut"
        ),
        "pl" to words(
            "w nie się z że jest jak co ale po od za przez dla tak już jego jej są był była " +
                "może tylko"
        ),
        "cs" to words("že ve jsou jako pro které který jen už bylo také být není jejich"),
        "sk" to words("sa ako pre ktoré ktorý sú bolo aj len že tiež byť ešte"),
        "tr" to words(
            "ve bir bu ile için çok olarak daha gibi ancak kadar sonra olan her ama değil ise " +
                "şey ki"
        ),
        "ro" to words(
            "și în cu care pe din că nu este mai pentru fost sau lui fi sunt această acest"
        ),
    )

    /**
     * Letters each language uses beyond a-z. A letter counts as evidence only when no
     * other language being considered uses it (é proves nothing between French and
     * Spanish, but a lot between Spanish and English).
     */
    private val LETTERS: Map<String, String> = mapOf<String, String>(
        "en" to "",
        "es" to "ñáéíóúü¿¡",
        "pt" to "ãõáéíóúâêôàç",
        "fr" to "éèêàâîïôûùçëœ",
        "it" to "àèéìòù",
        "de" to "äöüß",
        "nl" to "ëï",
        "sv" to "åäö",
        "fi" to "äö",
        "pl" to "ąćęłńóśźż",
        "cs" to "áčďéěíňóřšťúůýž",
        "sk" to "áäčďéíĺľňóôŕšťúýž",
        "tr" to "çğıöşü",
        "ro" to "ăâîșț",
    ).mapValues { normalize(it.value) }

    /** Base languages ("pt", not "pt-BR") that [guess] can tell apart. */
    val KNOWN: Set<String> = WORDS.keys

    /**
     * The language of [text] among [candidates] (base codes like "es", "pt"), or null
     * when it can't tell: too little text, or no clear winner.
     */
    fun guess(text: String, candidates: Set<String>): String? {
        val langs = candidates.filter { it in KNOWN }
        if (langs.isEmpty()) return null
        val sample = normalize(text.take(5_000))
        val words = WORD.findAll(sample).map { it.value }.toList()

        val wordScore = langs.associateWith { lang -> words.count { it in WORDS.getValue(lang) }.toDouble() }
        val score = langs.associateWith { lang ->
            val own = LETTERS.getValue(lang).filter { c -> langs.none { it != lang && c in LETTERS.getValue(it) } }
            val marks = if (own.isEmpty()) 0 else sample.count { it in own }
            // A letter counts like half a word, capped so a few foreign names in a text
            // (e.g. "Peña" in English news) don't flip it.
            val bestOther = langs.filter { it != lang }.maxOfOrNull { wordScore.getValue(it) } ?: 0.0
            wordScore.getValue(lang) + minOf(marks * 0.5, maxOf(6.0, bestOther))
        }
        val ranked = score.entries.sortedByDescending { it.value }
        val best = ranked.first()
        val second = ranked.getOrNull(1)?.value ?: 0.0
        if (best.value + second < 4) return null
        return if (best.value > second * 1.3) best.key else null
    }

    private val BRAZIL = words(
        "você vocês ônibus fato fatos contato contatos equipe registro gênero econômico " +
            "econômica acadêmico time celular tela trem"
    )
    private val PORTUGAL = words(
        "facto factos contacto contactos equipa registo género económico económica académico " +
            "telemóvel autocarro ecrã comboio"
    )

    /** Whether Portuguese [text] reads as Brazilian rather than European (ties: European). */
    fun isBrazilian(text: String): Boolean {
        val words = WORD.findAll(normalize(text.take(20_000))).map { it.value }
        var br = 0
        var pt = 0
        for (w in words) {
            if (w in BRAZIL) br++
            if (w in PORTUGAL) pt++
        }
        return br > pt
    }

    /**
     * Lower case, and ş/ţ (cedilla) as ș/ț (comma): Romanian is often typed with the
     * cedilla forms. Turkish ş becomes ș too, the same way in texts and in our lists.
     */
    private fun normalize(text: String): String =
        text.lowercase(Locale.ROOT).replace('ş', 'ș').replace('ţ', 'ț')

    private fun words(list: String): Set<String> =
        normalize(list).split(' ').filter { it.isNotEmpty() }.toSet()
}
