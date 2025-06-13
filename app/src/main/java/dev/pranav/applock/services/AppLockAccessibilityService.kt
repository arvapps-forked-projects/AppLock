package dev.pranav.applock.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import dev.pranav.applock.core.broadcast.DeviceAdmin
import dev.pranav.applock.data.repository.AppLockRepository
import dev.pranav.applock.features.lockscreen.ui.PasswordOverlayActivity

@SuppressLint("AccessibilityPolicy")
class AppLockAccessibilityService : AccessibilityService() {
    private lateinit var appLockRepository: AppLockRepository
    private var lastForegroundPackage = ""
    private var temporarilyUnlockedApp: String = ""
    private var currentBiometricState = BiometricState.IDLE
    private val lastEvents = mutableListOf<Pair<AccessibilityEvent, Long>>()

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
        Log.d(TAG, "Accessibility service created")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = serviceInfo
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.notificationTimeout = 100
        info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        info.packageNames = null // Monitor all packages
        serviceInfo = info
        Log.d(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            && event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            return
        }

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            lastEvents.add(Pair(event, System.currentTimeMillis()))
        }

        if (lastEvents.size > 3) {
            lastEvents.removeAt(0)
        }
        val packageName = event.packageName?.toString() ?: return

        if (appLockRepository.isAntiUninstallEnabled() && packageName == DEVICE_ADMIN_SETTINGS_PACKAGE) {
            Log.d(TAG, "In settings, in activity: ${event.className}")
            checkForDeviceAdminDeactivation()
        }

        // Skip if it's the same package, our own app, or system UI
        if (packageName == "com.android.systemui" ||
            packageName.startsWith(APP_PACKAGE_PREFIX)
        ) {
            return
        }

        // Apply the rapid events filter to all apps to prevent accidental locks when opening recents
        // This is a "hack" to prevent locking apps when user opens recents because a bug in Android causes
        // the app to be considered foreground for a brief moment
        if (lastEvents.size >= 2) {
            val lastEvent = lastEvents.last()
            val secondLastEvent = lastEvents[lastEvents.size - 2]
            if (lastEvent.first.packageName != secondLastEvent.first.packageName && secondLastEvent.first.packageName == temporarilyUnlockedApp &&
                lastEvent.second - secondLastEvent.second < 3000
            ) {
                Log.d(TAG, "Ignoring rapid events for package: $packageName")
                return
            }
        }

        Log.d(
            TAG,
            lastEvents.joinToString("\n") { "${it.first.packageName} type ${it.first.eventType} at ${it.second}" })

        if (packageName == temporarilyUnlockedApp) {
            return
        }
        temporarilyUnlockedApp = ""
        lastForegroundPackage = packageName
        checkAndLockApp(packageName)
    }

    private fun checkForDeviceAdminDeactivation() {
        val rootNode = rootInActiveWindow ?: return

        try {
            val isDeviceAdminPage = findNodeWithTextContaining(rootNode, "Device admin") != null

            val isOurAppVisible = findNodeWithTextContaining(rootNode, "App Lock") != null ||
                    findNodeWithTextContaining(rootNode, "AppLock") != null

            if (!isDeviceAdminPage || !isOurAppVisible) {
                return
            }

            val dpm =
                getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val component = ComponentName(this, DeviceAdmin::class.java)
            if (dpm.isAdminActive(component)) {
                // go to home screen with accessibility service
                Log.d(TAG, "Device admin is active, navigating to home screen")
                performGlobalAction(GLOBAL_ACTION_HOME)

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
        node: AccessibilityNodeInfo,
        text: String
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

    private fun checkAndLockApp(packageName: String) {
        if (isDeviceLocked()) {
            return
        }

        if (currentBiometricState == BiometricState.AUTH_STARTED) {
            return
        }

        val lockedApps = appLockRepository.getLockedApps()
        if (lockedApps.contains(packageName)) {
            Log.d(TAG, "Locked app detected: $packageName")

            // Launch lock screen
            val intent = Intent(this, PasswordOverlayActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("locked_package", packageName)
            }
            startActivity(intent)
        }
    }

    private fun isDeviceLocked(): Boolean {
        val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        return keyguardManager.isKeyguardLocked
    }

    fun validatePassword(password: String): Boolean {
        // Use repository to validate password
        return appLockRepository.validatePassword(password)
    }

    fun unlockApp(packageName: String) {
        Log.d(TAG, "Unlocking app: $packageName")
        temporarilyUnlockedApp = packageName
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
}
