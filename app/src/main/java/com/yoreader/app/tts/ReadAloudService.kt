package com.yoreader.app.tts

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.yoreader.app.MainActivity
import com.yoreader.app.R
import com.yoreader.app.YoReaderApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Keeps read-aloud going with the screen off or the app in the background.
 *
 * A foreground service (type mediaPlayback) stops Android from freezing or closing the
 * app mid-article. It shows a notification with the article and Pause/Resume and Stop,
 * linked to a media session, so Android shows it as a media card in the notification
 * panel and on the lock screen. It also pauses for calls and other audio (resuming
 * after a short interruption) and when headphones are unplugged.
 *
 * Headset/earbud buttons don't reach the session yet: Android sends them to the app it
 * sees playing audio, and the sound comes from the speech engine's own process. That
 * changes once the app plays the synthesized audio itself.
 *
 * Started by [start] when reading begins; stops itself when reading ends.
 */
class ReadAloudService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val speaker by lazy { (application as YoReaderApp).speaker }
    private lateinit var session: MediaSessionCompat
    private lateinit var audioManager: AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var hasFocus = false
    /** Paused by a short interruption (e.g. a notification sound or a call), not the user. */
    private var pausedForInterruption = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var noisyRegistered = false
    private var inForeground = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        createChannel()
        session = MediaSessionCompat(this, "yoreader").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = userResume()
                override fun onPause() = userPause()
                override fun onStop() = speaker.stop()
                override fun onFastForward() = speaker.faster()
                override fun onRewind() = speaker.slower()
            })
            setSessionActivity(openAppIntent())
            isActive = true
        }
        scope.launch {
            speaker.state.combine(speaker.rate) { s, r -> s to r }.collect { (s, rate) -> render(s, rate) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must go to the foreground right away, whatever the action.
        render(speaker.state.value, speaker.rate.value)
        when (intent?.action) {
            ACTION_PAUSE -> userPause()
            ACTION_RESUME -> userResume()
            ACTION_STOP -> speaker.stop()
        }
        return START_NOT_STICKY
    }

    private fun userPause() {
        pausedForInterruption = false
        speaker.pause()
    }

    private fun userResume() {
        pausedForInterruption = false
        speaker.resume()
    }

    private fun render(s: ArticleSpeaker.State, rate: Float) {
        if (!s.isActive) {
            // Android requires startForeground() after startForegroundService(), even if
            // reading already ended in the meantime.
            if (!inForeground) goForeground(placeholder())
            finish()
            return
        }
        val speaking = s.status != ArticleSpeaker.Status.PAUSED
        if (speaking) {
            requestFocus()
            acquireWakeLock()
            registerNoisy()
        } else {
            releaseWakeLock()
        }

        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, s.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, getString(R.string.app_name))
                .build()
        )
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_FAST_FORWARD or PlaybackStateCompat.ACTION_REWIND
                )
                .setState(
                    if (speaking) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                    rate,
                )
                .build()
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_read_aloud)
            .setContentTitle(s.title)
            .setContentText(
                (if (speaking) "Reading" else "Paused") +
                    " · part ${s.position + 1} of ${s.total} · ${SpeechRate.label(rate)}"
            )
            .setContentIntent(openAppIntent())
            .setDeleteIntent(serviceIntent(ACTION_STOP))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(speaking)
            .addAction(
                if (speaking) {
                    NotificationCompat.Action(R.drawable.ic_pause, "Pause", serviceIntent(ACTION_PAUSE))
                } else {
                    NotificationCompat.Action(R.drawable.ic_play, "Resume", serviceIntent(ACTION_RESUME))
                }
            )
            .addAction(NotificationCompat.Action(R.drawable.ic_stop, "Stop", serviceIntent(ACTION_STOP)))
            .setStyle(
                MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1)
            )
            .build()

        if (!inForeground) {
            goForeground(notification)
        } else {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun goForeground(notification: android.app.Notification) {
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            },
        )
        inForeground = true
    }

    private fun placeholder() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_read_aloud)
        .setContentTitle("Read aloud")
        .setSilent(true)
        .build()

    /** Reading ended (finished, stopped or failed): release everything and go away. */
    private fun finish() {
        releaseWakeLock()
        abandonFocus()
        unregisterNoisy()
        if (inForeground) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            inForeground = false
        }
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        releaseWakeLock()
        abandonFocus()
        unregisterNoisy()
        session.isActive = false
        session.release()
        super.onDestroy()
    }

    /** Swiping the app away from recent apps ends reading too. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        speaker.stop()
        super.onTaskRemoved(rootIntent)
    }

    // --- Audio focus: other apps and calls ---

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Another app started playing: stop competing, the user resumes by hand.
                hasFocus = false
                pausedForInterruption = false
                speaker.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // A call, a navigation prompt, a notification sound: pause, then continue.
                // (Ducking speech would make it hard to follow, so pause for those too.)
                if (speaker.state.value.status == ArticleSpeaker.Status.SPEAKING) {
                    pausedForInterruption = true
                    speaker.pause()
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasFocus = true
                if (pausedForInterruption) {
                    pausedForInterruption = false
                    speaker.resume()
                }
            }
        }
    }

    private fun requestFocus() {
        if (hasFocus) return
        val request = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener(focusListener)
            .build()
            .also { focusRequest = it }
        hasFocus = audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        hasFocus = false
        pausedForInterruption = false
    }

    // --- Headphones unplugged / Bluetooth disconnected ---

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) userPause()
        }
    }

    private fun registerNoisy() {
        if (noisyRegistered) return
        ContextCompat.registerReceiver(
            this, noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        noisyRegistered = true
    }

    private fun unregisterNoisy() {
        if (!noisyRegistered) return
        unregisterReceiver(noisyReceiver)
        noisyRegistered = false
    }

    // --- Keep the CPU awake while speaking (the screen may be off) ---

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val lock = wakeLock ?: (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "yoreader:read-aloud")
            .apply { setReferenceCounted(false) }
            .also { wakeLock = it }
        lock.acquire(MAX_WAKE_MS)
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
    }

    // --- Notification plumbing ---

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Read aloud", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Controls for the article being read aloud"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun serviceIntent(action: String): PendingIntent = PendingIntent.getService(
        this, action.hashCode(),
        Intent(this, ReadAloudService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        private const val CHANNEL_ID = "read_aloud"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_PAUSE = "com.yoreader.app.tts.PAUSE"
        private const val ACTION_RESUME = "com.yoreader.app.tts.RESUME"
        private const val ACTION_STOP = "com.yoreader.app.tts.STOP"
        /** Safety net: a very long article at slow speed still fits in this. */
        private const val MAX_WAKE_MS = 3 * 60 * 60 * 1000L

        /** Starts the service; call when reading starts (the app is in the foreground then). */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ReadAloudService::class.java))
        }
    }
}
