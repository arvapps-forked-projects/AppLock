package dev.pranav.applock.services

import android.app.KeyguardManager
import android.content.Context
import android.content.Context.KEYGUARD_SERVICE
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

var knownRecentsClasses = setOf(
    "com.android.systemui.recents.RecentsActivity",
    "com.android.quickstep.RecentsActivity",
    "com.android.systemui.recents.RecentsView",
    "com.android.systemui.recents.RecentsPanelView"
)

var knownAdminConfigClasses = setOf(
    "com.android.settings.deviceadmin.DeviceAdminAdd",
    "com.android.settings.applications.specialaccess.deviceadmin.DeviceAdminAdd",
    "com.android.settings.deviceadmin.DeviceAdminSettings",
    "com.android.settings.deviceadmin.DeviceAdminAdd"
)

object AppLockManager {
    var temporarilyUnlockedApp: String = ""
    val appUnlockTimes = ConcurrentHashMap<String, Long>()
    var currentBiometricState = AppLockAccessibilityService.BiometricState.IDLE


    fun unlockApp(packageName: String) {
        // get where this function is called from and log it
        Log.d(
            "AppLockManager",
            "Unlocking app: $packageName from ${Thread.currentThread().stackTrace[3].className}.${Thread.currentThread().stackTrace[3].methodName}"
        )
        temporarilyUnlockedApp = packageName
        appUnlockTimes[packageName] = System.currentTimeMillis()
        Log.d(
            "AppLockManager",
            "App $packageName temporarily unlocked at ${appUnlockTimes[packageName]}"
        )
    }

    fun temporarilyUnlockAppWithBiometrics(packageName: String) {
        unlockApp(packageName)
        reportBiometricAuthFinished()
    }

    fun reportBiometricAuthStarted() {
        currentBiometricState = AppLockAccessibilityService.BiometricState.AUTH_STARTED
    }

    fun reportBiometricAuthFinished() {
        currentBiometricState = AppLockAccessibilityService.BiometricState.IDLE
    }

    fun isAppTemporarilyUnlocked(packageName: String): Boolean {
        return temporarilyUnlockedApp == packageName
    }

    fun clearTemporarilyUnlockedApp() {
        temporarilyUnlockedApp = ""
    }
}

fun Context.isDeviceLocked(): Boolean {
    val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
    return keyguardManager.isKeyguardLocked
}
