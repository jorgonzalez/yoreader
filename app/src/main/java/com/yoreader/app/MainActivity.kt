package com.yoreader.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.yoreader.app.sync.SyncWorker
import com.yoreader.app.ui.YoReaderRoot
import com.yoreader.app.ui.theme.YoReaderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            YoReaderTheme {
                YoReaderRoot()
            }
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
