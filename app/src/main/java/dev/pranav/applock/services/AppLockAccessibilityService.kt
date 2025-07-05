package dev.pranav.applock.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.app.KeyguardManager
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
import dev.pranav.applock.features.lockscreen.ui.PasswordOverlayActivity
import java.util.concurrent.ConcurrentHashMap

@SuppressLint("AccessibilityPolicy")
class AppLockAccessibilityService : AccessibilityService() {
    private lateinit var appLockRepository: AppLockRepository

    // The last app that was on screen
    private var lastForegroundPackage = ""

    // currently unlocked apps package name
    private var temporarilyUnlockedApp: String = ""

    // Whether biometric authentication has happened or not
    private var currentBiometricState = BiometricState.IDLE

    // Keeps a record of last 3 events stored to prevents false lock screen due to recents bug
    private val lastEvents = mutableListOf<Pair<AccessibilityEvent, Long>>()

    // Map to store unlock timestamps for apps
    private val appUnlockTimes = ConcurrentHashMap<String, Long>()

    // Package name of the system app that provides the recent apps functionality
    private var recentsPackage = ""

    private var knownRecentsClasses = setOf(
        "com.android.systemui.recents.RecentsActivity",
        "com.android.quickstep.RecentsActivity",
        "com.android.systemui.recents.RecentsView",
        "com.android.systemui.recents.RecentsPanelView"
    )

    private var knownAdminConfigClasses = setOf(
        "com.android.settings.deviceadmin.DeviceAdminAdd",
        "com.android.settings.applications.specialaccess.deviceadmin.DeviceAdminAdd",
        "com.android.settings.deviceadmin.DeviceAdminSettings",
        "com.android.settings.deviceadmin.DeviceAdminAdd"
    )

    private val excludedPackages = setOf(
        "com.android.intentresolver",
    )

    enum class BiometricState {
        IDLE, AUTH_STARTED, AUTH_SUCCESSFUL
    }

    companion object {
        private const val TAG = "AppLockAccessibility"
        var isServiceRunning = false
        private var instance: AppLockAccessibilityService? = null
        private const val DEVICE_ADMIN_SETTINGS_PACKAGE = "com.android.settings"
        private const val APP_PACKAGE_PREFIX = "dev.pranav.applock"

        fun getInstance(): AppLockAccessibilityService? = instance
    }

