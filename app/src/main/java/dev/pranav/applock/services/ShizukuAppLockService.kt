package dev.pranav.applock.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import dev.pranav.applock.R
import dev.pranav.applock.data.repository.AppLockRepository
import dev.pranav.applock.features.lockscreen.ui.PasswordOverlayActivity
import dev.pranav.applock.shizuku.ShizukuActivityManager

class ShizukuAppLockService : Service() {
    private lateinit var appLockRepository: AppLockRepository
    private var shizukuActivityManager: ShizukuActivityManager? = null

    companion object {
        private const val TAG = "ShizukuAppLockService"
        private const val NOTIFICATION_ID = 112
        private const val CHANNEL_ID = "ShizukuAppLockServiceChannel"
    }

    override fun onCreate() {
        super.onCreate()
        appLockRepository = AppLockRepository(applicationContext)
        Log.d(TAG, "ShizukuAppLockService created")

        shizukuActivityManager = ShizukuActivityManager(this) { packageName, timeMillis ->
            Log.d(TAG, "Foreground app changed to: $packageName")
            if (packageName != AppLockManager.temporarilyUnlockedApp) {
                AppLockManager.temporarilyUnlockedApp = ""
            }
            checkAndLockApp(packageName, timeMillis)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "ShizukuAppLockService started")
        AppLockManager.resetRestartAttempts("ShizukuAppLockService")
        createNotificationChannel()
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        val shizukuStarted = shizukuActivityManager?.start()
        if (shizukuStarted == false) {
            Log.e(TAG, "Shizuku failed to start, triggering fallback")
            AppLockManager.startFallbackServices(this, ShizukuAppLockService::class.java)
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    override fun onDestroy() {
        shizukuActivityManager?.stop()
        Log.d(TAG, "ShizukuAppLockService destroyed")
        AppLockManager.startFallbackServices(this, ShizukuAppLockService::class.java)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "ShizukuAppLockService unbound")
        AppLockManager.startFallbackServices(this, ShizukuAppLockService::class.java)
        return super.onUnbind(intent)
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "AppLock Shizuku Service",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AppLock")
            .setContentText("Protecting your apps with Shizuku")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
    }

    private fun checkAndLockApp(packageName: String, currentTime: Long) {
        if (AppLockManager.isAppTemporarilyUnlocked(packageName)) {
            Log.d(TAG, "App $packageName is temporarily unlocked, skipping app lock")
            return
        } else {
            if (AppLockManager.appUnlockTimes.isEmpty()) {
                AppLockManager.clearTemporarilyUnlockedApp()
            }
        }
        val lockedApps = appLockRepository.getLockedApps()
        if (!lockedApps.contains(packageName)) {
            return
        }

        val unlockDuration = appLockRepository.getUnlockTimeDuration()
        val unlockTimestamp = AppLockManager.appUnlockTimes[packageName] ?: 0

        Log.d(
            TAG,
            "Checking app lock for $packageName at $currentTime, last unlock timestamp: $unlockTimestamp, unlock duration: $unlockDuration"
        )
        if (unlockDuration > 0 && unlockTimestamp > 0) {
            val elapsedMinutes = (currentTime - unlockTimestamp) / (60 * 1000)
            if (elapsedMinutes < unlockDuration) {
                return
            } else {
                AppLockManager.appUnlockTimes.remove(packageName)
                if (AppLockManager.isAppTemporarilyUnlocked(packageName)) {
                    AppLockManager.clearTemporarilyUnlockedApp()
                }
            }
        }

        Log.d(TAG, "Locked app detected: $packageName")

        val intent = Intent(this, PasswordOverlayActivity::class.java).apply {
            flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_FROM_BACKGROUND or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra("locked_package", packageName)
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start password overlay: ${e.message}", e)
        }
    }
}
