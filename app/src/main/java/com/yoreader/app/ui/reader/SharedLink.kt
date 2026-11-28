package com.yoreader.app.ui.reader

import android.content.Intent

/** What to do with a page shared to the app from another app (e.g. a browser). */
enum class StartAction { CLEAN, READ }

/** A link shared to the app, and what the chosen share entry asked for. */
data class SharedLink(val url: String, val action: StartAction) {
    companion object {
        /** Share entries in the manifest: aliases of MainActivity, one per action. */
        private const val READ_ALOUD_ALIAS = ".ShareReadAloud"

        private val WEB_ADDRESS = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)

        /** The link shared in [intent], or null if it isn't a share or has no web address. */
        fun fromIntent(intent: Intent?): SharedLink? {
            if (intent?.action != Intent.ACTION_SEND) return null
            val url = findUrl(intent.getStringExtra(Intent.EXTRA_TEXT)) ?: return null
            val read = intent.component?.className?.endsWith(READ_ALOUD_ALIAS) == true
            return SharedLink(url, if (read) StartAction.READ else StartAction.CLEAN)
        }

        /**
         * The first web address in shared [text]. Apps send just the link, or a title and
         * the link ("Some headline https://…"), sometimes with punctuation stuck to it.
         */
        fun findUrl(text: String?): String? {
            val match = text?.let { WEB_ADDRESS.find(it) }?.value ?: return null
            return match.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}', '\'', '»', '”')
        }
    }
}
