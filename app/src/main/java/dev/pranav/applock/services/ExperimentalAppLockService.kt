package dev.pranav.applock.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.inputmethod.InputMethodManager
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import dev.pranav.applock.R
import dev.pranav.applock.core.broadcast.DeviceAdmin
import dev.pranav.applock.core.utils.appLockRepository
import dev.pranav.applock.core.utils.hasUsagePermission
import dev.pranav.applock.data.repository.AppLockRepository
import dev.pranav.applock.data.repository.AppLockRepository.Companion.shouldStartService
import dev.pranav.applock.data.repository.BackendImplementation
import dev.pranav.applock.features.lockscreen.ui.PasswordOverlayActivity
import java.util.Timer
import java.util.TimerTask

class ExperimentalAppLockService : Service() {
    private lateinit var appLockRepository: AppLockRepository
    private var timer: Timer? = null
    private lateinit var usageStatsManager: UsageStatsManager
    private val TAG = "ExperimentalAppLockService"
    private val CHANNEL_ID = "ExperimentalAppLockServiceChannel"
    private val NOTIFICATION_ID = 113

    override fun onCreate() {
        super.onCreate()
        appLockRepository = appLockRepository()
        usageStatsManager = getSystemService()!!

        if (!shouldStartService(appLockRepository, this::class.java)) {
            Log.d(TAG, "Service not needed, stopping service")
            stopSelf()
            return
        }

        if (!this.hasUsagePermission()) {
            Log.e(TAG, "Usage permission not granted, stopping service")
            AppLockManager.startFallbackServices(this, ExperimentalAppLockService::class.java)
            stopSelf()
            return
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "ExperimentalAppLockService started")
        AppLockManager.resetRestartAttempts("ExperimentalAppLockService")
        appLockRepository.setActiveBackend(BackendImplementation.USAGE_STATS)

        // Stop other services to ensure only one runs at a time
        stopOtherServices()

        AppLockManager.isLockScreenShown.set(false) // Set to false on start to ensure correct initial state

        timer = Timer()
        timer?.schedule(object : TimerTask() {
            override fun run() {
                if (isDeviceLocked()) {
                    AppLockManager.appUnlockTimes.clear()
                    AppLockManager.clearTemporarilyUnlockedApp()
                    return
                }
                val foregroundApp = getCurrentForegroundAppInfo()
                if (foregroundApp == null) {
                    AppLockManager.clearTemporarilyUnlockedApp()
                    return
                }
                if (foregroundApp.packageName == packageName || foregroundApp.packageName in getKeyboardPackageNames()) {
                    return // Skip if the current app is this service or a keyboard app
                }
                checkAndLockApp(foregroundApp.packageName, System.currentTimeMillis())
            }
        }, 0, 250)


        val dpm =
            getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val component = ComponentName(this, DeviceAdmin::class.java)
        val hasDeviceAdmin = dpm.isAdminActive(component)

        createNotificationChannel()

        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                if (hasDeviceAdmin) ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED else ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }


    private fun getKeyboardPackageNames(): List<String> {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        return imm.enabledInputMethodList.map { it.packageName }
    }

    private fun stopOtherServices() {
        Log.d(TAG, "Stopping other app lock services")

        try {
            stopService(Intent(this, ShizukuAppLockService::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping other services", e)
        }
    }

    override fun onDestroy() {
        timer?.cancel()
        Log.d(TAG, "ExperimentalAppLockService destroyed")
        if (shouldStartService(appLockRepository, this::class.java)) {
            AppLockManager.startFallbackServices(this, ExperimentalAppLockService::class.java)
        }
        AppLockManager.isLockScreenShown.set(false)

        val notificationManager =
            getSystemService(NotificationManager::class.java)
        notificationManager.cancel(NOTIFICATION_ID)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun checkAndLockApp(packageName: String, currentTime: Long) {
        if (AppLockManager.currentBiometricState == AppLockAccessibilityService.BiometricState.AUTH_STARTED) {
            return
        }
        if (AppLockManager.isLockScreenShown.get()) {
            return
        }
        if (AppLockManager.isAppTemporarilyUnlocked(packageName)) {
            return
        } else {
            AppLockManager.clearTemporarilyUnlockedApp()
        }

        val lockedApps = appLockRepository.getLockedApps()
        if (!lockedApps.contains(packageName)) {
            return
        }

        val unlockDuration = appLockRepository.getUnlockTimeDuration()
        val unlockTimestamp = AppLockManager.appUnlockTimes[packageName] ?: 0

        Log.d(
            TAG,
            "Checking app lock for $packageName at $currentTime, unlockTimestamp: $unlockTimestamp, unlockDuration: $unlockDuration"
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

        val intent = Intent(this, PasswordOverlayActivity::class.java).apply {
            flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_FROM_BACKGROUND or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra("locked_package", packageName)
        }

        try {
            AppLockManager.isLockScreenShown.set(true) // Set to true before attempting to start
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting PasswordOverlayActivity for package: $packageName", e)
            AppLockManager.isLockScreenShown.set(false) // Reset on failure
        }
    }


    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "AppLock Service",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("App Lock")
            .setContentText("Protecting your apps")
            .setSmallIcon(R.drawable.baseline_shield_24)
            .setSmallIcon(R.drawable.baseline_shield_24)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
    }

    private fun getCurrentForegroundAppInfo(): ForegroundAppInfo? {
        val time = System.currentTimeMillis()
        val usageStatsList =
            usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                time - 1000 * 10,
                time
            )

        if (usageStatsList != null && usageStatsList.isNotEmpty()) {
            var recentAppInfo: ForegroundAppInfo? = null
            val events = usageStatsManager.queryEvents(time - 1000 * 100, time)
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.className == "dev.pranav.applock.features.lockscreen.ui.PasswordOverlayActivity") {
                    continue
                }
                if (event.className in knownRecentsClasses || event.className in knownAdminConfigClasses) {
                    continue
                }
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    recentAppInfo = ForegroundAppInfo(
                        packageName = event.packageName,
                        className = event.className,
                        timestamp = event.timeStamp
                    )
                }
            }
            return recentAppInfo
        }
        return null
    }

    data class ForegroundAppInfo(
        val packageName: String,
        val className: String,
        val timestamp: Long
    ) {
        override fun toString(): String {
            return "ForegroundAppInfo(packageName='$packageName', className='$className', timestamp=$timestamp)"
        }
    }
}
