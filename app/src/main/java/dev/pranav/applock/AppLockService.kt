package dev.pranav.applock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.content.edit

class AppLockService : Service() {

    private val TAG = "AppLockService"
    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var handler: Handler
    private var lastForegroundPackage: String? = null
    private var lastUnlockTime: Long = 0

    // Default locked apps - will be overridden by saved preferences
    private val lockedApps = mutableSetOf<String>()

    private var temporarilyUnlockedPackage: String? = null
    var isBiometricAuthInProgress = false

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "AppLockServiceChannel"
        const val NOTIFICATION_ID = 123
        var isOverlayActive = false
        var currentLockedPackage: String? = null
        var isBiometricAuthentication =
            false // This seems to track if biometric auth process is active

        // Time to wait before locking an app again after going to home screen
        private const val HOME_SCREEN_LOCK_DELAY_MS = 3000L

        // Polling intervals
        private const val STANDARD_POLLING_INTERVAL_MS = 100L
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AppLockService onCreate")
        usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        handler = Handler(Looper.getMainLooper())
        createNotificationChannel()
        loadLockedApps()
        startForeground(NOTIFICATION_ID, createNotification())
        startMonitoringApps()
        (applicationContext as AppLockApplication).appLockServiceInstance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(appMonitorRunnable)
        (applicationContext as AppLockApplication).appLockServiceInstance = null
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "App Lock Service Channel",
            NotificationManager.IMPORTANCE_LOW
        )
        // Add description to make the channel's purpose clear
        serviceChannel.description = "Shows when App Lock is running to protect your apps"
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }

    private fun createNotification(): Notification {
        // Import required for NotificationCompat
        val builder = androidx.core.app.NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("AppLock Active")
            .setContentText("Protecting your locked applications")
            .setSmallIcon(R.drawable.ic_notification) // This requires a notification icon in your drawable resources
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)

        // Create a PendingIntent for when the notification is tapped
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, notificationIntent,
            android.app.PendingIntent.FLAG_IMMUTABLE
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
        // Query events for a slightly longer period to catch transitions
        val usageEvents = usageStatsManager.queryEvents(currentTime - 2000, currentTime)
        val event = UsageEvents.Event()
        var detectedForegroundPackage: String?
        val recentLockedAppActivities = mutableSetOf<String>()

        // Process usage events to detect foreground app and recent locked app activities
        var latestTimestamp: Long = 0
        var currentForegroundAppFromEvents: String? = null

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                if (event.timeStamp > latestTimestamp) {
                    latestTimestamp = event.timeStamp
                    currentForegroundAppFromEvents = event.packageName
                }
                if (lockedApps.contains(event.packageName)) {
                    recentLockedAppActivities.add(event.packageName)
                }
            }
        }
        detectedForegroundPackage = currentForegroundAppFromEvents ?: getCurrentForegroundApp()

        // If lock screen is active but pushed to background, bring it back
        if (isOverlayActive && detectedForegroundPackage != null && lockedApps.contains(
                detectedForegroundPackage
            )
        ) {
            // Check if the current foreground app is the one that should be locked
            if (currentLockedPackage == detectedForegroundPackage) {
                handler.post { refreshPasswordOverlay() }
                return
            } else {
                // If another locked app comes to foreground while overlay is active for a different app
                // or if an unlocked app comes to foreground.
                // This might need more nuanced handling depending on desired UX.
                // For now, if overlay is active for a different app, we might want to let it be,
                // or switch the lock screen to the new app.
                // If an unlocked app is in foreground, the overlay should not be active.
            }
        }


        // Check if we're on home screen
        val isHomeScreen = isLauncherApp(detectedForegroundPackage)
        if (isHomeScreen) {
            handleHomeScreenDetected(detectedForegroundPackage)
            return
        }

        // Handle locked app detection
        if (shouldShowLockScreen(recentLockedAppActivities, detectedForegroundPackage)) {
            return // Lock screen shown, no further processing needed in this cycle
        }

        // Process foreground app state
        detectedForegroundPackage?.let { foregroundPackage ->
            processForegroundApp(foregroundPackage)
        }
    }


    private fun shouldShowLockScreen(
        recentLockedAppActivities: Set<String>,
        detectedForegroundPackage: String?
    ): Boolean {
        if (detectedForegroundPackage == null) return false

        // Skip showing lock screen for our own app
        if (detectedForegroundPackage == packageName) {
            return false
        }

        val isAppCurrentlyLocked = lockedApps.contains(detectedForegroundPackage)

        if (isAppCurrentlyLocked &&
            !isOverlayActive &&
            temporarilyUnlockedPackage != detectedForegroundPackage &&
            !isBiometricAuthInProgress && // If biometric prompt is actively showing
            !isBiometricAuthentication // If biometric auth was the last successful unlock method
        ) {
            Log.d(TAG, "Lock condition met for: $detectedForegroundPackage (showing lock screen)")
            showLockScreenFor(detectedForegroundPackage)
            return true
        }
        return false
    }


    private fun showLockScreenFor(packageName: String) {
        currentLockedPackage = packageName
        isOverlayActive = true
        // Ensure biometric auth in progress is reset if we are showing a new lock screen
        isBiometricAuthInProgress = false
        isBiometricAuthentication = false // Reset this too
        handler.post { showPasswordOverlay() }
    }

    private fun handleHomeScreenDetected(detectedForegroundPackage: String?) {
        // If we came from a locked app
        if (temporarilyUnlockedPackage != null) {
            Log.d(
                TAG,
                "User went to home screen from $temporarilyUnlockedPackage, scheduling unlock reset"
            )

            handler.postDelayed({
                // Check again if still on home screen before clearing
                if (isLauncherApp(getCurrentForegroundApp())) {
                    Log.d(TAG, "Clearing temporarily unlocked app after home screen timeout")
                    temporarilyUnlockedPackage = null
                    // If overlay was for this app and somehow still active (should not be), clear it.
                    if (currentLockedPackage == temporarilyUnlockedPackage) {
                        currentLockedPackage = null
                    }
                }
            }, HOME_SCREEN_LOCK_DELAY_MS)
        } else if (currentLockedPackage != null) {
            // If we have a current locked package but no temporarily unlocked package,
            // the user exited a locked app without unlocking it
            Log.d(TAG, "User exited locked app without unlocking, clearing lock state")
            currentLockedPackage = null
            isOverlayActive = false
        }

        lastForegroundPackage = detectedForegroundPackage
    }

    private fun processForegroundApp(foregroundPackage: String) {
        // Handle launcher app detection (already handled in detectAndHandleForegroundApp, but good for clarity)
        if (isLauncherApp(foregroundPackage)) {
            handleHomeScreenDetected(foregroundPackage)
            return
        }

        // If the current foreground app is the one temporarily unlocked, do nothing further.
        if (foregroundPackage == temporarilyUnlockedPackage) {
            Log.d(TAG, "$foregroundPackage is temporarily unlocked, ignoring.")
            if (currentLockedPackage == foregroundPackage) { // Ensure consistency
                currentLockedPackage = null
            }
            lastForegroundPackage = foregroundPackage
            return
        }

        // If a new app (not the temporarily unlocked one) comes to the foreground,
        // and it's not due to biometric auth flow for the *same* app.
        if (temporarilyUnlockedPackage != null &&
            foregroundPackage != temporarilyUnlockedPackage &&
            !isBiometricAuthInProgress // if biometric is in progress, it might be for the current temp unlocked app
        ) {
            Log.d(
                TAG,
                "New app ($foregroundPackage) detected. Clearing temporarilyUnlockedPackage ($temporarilyUnlockedPackage)."
            )
            temporarilyUnlockedPackage = null
        }

        // Check if the current foreground app needs to be locked
        // This is the main locking condition after other checks.
        if (lockedApps.contains(foregroundPackage) &&
            !isOverlayActive && // Don't show if already showing
            foregroundPackage != temporarilyUnlockedPackage && // Don't lock if temporarily unlocked
            !isBiometricAuthInProgress // Don't interfere if biometric auth is happening
        ) {
            Log.d(
                TAG,
                "Locked app detected in processForegroundApp: $foregroundPackage (showing lock screen)"
            )
            showLockScreenFor(foregroundPackage)
        }

        lastForegroundPackage = foregroundPackage
    }


    private fun refreshPasswordOverlay() {
        if (isOverlayActive && currentLockedPackage != null) {
            Log.d(TAG, "Refreshing password overlay for $currentLockedPackage")
            val currentIntent = Intent(this, PasswordOverlayScreen::class.java)
            currentIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT
            )
            // Ensure the package name is passed, in case the overlay needs to re-verify
            currentIntent.putExtra("locked_package", currentLockedPackage)
            startActivity(currentIntent)
        }
    }

    private fun showPasswordOverlay() {
        val intent = Intent(this, PasswordOverlayScreen::class.java)
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
        intent.putExtra("locked_package", currentLockedPackage)
        startActivity(intent)
    }

    fun unlockApp(unlockedPackageName: String) {
        temporarilyUnlockedPackage = unlockedPackageName
        lastUnlockTime = System.currentTimeMillis()
        Log.d(TAG, "App unlocked via PIN: $unlockedPackageName. Setting as temporarily unlocked.")
        isOverlayActive = false
        if (currentLockedPackage == unlockedPackageName) {
            currentLockedPackage = null
        }
        isBiometricAuthInProgress = false // Ensure this is reset
        isBiometricAuthentication = false // Reset this too
    }

    fun temporarilyUnlockApp(packageName: String) {
        Log.d(TAG, "Biometric auth: temporarily unlocking app: $packageName")
        temporarilyUnlockedPackage = packageName
        lastUnlockTime = System.currentTimeMillis()
        if (currentLockedPackage == packageName) {
            currentLockedPackage = null
        }
        isOverlayActive = false
        isBiometricAuthInProgress = false // Crucial: reset after successful biometric auth
        isBiometricAuthentication = false // Reset after auth success
        Log.d(
            TAG,
            "App state after temp unlock - temporarilyUnlockedPackage: $temporarilyUnlockedPackage, isOverlayActive: $isOverlayActive, currentLockedPackage: $currentLockedPackage"
        )
    }

    fun isAppLocked(packageName: String): Boolean {
        return lockedApps.contains(packageName)
    }

    fun isAppTemporarilyUnlocked(packageName: String): Boolean {
        return temporarilyUnlockedPackage == packageName
    }

    fun validatePassword(password: String): Boolean {
        val sharedPrefs = getSharedPreferences("app_lock_prefs", MODE_PRIVATE)
        val storedPassword = sharedPrefs.getString("password", "123456")
        return password == storedPassword
    }

    fun setPassword(password: String) {
        val sharedPrefs = getSharedPreferences("app_lock_prefs", MODE_PRIVATE)
        sharedPrefs.edit { putString("password", password) }
    }

    private fun loadLockedApps() {
        val sharedPrefs = getSharedPreferences("app_lock_prefs", MODE_PRIVATE)
        val savedLockedApps = sharedPrefs.getStringSet("locked_apps", null)
        if (savedLockedApps != null) {
            lockedApps.clear()
            lockedApps.addAll(savedLockedApps)
        }
        Log.d(TAG, "Loaded locked apps: $lockedApps")
    }

    fun addLockedApp(packageName: String) {
        lockedApps.add(packageName)
        saveLockedApps()
    }

    fun removeLockedApp(packageName: String) {
        lockedApps.remove(packageName)
        if (temporarilyUnlockedPackage == packageName) { // If removing a temp unlocked app, clear its state
            temporarilyUnlockedPackage = null
        }
        saveLockedApps()
    }

    private fun saveLockedApps() {
        val sharedPrefs = getSharedPreferences("app_lock_prefs", MODE_PRIVATE)
        sharedPrefs.edit { putStringSet("locked_apps", lockedApps) }
        Log.d(TAG, "Saved locked apps: $lockedApps")
    }

    fun getLockedApps(): Set<String> {
        return lockedApps.toSet() // Return a copy
    }

    private fun isLauncherApp(packageName: String?): Boolean {
        if (packageName == null) return false
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = packageManager.resolveActivity(intent, 0)
        return resolveInfo?.activityInfo?.packageName == packageName
    }

    private fun getCurrentForegroundApp(): String? {
        var currentApp: String? = null
        val usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val appList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            time - 1000 * 10,
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
}
