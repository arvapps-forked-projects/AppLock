package dev.pranav.applock.services

import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.content.getSystemService
import dev.pranav.applock.data.repository.AppLockRepository
import dev.pranav.applock.features.lockscreen.ui.PasswordOverlayActivity
import java.util.Timer
import java.util.TimerTask

class ExperimentalAppLockService : Service() {
    private lateinit var appLockRepository: AppLockRepository
    private var timer: Timer? = null
    private lateinit var usageStatsManager: UsageStatsManager
    private val TAG = "ExperimentalAppLockService"

    override fun onCreate() {
        super.onCreate()
        appLockRepository = AppLockRepository(applicationContext)
        if (appLockRepository.isExperimentalImplEnabled()) {
            usageStatsManager = getSystemService()!!
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (appLockRepository.isExperimentalImplEnabled()) {
            timer = Timer()
            timer?.schedule(object : TimerTask() {
                override fun run() {
                    val foregroundApp = getCurrentForegroundAppInfo()
                    if (foregroundApp == null) {
                        AppLockManager.clearTemporarilyUnlockedApp()
                        return
                    }
                    checkAndLockApp(foregroundApp, System.currentTimeMillis())
                }
            }, 0, 250)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun checkAndLockApp(packageName: String, currentTime: Long) {
        if (AppLockManager.currentBiometricState == AppLockAccessibilityService.BiometricState.AUTH_STARTED) {
            return
        }
        if (AppLockManager.isAppTemporarilyUnlocked(packageName)) {
            return
        } else {
            AppLockManager.clearTemporarilyUnlockedApp()
        }

        if (PasswordOverlayActivity.isActive()) {
            return
        }

        val lockedApps = appLockRepository.getLockedApps()
        if (!lockedApps.contains(packageName)) {
            return
        }

        val unlockDuration = appLockRepository.getUnlockTimeDuration()
        val unlockTimestamp = AppLockManager.appUnlockTimes[packageName] ?: 0

        if (unlockDuration > 0 && unlockTimestamp > 0) {
            val elapsedMinutes = (currentTime - unlockTimestamp) / (60 * 1000)
            if (elapsedMinutes < unlockDuration) {
                if (!AppLockManager.isAppTemporarilyUnlocked(packageName)) {
                    AppLockManager.unlockApp(packageName)
                }
                return
            } else {
                AppLockManager.appUnlockTimes.remove(packageName)
                if (AppLockManager.isAppTemporarilyUnlocked(packageName)) {
                    AppLockManager.clearTemporarilyUnlockedApp()
                }
            }
        }

        val intent = Intent(this, PasswordOverlayActivity::class.java).apply {
            flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_FROM_BACKGROUND or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra("locked_package", packageName)
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting PasswordOverlayActivity for package: $packageName", e)
        } finally {
            AppLockManager.clearTemporarilyUnlockedApp()
        }
    }

    private fun getCurrentForegroundAppInfo(): String? {
        val time = System.currentTimeMillis()
        val usageStatsList =
            usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                time - 1000 * 10,
                time
            )

        if (usageStatsList != null && usageStatsList.isNotEmpty()) {
            var recentPackageName: String? = null
            val events = usageStatsManager.queryEvents(time - 1000 * 100, time)
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.className == "dev.pranav.applock.features.lockscreen.ui.PasswordOverlayActivity") {
                    Log.d(TAG, "Ignoring PasswordOverlayActivity event")
                    continue
                }
                if (event.className in knownRecentsClasses) continue
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    recentPackageName = event.packageName
                }
            }
            return recentPackageName
        }
        return null
    }
}
