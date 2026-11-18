package com.yoreader.app.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class TextZoomTest {

    @Test
    fun keepsOfferedSizes() {
        for (p in TextZoom.MIN..TextZoom.MAX step TextZoom.STEP) assertEquals(p, TextZoom.snap(p))
    }

    @Test
    fun roundsToNearestStep() {
        assertEquals(110, TextZoom.snap(107))
        assertEquals(100, TextZoom.snap(104))
    }

    @Test
    fun clampsToRange() {
        assertEquals(TextZoom.MIN, TextZoom.snap(10))
        assertEquals(TextZoom.MAX, TextZoom.snap(400))
    }

    @Test
    fun sliderStepsMatchRange() {
        // 80, 90, ... 150: 8 sizes, 6 stops between the two ends.
        assertEquals(6, TextZoom.STEPS_BETWEEN)
    }
}
