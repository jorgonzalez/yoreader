package com.yoreader.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.yoreader.app.sync.SyncWorker
import com.yoreader.app.ui.YoReaderRoot
import com.yoreader.app.ui.reader.SharedLink
import com.yoreader.app.ui.theme.YoReaderTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    /** A link shared from another app, waiting to be opened. */
    private val sharedLink = MutableStateFlow<SharedLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // Only a new start: after a rotation the shared page is already open.
        if (savedInstanceState == null) takeShare(intent)
        setContent {
            YoReaderTheme {
                YoReaderRoot(sharedLink = sharedLink)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        takeShare(intent)
    }

    private fun takeShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val link = SharedLink.fromIntent(intent)
        if (link == null) {
            Toast.makeText(this, "No web address in what was shared", Toast.LENGTH_LONG).show()
        } else {
            sharedLink.value = link
        }
    }

    // Laptop sync: take what was read there when coming back, and hand over what was
    // read here when leaving.
    override fun onStart() {
        super.onStart()
        (application as YoReaderApp).syncLaptopSoon()
    }

    override fun onStop() {
        super.onStop()
        if ((application as YoReaderApp).laptopSync.state.value.fileUri != null) SyncWorker.enqueue(this)
    }
}
