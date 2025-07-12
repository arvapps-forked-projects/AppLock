package dev.pranav.applock.services

import java.util.concurrent.ConcurrentHashMap

var knownRecentsClasses = setOf(
    "com.android.systemui.recents.RecentsActivity",
    "com.android.quickstep.RecentsActivity",
    "com.android.systemui.recents.RecentsView",
    "com.android.systemui.recents.RecentsPanelView"
)

object AppLockManager {
    var temporarilyUnlockedApp: String = ""
    val appUnlockTimes = ConcurrentHashMap<String, Long>()
    var currentBiometricState = AppLockAccessibilityService.BiometricState.IDLE


    fun unlockApp(packageName: String) {
        temporarilyUnlockedApp = packageName
        appUnlockTimes[packageName] = System.currentTimeMillis()
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

