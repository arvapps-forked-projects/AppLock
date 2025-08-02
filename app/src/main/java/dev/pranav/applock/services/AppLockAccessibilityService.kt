package dev.pranav.applock.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import dev.pranav.applock.core.broadcast.DeviceAdmin
import dev.pranav.applock.data.repository.AppLockRepository
import dev.pranav.applock.data.repository.BackendImplementation
import dev.pranav.applock.features.lockscreen.ui.PasswordOverlayActivity
import dev.pranav.applock.services.AppLockManager.isServiceRunning

@SuppressLint("AccessibilityPolicy")
class AppLockAccessibilityService : AccessibilityService() {
    private lateinit var appLockRepository: AppLockRepository

    // The last app that was on screen
    private var lastForegroundPackage = ""

    // Keeps a record of last 3 events stored to prevents false lock screen due to recents bug
    private val lastEvents = mutableListOf<Pair<AccessibilityEvent, Long>>()

    // Package name of the system app that provides the recent apps functionality
    private var recentsPackage = ""

    enum class BiometricState {
        IDLE, AUTH_STARTED
    }

    companion object {
        private const val TAG = "AppLockAccessibility"
        var isServiceRunning = false
        private var instance: AppLockAccessibilityService? = null
        private const val DEVICE_ADMIN_SETTINGS_PACKAGE = "com.android.settings"
        private const val APP_PACKAGE_PREFIX = "dev.pranav.applock"

        fun getInstance(): AppLockAccessibilityService? = instance
    }

    @SuppressLint("PrivateApi")
    override fun onCreate() {
        super.onCreate()
        appLockRepository = AppLockRepository(applicationContext)
        isServiceRunning = true
        instance = this

        startServices()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = serviceInfo
        info.eventTypes =
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or AccessibilityEvent.TYPE_WINDOWS_CHANGED or AccessibilityEvent.TYPE_VIEW_CLICKED or
                    AccessibilityEvent.TYPE_VIEW_FOCUSED or AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_VISUAL
        info.packageNames = null
        serviceInfo = info
        Log.d(TAG, "Accessibility service connected")

        AppLockManager.resetRestartAttempts("AppLockAccessibilityService")
        appLockRepository.setActiveBackend(BackendImplementation.ACCESSIBILITY)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!::appLockRepository.isInitialized) return

        // Always handle device admin deactivation regardless of backend
        if (appLockRepository.isAntiUninstallEnabled() && event.packageName == DEVICE_ADMIN_SETTINGS_PACKAGE) {
            Log.d(TAG, "In settings, in activity: ${event.className}")
            checkForDeviceAdminDeactivation(event)
        }

        // Check if accessibility should handle app locking
        val currentBackend = appLockRepository.getBackendImplementation()
        val shouldHandleAppLocking = when (currentBackend) {
            BackendImplementation.ACCESSIBILITY -> {
//                Log.d(TAG, "Accessibility is the chosen backend, handling app locking")
                true
            }

            BackendImplementation.SHIZUKU -> {
                val shouldFallback = !isServiceRunning(ShizukuAppLockService::class.java)
//                if (shouldFallback) {
//                    Log.d(TAG, "Shizuku service not running, accessibility acting as fallback")
//                }
                shouldFallback
            }

            BackendImplementation.USAGE_STATS -> {
                val shouldFallback = !isServiceRunning(ExperimentalAppLockService::class.java)
//                if (shouldFallback) {
//                    Log.d(TAG, "Experimental service not running, accessibility acting as fallback")
//                }
                shouldFallback
            }
        }

        if (!shouldHandleAppLocking) {
            return
        }

        if (isDeviceLocked()) {
            Log.d(TAG, "Device is locked, ignoring event")
            AppLockManager.appUnlockTimes.clear()
            AppLockManager.clearTemporarilyUnlockedApp()
            return
        }

        val packageName = event.packageName?.toString() ?: return

        if (appLockRepository.isAntiUninstallEnabled() && packageName == DEVICE_ADMIN_SETTINGS_PACKAGE) {
            Log.d(TAG, "In settings, in activity: ${event.className}")
            checkForDeviceAdminDeactivation(event)
        }

        // Dont continue if its system or our app or keyboard package
        if (packageName.startsWith(APP_PACKAGE_PREFIX) || packageName in getKeyboardPackageNames()) {
            return
        }

        if (event.className in knownRecentsClasses) {
            recentsPackage = packageName
            Log.d(TAG, "Recents activity detected: $packageName")
            return
        }

        val lockedApps = appLockRepository.getLockedApps()

        // Apply the rapid events filter to all apps to prevent accidental locks when opening recents
        // This is a "hack" to prevent locking apps when user opens recents because a bug in Android causes
        // the last foreground app to come to foreground momentarily, atleast according to accessibility events
        if (lastEvents.size >= 2) {
            val firstEvent = lastEvents.first()
            val lastEvent = lastEvents.last()
            val secondLastEvent = lastEvents[lastEvents.size - 2]

            Log.d(
                TAG,
                "Last events: ${lastEvents.map { it.first.packageName.toString() + " at " + it.second.toString() }}"
            )

            if (secondLastEvent.first.packageName in lockedApps && lastEvent.first.packageName == "com.android.vending" && lastEvent.second - secondLastEvent.second < 5000) {
                return
            }

            if (secondLastEvent.first.packageName in getLauncherPackageNames(this) && AppLockManager.isAppTemporarilyUnlocked(
                    lastEvent.first.packageName.toString()
                ) && lastEvent.second - secondLastEvent.second < 5000
            ) {
                Log.d(TAG, "Ignoring rapid events for launcher and keyboard package: $packageName")
                return
            }

            if (firstEvent.first.packageName in lockedApps && firstEvent.first.packageName == lastEvent.first.packageName && lastEvent.second - firstEvent.second < 5000) {
                Log.d(TAG, "Ignoring rapid events for same package: $packageName")
                return
            }
        }