    override fun onCreate() {
        super.onCreate()
        appLockRepository = AppLockRepository(applicationContext)
        isServiceRunning = true
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = serviceInfo
        info.eventTypes =
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or AccessibilityEvent.TYPE_WINDOWS_CHANGED or AccessibilityEvent.TYPE_VIEW_CLICKED or
                    AccessibilityEvent.TYPE_VIEW_FOCUSED or AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                    AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_VISUAL
        info.flags =
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
        info.packageNames = null
        serviceInfo = info
        Log.d(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!::appLockRepository.isInitialized) return

        if (isDeviceLocked()) {
            Log.d(TAG, "Device is locked, ignoring event")
            appUnlockTimes.clear() // Clear unlock times when device is locked
            return
        }

        if (event.packageName == recentsPackage || event.packageName in excludedPackages) return

        val keyboardPackages = getKeyboardPackageNames()

        if (event.className !in knownRecentsClasses && event.packageName !in keyboardPackages) {
            val lastEvent = lastEvents.lastOrNull()
            if (event.packageName == lastEvent?.first?.packageName) {
                if (lastEvents.size > 1) {
                    lastEvents.removeAt(lastEvents.lastIndex) // Remove last event if same package
                }
            }
            lastEvents.add(Pair(event, event.eventTime))
        }

        if (lastEvents.size > 3) {
            lastEvents.removeAt(0)
        }
        val packageName = event.packageName?.toString() ?: return

        if (appLockRepository.isAntiUninstallEnabled() && packageName == DEVICE_ADMIN_SETTINGS_PACKAGE) {
            Log.d(TAG, "In settings, in activity: ${event.className}")
            checkForDeviceAdminDeactivation(event)
        }

        // Dont continue if its system or our app or keyboard package
        if (packageName == "com.android.systemui" || packageName.startsWith(APP_PACKAGE_PREFIX) || packageName in keyboardPackages) {
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

            if (secondLastEvent.first.packageName == getCurrentLauncherPackageName(this) && lastEvent.first.packageName == temporarilyUnlockedApp && lastEvent.second - secondLastEvent.second < 5000) {
                Log.d(TAG, "Ignoring rapid events for launcher and keyboard package: $packageName")
                return
            }

            if (firstEvent.first.packageName in lockedApps && firstEvent.first.packageName == lastEvent.first.packageName && lastEvent.second - firstEvent.second < 5000) {
                Log.d(TAG, "Ignoring rapid events for same package: $packageName")
                return
            }
        }

        if (packageName == temporarilyUnlockedApp) {
            return
        }
        Log.d(
            TAG,
            "Clearing unlocked app: $temporarilyUnlockedApp because new event for package: $packageName"
        )
        temporarilyUnlockedApp = ""
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

    private fun checkAndLockApp(packageName: String, currentTime: Long) {
        if (currentBiometricState == BiometricState.AUTH_STARTED) {
            Log.d(TAG, "Biometric authentication in progress, skipping app lock for $packageName")
            return
        }

        val lockedApps = appLockRepository.getLockedApps()
        if (!lockedApps.contains(packageName)) {
            return
        }

        // Check if app is within unlock time period
        val unlockDuration = appLockRepository.getUnlockTimeDuration()
        val unlockTimestamp = appUnlockTimes[packageName] ?: 0

        // If unlock time is set and app was unlocked within the duration period
        if (unlockDuration > 0 && unlockTimestamp > 0) {
            val elapsedMinutes = (currentTime - unlockTimestamp) / (60 * 1000)
            if (elapsedMinutes < unlockDuration) {
                Log.d(
                    TAG,
                    "App $packageName is within unlock time period ($elapsedMinutes/${unlockDuration}min)"
                )
                temporarilyUnlockedApp = packageName
                return
            } else {
                // Unlock time expired, remove from tracking
                appUnlockTimes.remove(packageName)
            }
        }

        Log.d(TAG, "Locked app detected: $packageName")

        // Launch lock screen
        val intent = Intent(this, PasswordOverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("locked_package", packageName)
        }
        startActivity(intent)
    }

    private fun isDeviceLocked(): Boolean {
        val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        return keyguardManager.isKeyguardLocked
    }

    private fun getKeyboardPackageNames(): List<String> {
        val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        return inputMethodManager.inputMethodList.map { it.packageName }
    }

    private fun getCurrentLauncherPackageName(context: Context): String? {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        val resolveInfo =
            context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName
    }

    fun validatePassword(password: String): Boolean {
        // Use repository to validate password
        return appLockRepository.validatePassword(password)
    }

    fun unlockApp(packageName: String) {
        Log.d(TAG, "Unlocking app: $packageName")
        temporarilyUnlockedApp = packageName
        appUnlockTimes[packageName] = System.currentTimeMillis() // Track unlock time
    }

    fun temporarilyUnlockAppWithBiometrics(packageName: String) {
        currentBiometricState = BiometricState.AUTH_SUCCESSFUL
        unlockApp(packageName)
    }

    fun reportBiometricAuthStarted() {
        currentBiometricState = BiometricState.AUTH_STARTED
    }

    fun reportBiometricAuthFinished() {
        currentBiometricState = BiometricState.IDLE
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        if (instance == this) {
            instance = null
        }
        Log.d(TAG, "Accessibility service destroyed")
    }

    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
        Log.d(TAG, "Service rebound")
        isServiceRunning = true
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "Service unbound")
        return true
    }
}
