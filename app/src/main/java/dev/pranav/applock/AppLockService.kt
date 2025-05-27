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

    private val lockedApps = mutableSetOf(
        "com.google.android.youtube",
        "com.android.chrome"
    )

    private var temporarilyUnlockedPackage: String? = null

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "AppLockServiceChannel"
        const val NOTIFICATION_ID = 123
        var isOverlayActive = false
        var currentLockedPackage: String? = null
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
        Log.d(TAG, "AppLockService onStartCommand")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "AppLockService onDestroy")
        handler.removeCallbacks(appMonitorRunnable)
        (applicationContext as AppLockApplication).appLockServiceInstance = null
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "App Lock Service Channel",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }

    private fun createNotification(): Notification {
        return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("App Lock Running")
            .setContentText("Monitoring app launches...")
            .build()
    }

    private fun startMonitoringApps() {
        handler.post(appMonitorRunnable)
    }

    private val appMonitorRunnable = object : Runnable {
        override fun run() {
            val currentTime = System.currentTimeMillis()
            val usageEvents = usageStatsManager.queryEvents(currentTime - 5000, currentTime)
            val event = UsageEvents.Event()

            var detectedForegroundPackage: String? = null

            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                    event.eventType == UsageEvents.Event.ACTIVITY_PAUSED
                ) {
                    detectedForegroundPackage = event.packageName
                }
            }

            detectedForegroundPackage?.let { foregroundPackage ->
                Log.d(
                    TAG,
                    "Current Foreground app: $foregroundPackage, last: $lastForegroundPackage, unlocked: $temporarilyUnlockedPackage"
                )

                if (foregroundPackage == packageName ||
                    foregroundPackage == "com.google.android.apps.nexuslauncher" ||
                    foregroundPackage == "com.android.launcher3" ||
                    foregroundPackage == "com.sec.android.app.launcher" ||
                    foregroundPackage.contains("launcher")
                ) {
                    if (temporarilyUnlockedPackage != null && (foregroundPackage.contains("launcher") || foregroundPackage == packageName)) {
                        Log.d(
                            TAG,
                            "Launcher/Our App detected. Clearing temporarilyUnlockedPackage."
                        )
                        temporarilyUnlockedPackage = null
                    }
                    lastForegroundPackage = foregroundPackage
                    handler.postDelayed(this, 100)
                    return
                }

                if (foregroundPackage == temporarilyUnlockedPackage) {
                    Log.d(TAG, "$foregroundPackage is temporarily unlocked, ignoring.")
                    if (currentLockedPackage == foregroundPackage) {
                        currentLockedPackage = null
                    }
                    lastForegroundPackage = foregroundPackage
                    handler.postDelayed(this, 100)
                    return
                }

                if (temporarilyUnlockedPackage != null && foregroundPackage != temporarilyUnlockedPackage) {
                    Log.d(
                        TAG,
                        "New app ($foregroundPackage) detected. Clearing temporarilyUnlockedPackage."
                    )
                    temporarilyUnlockedPackage = null
                }

                if (lockedApps.contains(foregroundPackage) && !isOverlayActive) {
                    Log.d(TAG, "Locked app detected: $foregroundPackage (showing lock screen)")
                    Log.d(TAG, "All locked apps: $lockedApps")

                    if (temporarilyUnlockedPackage == foregroundPackage) {
                        Log.d(
                            TAG,
                            "Forcing reset of temporarily unlocked state for $foregroundPackage"
                        )
                        temporarilyUnlockedPackage = null
                    }

                    currentLockedPackage = foregroundPackage
                    isOverlayActive = true

                    handler.postDelayed({
                        showPasswordOverlay()
                    }, 100)
                }
                lastForegroundPackage = foregroundPackage
            }
            handler.postDelayed(this, 100)
        }
    }

    private fun showPasswordOverlay() {
        val intent = Intent(this, PasswordOverlayScreen::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
    }

    fun unlockApp(unlockedPackageName: String) {
        temporarilyUnlockedPackage = unlockedPackageName
        Log.d(TAG, "App unlocked: $unlockedPackageName. Setting as temporarily unlocked.")

        isOverlayActive = false
        currentLockedPackage = null
    }

    fun validatePassword(password: String): Boolean {
        // TODO: Implement secure password hashing and comparison here
        return password == "123456"
    }

    fun addLockedApp(packageName: String) {
        lockedApps.add(packageName)
        persistLockedApps()

        val currentForegroundApp = getCurrentForegroundApp()
        if (packageName == currentForegroundApp && !isOverlayActive) {
            Log.d(TAG, "Newly locked app is in foreground. Locking immediately: $packageName")
            currentLockedPackage = packageName
            isOverlayActive = true
            showPasswordOverlay()
        }
    }

    fun removeLockedApp(packageName: String) {
        lockedApps.remove(packageName)
        persistLockedApps()

        if (temporarilyUnlockedPackage == packageName) {
            temporarilyUnlockedPackage = null
        }
    }

    private fun persistLockedApps() {
        val prefs = getSharedPreferences("AppLockPrefs", MODE_PRIVATE)
        prefs.edit {
            putStringSet("locked_apps", lockedApps)
        }
        Log.d(TAG, "Persisted locked apps: $lockedApps")
    }

    private fun loadLockedApps() {
        val prefs = getSharedPreferences("AppLockPrefs", MODE_PRIVATE)
        val savedApps = prefs.getStringSet("locked_apps", null)
        savedApps?.let {
            lockedApps.clear()
            lockedApps.addAll(it)
            Log.d(TAG, "Loaded locked apps: $lockedApps")
        }
    }

    private fun getCurrentForegroundApp(): String? {
        val currentTime = System.currentTimeMillis()
        val usageEvents = usageStatsManager.queryEvents(currentTime - 5000, currentTime)
        val event = UsageEvents.Event()

        var foregroundPackage: String? = null

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                event.eventType == UsageEvents.Event.ACTIVITY_PAUSED
            ) {
                foregroundPackage = event.packageName
            }
        }

        return foregroundPackage
    }

    fun isAppLocked(packageName: String): Boolean {
        return lockedApps.contains(packageName)
    }

}
