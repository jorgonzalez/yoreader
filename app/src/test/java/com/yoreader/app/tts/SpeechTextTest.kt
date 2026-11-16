package com.yoreader.app.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechTextTest {

    @Test
    fun guessesSpanishAndEnglish() {
        assertEquals(
            "es",
            SpeechText.guessLanguage(
                "El Gobierno estonio anunció este domingo que ha decidido cerrar la frontera del " +
                    "país báltico con Rusia el 1 de diciembre, después de que se produjera un intento de sabotaje."
            ),
        )
        assertEquals(
            "en",
            SpeechText.guessLanguage(
                "Huawei believes its new battery is more than just a home backup, and the company says " +
                    "that it will be available in the first half of the year for owners of solar panels."
            ),
        )
    }

    @Test
    fun tooLittleTextIsUnknown() {
        assertNull(SpeechText.guessLanguage("Hola mundo"))
        assertNull(SpeechText.guessLanguage(""))
    }

    @Test
    fun shortParagraphsStayWhole() {
        assertEquals(listOf("Uno.", "Dos."), SpeechText.chunks(listOf("Uno.", "Dos.")))
    }

    @Test
    fun longParagraphsSplitAtSentences() {
        val sentence = "Esta es una frase de prueba bastante larga para el lector. "
        val paragraph = sentence.repeat(20).trim()
        val chunks = SpeechText.chunks(listOf(paragraph), maxChars = 200)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 200 })
        assertTrue(chunks.all { it.endsWith(".") }) // cut at sentence ends
        assertEquals(paragraph, chunks.joinToString(" "))
    }

    @Test
    fun hugeSentenceIsSplitAtSpaces() {
        val words = "palabra ".repeat(100).trim()
        val chunks = SpeechText.chunks(listOf(words), maxChars = 100)
        assertTrue(chunks.all { it.length <= 100 })
        assertEquals(words, chunks.joinToString(" "))
    }

    private val text = "Primera frase corta. Segunda frase, que estamos leyendo ahora mismo. Tercera."

    @Test
    fun resumesAtStartOfCurrentSentence() {
        val inSecond = text.indexOf("leyendo")
        assertEquals(text.indexOf("Segunda"), SpeechText.resumePoint(text, inSecond))
    }

    @Test
    fun resumesAtStartWhenInFirstSentence() {
        assertEquals(0, SpeechText.resumePoint(text, text.indexOf("corta")))
        assertEquals(0, SpeechText.resumePoint(text, 0))
    }

    @Test
    fun atSentenceStartStaysThere() {
        val third = text.indexOf("Tercera")
        assertEquals(third, SpeechText.resumePoint(text, third))
    }

    @Test
    fun veryLongSentenceResumesAtCurrentWord() {
        val long = "Inicio. " + "palabra ".repeat(60) + "objetivo y más palabras después"
        val target = long.indexOf("objetivo")
        // Pointing into the middle of the word still goes back to its start.
        assertEquals(target, SpeechText.resumePoint(long, target + 3))
    }

    @Test
    fun outOfRangeOffsetsAreClamped() {
        assertEquals(0, SpeechText.resumePoint(text, -5))
        assertEquals(text.indexOf("Tercera"), SpeechText.resumePoint(text, 10_000))
    }
}
