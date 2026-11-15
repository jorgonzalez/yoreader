package com.yoreader.app.tts

import java.util.Locale

/** Read-aloud speeds the user can step through; 1 is the engine's normal speed. */
object SpeechRate {

    /** Finer steps around normal speed, bigger ones further out. */
    val STEPS = listOf(0.5f, 0.75f, 0.9f, 1f, 1.1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)

    const val NORMAL = 1f

    private const val EPSILON = 0.001f

    fun faster(rate: Float): Float = STEPS.firstOrNull { it > rate + EPSILON } ?: STEPS.last()

    fun slower(rate: Float): Float = STEPS.lastOrNull { it < rate - EPSILON } ?: STEPS.first()

    fun isFastest(rate: Float) = rate >= STEPS.last() - EPSILON

    fun isSlowest(rate: Float) = rate <= STEPS.first() + EPSILON

    /** "1×", "1.25×", "0.9×". */
    fun label(rate: Float): String =
        String.format(Locale.ROOT, "%.2f", rate).trimEnd('0').trimEnd('.') + "×"
}
