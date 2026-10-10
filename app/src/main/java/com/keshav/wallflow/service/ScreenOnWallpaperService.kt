package com.keshav.wallflow.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.keshav.wallflow.MainActivity
import com.keshav.wallflow.R
import com.keshav.wallflow.data.prefs.RotationPreferences
import com.keshav.wallflow.data.repository.WallpaperRepository
import com.keshav.wallflow.wallpaper.WallpaperTarget
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers

/**
 * SCREEN_ON mode. A foreground service that registers [ScreenOnReceiver] at runtime and, on each
 * screen wake, applies the next least-recently-used wallpaper to the lock screen. Selection + apply
 * is the same repository path used by INTERVAL mode.
 */
@AndroidEntryPoint
class ScreenOnWallpaperService : Service() {

    @Inject
    lateinit var repository: WallpaperRepository

    @Inject
    lateinit var preferences: RotationPreferences

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var receiver: ScreenOnReceiver? = null
    private val rotateMutex = Mutex()
    private var lastRotateElapsed = 0L

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        registerScreenReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Restart if the system kills us; the receiver must stay alive to catch screen wakes.
        return START_STICKY
    }

    private fun registerScreenReceiver() {
        if (receiver != null) return
        val newReceiver = ScreenOnReceiver(::onScreenOn)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(newReceiver, filter)
        receiver = newReceiver
    }

    private fun onScreenOn() {
        // Debounce: SCREEN_ON and USER_PRESENT can fire close together.
        val now = SystemClock.elapsedRealtime()
        if (now - lastRotateElapsed < DEBOUNCE_MS) return
        lastRotateElapsed = now

        scope.launch {
            rotateMutex.withLock {
                if (repository.count() == 0) return@withLock
                val filterTagId = preferences.config.first().filterTagId
                // Screen-ON mode always targets the lock screen.
                repository.rotateToNext(WallpaperTarget.LOCK, filterTagId)
            }
        }
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.rotation_notification_title))
            .setContentText(getString(R.string.rotation_notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        receiver?.let { runCatching { unregisterReceiver(it) } }
        receiver = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = null

    companion object {
        const val CHANNEL_ID = "wallpaper_rotation"
        private const val NOTIFICATION_ID = 1001
        private const val DEBOUNCE_MS = 2_000L
    }
}
