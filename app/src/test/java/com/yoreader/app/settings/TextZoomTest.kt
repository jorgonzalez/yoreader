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
        assertEquals(105, TextZoom.snap(107))
        assertEquals(110, TextZoom.snap(108))
        assertEquals(100, TextZoom.snap(102))
    }

    @Test
    fun clampsToRange() {
        assertEquals(TextZoom.MIN, TextZoom.snap(10))
        assertEquals(TextZoom.MAX, TextZoom.snap(400))
    }

    @Test
    fun sliderStepsMatchRange() {
        // 80, 85, ... 150: 15 sizes, 13 stops between the two ends.
        assertEquals(13, TextZoom.STEPS_BETWEEN)
    }
}