        if (AppLockManager.isAppTemporarilyUnlocked(packageName)) {
            return
        }
//        Log.d(
//            TAG,
//            "Clearing unlocked app: ${AppLockManager.temporarilyUnlockedApp} because new event for package: $packageName"
//        )
        AppLockManager.clearTemporarilyUnlockedApp()
        lastForegroundPackage = packageName
        checkAndLockApp(packageName, event.eventTime)
    }

    private fun checkForDeviceAdminDeactivation(event: AccessibilityEvent) {
        val rootNode = rootInActiveWindow ?: return

        try {
            // works atleast on Stock Android/Motorola devices
            val isDeviceAdminPage =
                (event.className in knownAdminConfigClasses) || (findNodeWithTextContaining(
                    rootNode,
                    "Device admin"
                ) != null)


            val isOurAppVisible = findNodeWithTextContaining(
                rootNode,
                "App Lock"
            ) != null || findNodeWithTextContaining(rootNode, "AppLock") != null

            if (!isDeviceAdminPage || !isOurAppVisible) {
                return
            }

            val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val component = ComponentName(this, DeviceAdmin::class.java)
            if (dpm.isAdminActive(component)) {
                // go to home screen with accessibility service
                Log.d(TAG, "Device admin is active, navigating to home screen")
                performGlobalAction(GLOBAL_ACTION_HOME)

                Log.d(TAG, rootInActiveWindow.className.toString())

                Toast.makeText(
                    this,
                    "Disable anti-uninstall from AppLock settings to remove this restriction.",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for device admin deactivation", e)
        }
    }

    private fun findNodeWithTextContaining(
        node: AccessibilityNodeInfo, text: String
    ): AccessibilityNodeInfo? {
        if (node.text?.toString()?.contains(text, ignoreCase = true) == true) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeWithTextContaining(child, text)
            if (result != null) {
                return result
            }
        }

        return null
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "Accessibility service unbound")
        isServiceRunning = false
        instance = null
        AppLockManager.startFallbackServices(this, AppLockAccessibilityService::class.java)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        instance = null

        Log.d(TAG, "Accessibility service destroyed")
        AppLockManager.startFallbackServices(this, AppLockAccessibilityService::class.java)
    }

    fun checkAndLockApp(packageName: String, currentTime: Long) {
        if (AppLockManager.isLockScreenShown.get()) { // Check if lock screen is already shown
            Log.d(TAG, "Password overlay already active, skipping app lock for $packageName")
            return
        }

        if (shouldBeIgnored(packageName)) {
            return
        }
        if (AppLockManager.currentBiometricState == BiometricState.AUTH_STARTED) {
            Log.d(TAG, "Biometric authentication in progress, skipping app lock for $packageName")
            return
        }
        if (AppLockManager.isAppTemporarilyUnlocked(packageName)) {
            Log.d(TAG, "App $packageName is temporarily unlocked, skipping app lock")
            return
        } else {
            AppLockManager.clearTemporarilyUnlockedApp()
        }

        val lockedApps = appLockRepository.getLockedApps()
        if (!lockedApps.contains(packageName)) {
            return
        }

        // Check if app is within unlock time period
        val unlockDuration = appLockRepository.getUnlockTimeDuration()
        val unlockTimestamp = AppLockManager.appUnlockTimes[packageName] ?: 0

        if (unlockDuration > 0 && unlockTimestamp > 0) {
            val elapsedMinutes = (currentTime - unlockTimestamp) / (60 * 1000)
            if (elapsedMinutes < unlockDuration) {
                Log.d(
                    TAG,
                    "App $packageName is within unlock time period ($elapsedMinutes/${unlockDuration}min)"
                )
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

        Log.d(TAG, "Locked app detected: $packageName")

        val intent = Intent(this, PasswordOverlayActivity::class.java).apply {
            flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_FROM_BACKGROUND or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra("locked_package", packageName)
        }

        try {
            AppLockManager.isLockScreenShown.set(true) // Set to true before attempting to start
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start password overlay: ${e.message}", e)
            AppLockManager.isLockScreenShown.set(false) // Reset on failure
        }
    }

    private fun shouldBeIgnored(packageName: String): Boolean {
        return packageName in getLauncherPackageNames(this)
    }

    private fun getKeyboardPackageNames(): List<String> {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        return imm.enabledInputMethodList.map { it.packageName }
    }

    private fun getLauncherPackageNames(context: Context): List<String> {
        val packageManager: PackageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        val resolveInfo =
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo.map { it.activityInfo.packageName }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    private fun startServices() {
        // Stop all services first to ensure only one runs at a time
        stopAllServices()

        // Start only the primary backend service
        when (appLockRepository.getBackendImplementation()) {
            BackendImplementation.SHIZUKU -> {
                Log.d(TAG, "Starting Shizuku service as primary backend")
                startService(Intent(this, ShizukuAppLockService::class.java))
            }

            BackendImplementation.USAGE_STATS -> {
                Log.d(TAG, "Starting Experimental service as primary backend")
                startService(Intent(this, ExperimentalAppLockService::class.java))
            }

            else -> {
                Log.d(
                    TAG,
                    "Accessibility service is the primary backend, no additional service needed"
                )
            }
        }
    }

    private fun stopAllServices() {
        Log.d(TAG, "Stopping all app lock services")

        try {
            stopService(Intent(this, ExperimentalAppLockService::class.java))
            stopService(Intent(this, ShizukuAppLockService::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping services", e)
        }
    }
}
