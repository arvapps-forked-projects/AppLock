package dev.pranav.applock.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
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

// Define Service State Sealed Class
sealed class LockServiceState {
    object Inactive : LockServiceState()
    data class OverlayDisplayed(val packageName: String) : LockServiceState()
    data class AppTemporarilyUnlocked(val packageName: String, val unlockTime: Long) :
        LockServiceState()

    // If BiometricInProgress needs to hold the package name for which it was initiated:
    // data class BiometricAuthInProgress(val packageName: String) : LockServiceState()
    // For now, a simpler BiometricAuthInProgress, assuming context is managed or not needed by this state itself
    object BiometricAuthInProgress : LockServiceState()
}

class AppLockService : Service() {

    private val TAG = "AppLockService"
    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var handler: Handler
    private var lastForegroundPackage: String? = null

    private lateinit var appLockRepository: AppLockRepository
    private val lockedAppsCache = mutableSetOf<String>() // Renamed for clarity

    // New state management
    private var currentServiceState: LockServiceState = LockServiceState.Inactive

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "AppLockServiceChannel"
        const val NOTIFICATION_ID = 123

        private const val HOME_SCREEN_LOCK_DELAY_MS = 3000L
        private const val STANDARD_POLLING_INTERVAL_MS = 100L
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AppLockService onCreate")
        usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        handler = Handler(Looper.getMainLooper())
        appLockRepository = AppLockRepository(applicationContext)
        createNotificationChannel()
        loadLockedAppsFromRepository()
        startForeground(NOTIFICATION_ID, createNotification())
        startMonitoringApps()
        (applicationContext as AppLockApplication).appLockServiceInstance = this
        currentServiceState = LockServiceState.Inactive // Initial state
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
        Log.d(TAG, "AppLockService onDestroy. State: $currentServiceState")
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

