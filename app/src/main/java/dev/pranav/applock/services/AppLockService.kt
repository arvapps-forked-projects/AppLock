package dev.pranav.applock.services

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import dev.pranav.applock.AppLockApplication
import dev.pranav.applock.MainActivity
import dev.pranav.applock.R
import dev.pranav.applock.data.repository.AppLockRepository
import dev.pranav.applock.features.lockscreen.ui.PasswordOverlayActivity

sealed class LockServiceState {
    object Inactive : LockServiceState()
    data class OverlayDisplayed(val packageName: String) : LockServiceState()
    data class AppTemporarilyUnlocked(val packageName: String, val unlockTime: Long) :
        LockServiceState()

    object BiometricAuthInProgress : LockServiceState()
}

class AppLockService : Service() {

    private val TAG = "AppLockService"
    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var handler: Handler
    private var lastForegroundPackage: String? = null

    private lateinit var appLockRepository: AppLockRepository
    private val lockedAppsCache = mutableSetOf<String>()

    private var currentServiceState: LockServiceState = LockServiceState.Inactive

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "AppLockServiceChannel"
        const val NOTIFICATION_ID = 123

        private const val HOME_SCREEN_LOCK_DELAY_MS = 3000L
        private const val STANDARD_POLLING_INTERVAL_MS = 100L
    }

    override fun onCreate() {
        super.onCreate()
        usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        handler = Handler(Looper.getMainLooper())
        appLockRepository = AppLockRepository(applicationContext)
        createNotificationChannel()
        loadLockedAppsFromRepository()
        startForeground(NOTIFICATION_ID, createNotification())
        startMonitoringApps()
        (applicationContext as AppLockApplication).appLockServiceInstance = this
        currentServiceState = LockServiceState.Inactive
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(appMonitorRunnable)
        (applicationContext as AppLockApplication).appLockServiceInstance = null
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "App Lock Service Channel",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        serviceChannel.description = "Shows when App Lock is running to protect your apps"
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }

    private fun createNotification(): Notification {
        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("AppLock Active")
            .setContentText("Protecting your locked applications")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        builder.setContentIntent(pendingIntent)

        return builder.build()
    }

    private fun startMonitoringApps() {
        handler.post(appMonitorRunnable)
    }

    private val appMonitorRunnable = object : Runnable {
        override fun run() {
            detectAndHandleForegroundApp()
            handler.postDelayed(this, STANDARD_POLLING_INTERVAL_MS)
        }
    }

    private fun detectAndHandleForegroundApp() {
        val currentTime = System.currentTimeMillis()
        val detectedForegroundPackage = getCurrentForegroundAppInfo(currentTime)

        if (isDeviceLocked()) {
            return
        }

        if (currentServiceState is LockServiceState.OverlayDisplayed) {
            val state = currentServiceState as LockServiceState.OverlayDisplayed
            if (detectedForegroundPackage == state.packageName) {
                handler.post { refreshPasswordOverlay(state.packageName) }
                return
            }
        }

        if (isLauncherApp(detectedForegroundPackage)) {
            handleHomeScreenDetected(detectedForegroundPackage, currentTime)
            lastForegroundPackage = detectedForegroundPackage
            return
        }

        if (detectedForegroundPackage != null && detectedForegroundPackage != packageName) {
            // Verify that the app is actually locked before proceeding
            if (!lockedAppsCache.contains(detectedForegroundPackage)) {
                lastForegroundPackage = detectedForegroundPackage
                return
            }

            if (shouldShowLockScreenForApp(detectedForegroundPackage)) {
                showLockScreenFor(detectedForegroundPackage)
            } else {
                processForegroundApp(detectedForegroundPackage, currentTime)
            }
        }
        lastForegroundPackage = detectedForegroundPackage
    }

    private fun getCurrentForegroundAppInfo(currentTime: Long): String? {
        var currentForegroundApp: String? = null
        val usageEvents = usageStatsManager.queryEvents(currentTime - 2000, currentTime)
        val event = UsageEvents.Event()
        var latestTimestamp: Long = 0

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                if (event.timeStamp > latestTimestamp) {
                    latestTimestamp = event.timeStamp
                    currentForegroundApp = event.packageName
                }
            }
        }
        return currentForegroundApp ?: getCurrentForegroundAppLegacy()
    }

    private fun shouldShowLockScreenForApp(packageName: String): Boolean {
        if (!lockedAppsCache.contains(packageName)) return false
        if (packageName == this.packageName) return false

        return when (currentServiceState) {
            is LockServiceState.Inactive -> true
            is LockServiceState.AppTemporarilyUnlocked -> {
                val state = currentServiceState as LockServiceState.AppTemporarilyUnlocked
                state.packageName != packageName
            }

            is LockServiceState.OverlayDisplayed, LockServiceState.BiometricAuthInProgress -> false
        }
    }

    private fun showLockScreenFor(packageName: String) {
        currentServiceState = LockServiceState.OverlayDisplayed(packageName)
        handler.post { showPasswordOverlay(packageName) }
    }

    private fun handleHomeScreenDetected(detectedForegroundPackage: String?, currentTime: Long) {
        Log.d(TAG, "Home screen detected. Current state: $currentServiceState")

        if (currentServiceState is LockServiceState.AppTemporarilyUnlocked) {
            val state = currentServiceState as LockServiceState.AppTemporarilyUnlocked

            // Clear the temporary unlock state immediately when home screen is detected
            currentServiceState = LockServiceState.Inactive
            Log.d(TAG, "Clearing temporarily unlocked app (${state.packageName}) immediately")
        } else if (currentServiceState is LockServiceState.OverlayDisplayed) {
            currentServiceState = LockServiceState.Inactive
        }
    }

    private fun processForegroundApp(foregroundPackage: String, currentTime: Long) {
        when (val state = currentServiceState) {
            is LockServiceState.AppTemporarilyUnlocked -> {
                if (foregroundPackage != state.packageName) {
                    currentServiceState = LockServiceState.Inactive
                    if (shouldShowLockScreenForApp(foregroundPackage)) {
                        showLockScreenFor(foregroundPackage)
                    }
                }
            }

            is LockServiceState.Inactive -> {
                // Clear any temporary unlocks when a different app becomes active
                if (lockedAppsCache.contains(foregroundPackage)) {
                    showLockScreenFor(foregroundPackage)
                }
            }

            is LockServiceState.OverlayDisplayed -> {
                if (state.packageName != foregroundPackage && lockedAppsCache.contains(
                        foregroundPackage
                    )
                ) {
                    showLockScreenFor(foregroundPackage)
                } else if (!lockedAppsCache.contains(foregroundPackage)) {
                    currentServiceState = LockServiceState.Inactive
                }
            }

            is LockServiceState.BiometricAuthInProgress -> {
                // Keep current state during biometric authentication
            }
        }
    }

    private fun refreshPasswordOverlay(packageName: String) {
        if (currentServiceState is LockServiceState.OverlayDisplayed &&
            (currentServiceState as LockServiceState.OverlayDisplayed).packageName == packageName
        ) {
            val currentIntent = Intent(this, PasswordOverlayActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT
                )
                putExtra("locked_package", packageName)
            }
            startActivity(currentIntent)
        }
    }

    private fun showPasswordOverlay(packageName: String) {
        val intent = Intent(this, PasswordOverlayActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra("locked_package", packageName)
        }
        startActivity(intent)
    }

    fun unlockApp(unlockedPackageName: String) {
        currentServiceState = LockServiceState.AppTemporarilyUnlocked(
            unlockedPackageName,
            System.currentTimeMillis()
        )
    }

    fun activePackageName(): String? {
        return if (currentServiceState is LockServiceState.OverlayDisplayed) {
            (currentServiceState as LockServiceState.OverlayDisplayed).packageName
        } else {
            lastForegroundPackage
        }
    }

    fun temporarilyUnlockAppWithBiometrics(packageName: String) {
        currentServiceState = LockServiceState.AppTemporarilyUnlocked(
            packageName,
            System.currentTimeMillis()
        )
    }

    fun reportBiometricAuthStarted() {
        currentServiceState = LockServiceState.BiometricAuthInProgress
    }

    fun reportBiometricAuthFinished() {
        if (currentServiceState is LockServiceState.BiometricAuthInProgress) {
            currentServiceState = LockServiceState.Inactive
        }
    }

    fun isAppLocked(packageName: String): Boolean {
        return lockedAppsCache.contains(packageName)
    }

    fun validatePassword(password: String): Boolean {
        val storedPassword = appLockRepository.getPassword() ?: "123456"
        return password == storedPassword
    }

    fun setPassword(password: String) {
        appLockRepository.setPassword(password)
    }

    private fun loadLockedAppsFromRepository() {
        val savedLockedApps = appLockRepository.getLockedApps()
        lockedAppsCache.clear()
        lockedAppsCache.addAll(savedLockedApps)
    }

    fun addLockedApp(packageName: String) {
        appLockRepository.addLockedApp(packageName)
        lockedAppsCache.add(packageName)
    }

    fun removeLockedApp(packageName: String) {
        appLockRepository.removeLockedApp(packageName)
        lockedAppsCache.remove(packageName)
    }

    private fun isLauncherApp(packageName: String?): Boolean {
        if (packageName == null) return false
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = packageManager.resolveActivity(intent, 0)
        return resolveInfo?.activityInfo?.packageName == packageName
    }

    private fun getCurrentForegroundAppLegacy(): String? {
        var currentApp: String? = null
        val time = System.currentTimeMillis()
        val appList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            time - 1000 * 100,
            time
        )
        if (appList != null && appList.isNotEmpty()) {
            val sortedMap = sortedMapOf<Long, String>()
            for (usageStats in appList) {
                sortedMap[usageStats.lastTimeUsed] = usageStats.packageName
            }
            if (sortedMap.isNotEmpty()) {
                currentApp = sortedMap[sortedMap.lastKey()]
            }
        }
        return currentApp
    }

    fun getPackageNameForOverlay(): String? {
        return if (currentServiceState is LockServiceState.OverlayDisplayed) {
            (currentServiceState as LockServiceState.OverlayDisplayed).packageName
        } else {
            null
        }
    }

    private fun isDeviceLocked(): Boolean {
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return keyguardManager.isKeyguardLocked
    }
}
