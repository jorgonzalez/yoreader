package com.yoreader.app.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageGuessTest {

    private val samples = mapOf(
        "en" to "The government said on Sunday that it would close the border with the neighbouring " +
            "country next month, after an attempted act of sabotage that was reported by the police.",
        "es" to "El Gobierno anunció este domingo que ha decidido cerrar la frontera del país con Rusia " +
            "el próximo mes, después de que se produjera un intento de sabotaje en la zona.",
        "pt" to "O governo anunciou no domingo que vai fechar a fronteira com o país vizinho no próximo " +
            "mês, depois de uma tentativa de sabotagem que foi denunciada pela polícia e também pelos militares.",
        "fr" to "Le gouvernement a annoncé dimanche qu'il allait fermer la frontière avec le pays voisin " +
            "le mois prochain, après une tentative de sabotage qui a été signalée par la police dans la région.",
        "it" to "Il governo ha annunciato domenica che chiuderà il confine con il paese vicino il mese " +
            "prossimo, dopo un tentativo di sabotaggio che è stato segnalato dalla polizia anche nella regione.",
        "de" to "Die Regierung hat am Sonntag angekündigt, dass sie die Grenze zum Nachbarland im " +
            "nächsten Monat schließen wird, nachdem die Polizei einen Sabotageversuch gemeldet hat.",
        "nl" to "De regering heeft zondag aangekondigd dat zij de grens met het buurland volgende maand " +
            "zal sluiten, nadat de politie een poging tot sabotage had gemeld bij het ministerie.",
        "sv" to "Regeringen meddelade på söndagen att den kommer att stänga gränsen mot grannlandet " +
            "nästa månad, efter att polisen har rapporterat ett försök till sabotage vid gränsen.",
        "fi" to "Hallitus ilmoitti sunnuntaina, että se sulkee rajan naapurimaahan ensi kuussa, koska " +
            "poliisi on raportoinut sabotaasiyrityksestä, mutta tarkempia tietoja ei ole vielä kerrottu.",
        "pl" to "Rząd ogłosił w niedzielę, że w przyszłym miesiącu zamknie granicę z sąsiednim krajem, " +
            "ponieważ policja zgłosiła próbę sabotażu, ale szczegóły nie są jeszcze znane.",
        "cs" to "Vláda v neděli oznámila, že příští měsíc uzavře hranici se sousední zemí, protože " +
            "policie ohlásila pokus o sabotáž, který podle ní byl také namířen proti železnici.",
        "sk" to "Vláda v nedeľu oznámila, že budúci mesiac uzavrie hranicu so susednou krajinou, pretože " +
            "polícia ohlásila pokus o sabotáž, ktorý sa podľa nej týkal aj železnice.",
        "tr" to "Hükümet pazar günü yaptığı açıklamada, polisin bir sabotaj girişimini bildirmesinin " +
            "ardından komşu ülkeyle olan sınırı gelecek ay kapatacağını ve bu kararın çok önemli olduğunu söyledi.",
        "ro" to "Guvernul a anunțat duminică că va închide granița cu țara vecină luna viitoare, după ce " +
            "poliția a semnalat o tentativă de sabotaj care a fost descoperită în această regiune.",
    )

    @Test
    fun guessesEachLanguageAmongAll() {
        for ((lang, text) in samples) {
            assertEquals("sample $lang", lang, LanguageGuess.guess(text, LanguageGuess.KNOWN))
        }
    }

    @Test
    fun guessesAmongJustSpanishAndEnglish() {
        assertEquals("es", LanguageGuess.guess(samples.getValue("es"), setOf("es", "en")))
        assertEquals("en", LanguageGuess.guess(samples.getValue("en"), setOf("es", "en")))
    }

    @Test
    fun onlyConsidersCandidates() {
        // Czech text with only Slovak and English on: closest is Slovak, never Czech.
        val guess = LanguageGuess.guess(samples.getValue("cs"), setOf("sk", "en"))
        assertTrue(guess == null || guess == "sk")
        assertNull(LanguageGuess.guess(samples.getValue("de"), emptySet()))
    }

    @Test
    fun romanianWithCedillasStillRomanian() {
        val cedilla = samples.getValue("ro").replace('ș', 'ş').replace('ț', 'ţ')
        assertEquals("ro", LanguageGuess.guess(cedilla, LanguageGuess.KNOWN))
    }

    @Test
    fun tooLittleTextIsUnknown() {
        assertNull(LanguageGuess.guess("Hola", LanguageGuess.KNOWN))
    }

    @Test
    fun brazilianOrEuropeanPortuguese() {
        assertTrue(LanguageGuess.isBrazilian("Você viu o fato? A equipe entrou em contato pelo celular."))
        assertFalse(LanguageGuess.isBrazilian("Viu o facto? A equipa entrou em contacto pelo telemóvel."))
        assertFalse(LanguageGuess.isBrazilian(samples.getValue("pt"))) // no markers: Portugal
    }

    @Test
    fun voiceForPicksAnEnabledVoice() {
        val enabled = listOf("es", "en", "pt-PT", "pt-BR")
        assertEquals("es", TtsLanguages.voiceFor("es", enabled))
        assertEquals("pt-BR", TtsLanguages.voiceFor("pt-BR", enabled))
        assertEquals("pt-BR", TtsLanguages.voiceFor("pt", enabled, "Você entrou em contato?"))
        assertEquals("pt-PT", TtsLanguages.voiceFor("pt", enabled, "Entrou em contacto?"))
        assertEquals("pt-BR", TtsLanguages.voiceFor("pt", listOf("pt-BR"), "Entrou em contacto?"))
        assertEquals("en", TtsLanguages.voiceFor("en-US", enabled))
        assertNull(TtsLanguages.voiceFor("de", enabled))
        assertNull(TtsLanguages.voiceFor(null, enabled))
    }

    @Test
    fun catalogIsSortedByNameAndCovered() {
        val names = TtsLanguages.ALL.map { it.name }
        assertEquals(names.sorted(), names)
        assertTrue(TtsLanguages.ALL.all { it.base in LanguageGuess.KNOWN })
        assertTrue(TtsLanguages.DEFAULT_ENABLED.all { TtsLanguages.byId(it) != null })
    }
}