        // If lock screen is active (OverlayDisplayed state) and the foreground app matches,
        // ensure the overlay is still in front.
        if (currentServiceState is LockServiceState.OverlayDisplayed) {
            val state = currentServiceState as LockServiceState.OverlayDisplayed
            if (detectedForegroundPackage == state.packageName) {
                Log.d(TAG, "Overlay is active for $detectedForegroundPackage, refreshing.")
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
        // Fallback if UsageEvents is empty, though less ideal
        return currentForegroundApp ?: getCurrentForegroundAppLegacy()
    }

    private fun shouldShowLockScreenForApp(packageName: String): Boolean {
        if (!lockedAppsCache.contains(packageName)) return false // Not a locked app
        if (packageName == this.packageName) return false // Don't lock self

        return when (currentServiceState) {
            is LockServiceState.Inactive -> true
            is LockServiceState.AppTemporarilyUnlocked -> {
                val state = currentServiceState as LockServiceState.AppTemporarilyUnlocked
                state.packageName != packageName // If different app, or temp unlock expired (handled elsewhere)
            }

            is LockServiceState.OverlayDisplayed, LockServiceState.BiometricAuthInProgress -> false
        }
    }

    private fun showLockScreenFor(packageName: String) {
        Log.d(TAG, "Showing lock screen for: $packageName. Current state: $currentServiceState")
        currentServiceState = LockServiceState.OverlayDisplayed(packageName)
        // Reset biometric flags if any, as we are showing a new lock screen
        // isBiometricAuthentication = false // This flag from companion needs to be removed/rethought
        handler.post { showPasswordOverlay(packageName) }
    }

    private fun handleHomeScreenDetected(detectedForegroundPackage: String?, currentTime: Long) {
        Log.d(TAG, "Home screen detected. Current state: $currentServiceState")
        if (currentServiceState is LockServiceState.AppTemporarilyUnlocked) {
            val state = currentServiceState as LockServiceState.AppTemporarilyUnlocked
            Log.d(TAG, "User went to home from ${state.packageName}, scheduling unlock reset")
            handler.postDelayed({
                // Check if still on home screen and if the temp unlock hasn't been superseded by another state
                if (isLauncherApp(getCurrentForegroundAppInfo(System.currentTimeMillis())) &&
                    currentServiceState is LockServiceState.AppTemporarilyUnlocked &&
                    (currentServiceState as LockServiceState.AppTemporarilyUnlocked).packageName == state.packageName
                ) {
                    Log.d(
                        TAG,
                        "Clearing temporarily unlocked app (${state.packageName}) after home screen timeout"
                    )
                    currentServiceState = LockServiceState.Inactive
                }
            }, HOME_SCREEN_LOCK_DELAY_MS)
        } else if (currentServiceState is LockServiceState.OverlayDisplayed) {
            Log.d(TAG, "User exited locked app without unlocking. Resetting state.")
            currentServiceState = LockServiceState.Inactive
        }
        // lastForegroundPackage = detectedForegroundPackage // Set by caller
    }

    private fun processForegroundApp(foregroundPackage: String, currentTime: Long) {
        Log.d(
            TAG,
            "Processing foreground app: $foregroundPackage. Current state: $currentServiceState"
        )
        when (val state = currentServiceState) {
            is LockServiceState.AppTemporarilyUnlocked -> {
                if (foregroundPackage != state.packageName) {
                    // New app has come to foreground, reset temporary unlock
                    Log.d(
                        TAG,
                        "New app ($foregroundPackage) detected. Clearing temp unlock for ${state.packageName}."
                    )
                    currentServiceState = LockServiceState.Inactive
                    // Re-check if this new app needs locking (recursive, be careful or simplify)
                    if (shouldShowLockScreenForApp(foregroundPackage)) {
                        showLockScreenFor(foregroundPackage)
                    }
                } else {
                    // Still the same temporarily unlocked app, do nothing, extend or refresh timeout if needed
                }
            }

            is LockServiceState.Inactive -> {
                // This case should ideally be caught by shouldShowLockScreenForApp if the app is locked.
                // If it's an unlocked app, state remains Inactive.
                if (lockedAppsCache.contains(foregroundPackage)) {
                    Log.w(
                        TAG,
                        "Inactive state but locked app $foregroundPackage in foreground. Should have been caught by shouldShowLockScreenForApp."
                    )
                    showLockScreenFor(foregroundPackage) // Attempt to lock
                }
            }

            is LockServiceState.OverlayDisplayed -> {
                // If overlay is for a different app, something is wrong. Reset.
                if (state.packageName != foregroundPackage && lockedAppsCache.contains(
                        foregroundPackage
                    )
                ) {
                    Log.w(
                        TAG,
                        "Overlay was for ${state.packageName} but $foregroundPackage is in front. Resetting."
                    )
                    showLockScreenFor(foregroundPackage)
                } else if (!lockedAppsCache.contains(foregroundPackage)) {
                    Log.d(
                        TAG,
                        "Foreground app $foregroundPackage is not locked, and overlay was for ${state.packageName}. Resetting state."
                    )
                    currentServiceState = LockServiceState.Inactive
                }
            }

            is LockServiceState.BiometricAuthInProgress -> {
                // If biometric auth is in progress, typically no other app should take precedence
                // unless it's part of the auth flow (e.g. system dialog).
                // If a different app appears, might need to cancel biometric auth or reassess.
                Log.d(TAG, "Biometric auth in progress. Foreground app: $foregroundPackage")
            }
        }
        // lastForegroundPackage = foregroundPackage // Set by caller
    }

    private fun refreshPasswordOverlay(packageName: String) {
        // Only refresh if the overlay should be active for this package
        if (currentServiceState is LockServiceState.OverlayDisplayed && (currentServiceState as LockServiceState.OverlayDisplayed).packageName == packageName) {
            Log.d(TAG, "Refreshing password overlay for $packageName")
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

    // Called when PIN is correct
    fun unlockApp(unlockedPackageName: String) {
        Log.d(TAG, "App unlocked via PIN: $unlockedPackageName. Updating state.")
        currentServiceState =
            LockServiceState.AppTemporarilyUnlocked(unlockedPackageName, System.currentTimeMillis())
        // Companion object flags like isOverlayActive, currentLockedPackage, isBiometricAuthentication
        // should be phased out or their interaction with the new state machine clarified.
        // For now, PasswordOverlayActivity might still rely on them.
    }

    // Called when Biometric is successful
    fun temporarilyUnlockAppWithBiometrics(packageName: String) {
        Log.d(TAG, "Biometric auth successful for: $packageName. Updating state.")
        currentServiceState =
            LockServiceState.AppTemporarilyUnlocked(packageName, System.currentTimeMillis())
        // Companion object flags update might be needed here if PasswordOverlayActivity reads them.
    }

    // Call this when starting biometric prompt
    fun reportBiometricAuthStarted() {
        Log.d(TAG, "Biometric authentication process started.")
        currentServiceState = LockServiceState.BiometricAuthInProgress
    }

    // Call this if biometric prompt is cancelled or fails before showing our lock screen
    fun reportBiometricAuthFinished() {
        if (currentServiceState is LockServiceState.BiometricAuthInProgress) {
            Log.d(TAG, "Biometric authentication process finished (cancelled/failed).")
            currentServiceState = LockServiceState.Inactive // Or revert to previous state if known
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
        Log.d(TAG, "Loaded locked apps from repository: $lockedAppsCache")
    }

    fun addLockedApp(packageName: String) {
        appLockRepository.addLockedApp(packageName)
        lockedAppsCache.add(packageName)
        Log.d(TAG, "Added locked app: $packageName. Current cache: $lockedAppsCache")
    }

    fun removeLockedApp(packageName: String) {
        appLockRepository.removeLockedApp(packageName)
        lockedAppsCache.remove(packageName)
        Log.d(TAG, "Removed locked app: $packageName. Current cache: $lockedAppsCache")
    }

    private fun isLauncherApp(packageName: String?): Boolean {
        if (packageName == null) return false
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = packageManager.resolveActivity(intent, 0)
        return resolveInfo?.activityInfo?.packageName == packageName
    }

    // Renamed from getCurrentForegroundApp to avoid confusion with the one using UsageEvents
    private fun getCurrentForegroundAppLegacy(): String? {
        var currentApp: String? = null
        // usageStatsManager is already an instance variable
        val time = System.currentTimeMillis()
        val appList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            time - 1000 * 100, // Increased window slightly for reliability
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

    // Public method for PasswordOverlayActivity to check current overlay target
    // This is a temporary measure while refactoring companion object variables.
    fun getPackageNameForOverlay(): String? {
        return if (currentServiceState is LockServiceState.OverlayDisplayed) {
            (currentServiceState as LockServiceState.OverlayDisplayed).packageName
        } else {
            null
        }
    }
}
