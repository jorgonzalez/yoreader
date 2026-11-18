package com.yoreader.app.settings

import android.content.Context
import com.yoreader.app.data.ArticleRetention
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The user's choices from the Options screen, kept on the phone (SharedPreferences)
 * and offered as flows so screens update as soon as something changes.
 */
class AppSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _textZoom = MutableStateFlow(TextZoom.snap(prefs.getInt(KEY_TEXT_ZOOM, TextZoom.DEFAULT)))

    /** Size of article text in the reader and in cleaned pages, in percent (100 = normal). */
    val textZoom: StateFlow<Int> = _textZoom.asStateFlow()

    fun setTextZoom(percent: Int) {
        val value = TextZoom.snap(percent)
        if (value == _textZoom.value) return
        _textZoom.value = value
        prefs.edit().putInt(KEY_TEXT_ZOOM, value).apply()
    }

    private val _keepReadDays = MutableStateFlow(
        prefs.getInt(KEY_KEEP_READ_DAYS, ArticleRetention.DEFAULT_DAYS)
            .takeIf { it in ArticleRetention.CHOICES } ?: ArticleRetention.DEFAULT_DAYS
    )

    /** Read articles older than this many days are removed (saved ones are kept); 0 = never. */
    val keepReadDays: StateFlow<Int> = _keepReadDays.asStateFlow()

    fun setKeepReadDays(days: Int) {
        if (days !in ArticleRetention.CHOICES || days == _keepReadDays.value) return
        _keepReadDays.value = days
        prefs.edit().putInt(KEY_KEEP_READ_DAYS, days).apply()
    }

    private companion object {
        const val PREFS = "settings"
        const val KEY_TEXT_ZOOM = "text_zoom"
        const val KEY_KEEP_READ_DAYS = "keep_read_days"
    }
}

/** Article text sizes offered: 80% to 150% in steps of 10. */
object TextZoom {
    const val MIN = 80
    const val MAX = 150
    const val STEP = 10
    const val DEFAULT = 100

    /** Number of stops between [MIN] and [MAX] (for a stepped slider). */
    const val STEPS_BETWEEN = (MAX - MIN) / STEP - 1

    /** The nearest offered size to [percent]. */
    fun snap(percent: Int): Int {
        val clamped = percent.coerceIn(MIN, MAX)
        return MIN + Math.round((clamped - MIN).toFloat() / STEP) * STEP
    }
}
