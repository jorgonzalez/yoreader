package com.yoreader.app.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechRateTest {

    @Test
    fun stepsUpAndDown() {
        assertEquals(1.1f, SpeechRate.faster(1f))
        assertEquals(0.9f, SpeechRate.slower(1f))
        assertEquals(1.25f, SpeechRate.faster(1.1f))
    }

    @Test
    fun staysWithinLimits() {
        assertEquals(3f, SpeechRate.faster(3f))
        assertEquals(0.5f, SpeechRate.slower(0.5f))
        assertTrue(SpeechRate.isFastest(3f))
        assertTrue(SpeechRate.isSlowest(0.5f))
    }

    @Test
    fun offStepValuesSnapToNeighbours() {
        // e.g. a value saved by an older version
        assertEquals(1.25f, SpeechRate.faster(1.2f))
        assertEquals(1.1f, SpeechRate.slower(1.2f))
    }

    @Test
    fun labels() {
        assertEquals("1×", SpeechRate.label(1f))
        assertEquals("1.25×", SpeechRate.label(1.25f))
        assertEquals("0.9×", SpeechRate.label(0.9f))
        assertEquals("2×", SpeechRate.label(2f))
    }
}
