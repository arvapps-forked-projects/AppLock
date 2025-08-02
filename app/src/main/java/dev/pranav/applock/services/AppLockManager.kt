package dev.pranav.applock.services

import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.Context
import android.content.Context.KEYGUARD_SERVICE
import android.content.Intent
import android.util.Log
import dev.pranav.applock.data.repository.AppLockRepository
import dev.pranav.applock.data.repository.BackendImplementation
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean


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

var knownAccessibilitySettingsClasses = setOf(
    "com.android.settings.accessibility.AccessibilitySettings",
    "com.android.settings.accessibility.AccessibilityMenuActivity",
    "com.android.settings.accessibility.AccessibilityShortcutActivity",
    "com.android.settings.Settings\$AccessibilitySettingsActivity"
)

val excludedApps = setOf(
    "com.android.systemui",
    "com.android.intentresolver",
    "com.google.android.permissioncontroller",
    "android.uid.system:1000"
)

object AppLockManager {
    var temporarilyUnlockedApp: String = ""
    val appUnlockTimes = ConcurrentHashMap<String, Long>()
    var currentBiometricState = AppLockAccessibilityService.BiometricState.IDLE
    val isLockScreenShown = AtomicBoolean(false)

    private val serviceRestartAttempts = ConcurrentHashMap<String, Int>()
    private val lastRestartTime = ConcurrentHashMap<String, Long>()
    private const val MAX_RESTART_ATTEMPTS = 3
    private const val RESTART_COOLDOWN_MS = 30000L // 30 seconds
    private const val SERVICE_RESTART_INTERVAL_MS = 5000L // 5 seconds between attempts


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

    fun startFallbackServices(context: Context, failedService: Class<*>) {
        val serviceName = failedService.simpleName
        Log.d("AppLockManager", "Starting fallback services after $serviceName failed")

        if (!shouldAttemptRestart(serviceName)) {
            Log.w(
                "AppLockManager",
                "Skipping fallback for $serviceName - too many attempts or cooldown active"
            )
            return
        }

        val appLockRepository = AppLockRepository(context)
        val fallbackBackend = appLockRepository.getFallbackBackend()

        when (failedService) {
            AppLockAccessibilityService::class.java -> {
                Log.d("AppLockManager", "Accessibility service failed, trying fallback")
                startServiceByBackend(context, fallbackBackend)
            }

            ShizukuAppLockService::class.java -> {
                Log.d("AppLockManager", "Shizuku service failed, trying fallback")
                if (AppLockAccessibilityService.isServiceRunning) {
                    Log.d("AppLockManager", "Accessibility service is running, no fallback needed")
                    return
                }
                startServiceByBackend(context, BackendImplementation.USAGE_STATS)
            }

            ExperimentalAppLockService::class.java -> {
                Log.d("AppLockManager", "Experimental service failed, trying fallback")
                if (AppLockAccessibilityService.isServiceRunning) {
                    Log.d("AppLockManager", "Accessibility service is running, no fallback needed")
                    return
                }
                startServiceByBackend(context, BackendImplementation.SHIZUKU)
            }
        }

        recordRestartAttempt(serviceName)
    }

    private fun shouldAttemptRestart(serviceName: String): Boolean {
        val currentTime = System.currentTimeMillis()
        val attempts = serviceRestartAttempts[serviceName] ?: 0
        val lastRestart = lastRestartTime[serviceName] ?: 0

        if (currentTime - lastRestart < SERVICE_RESTART_INTERVAL_MS) {
            Log.d("AppLockManager", "Service $serviceName restart too recent, skipping")
            return false
        }

        if (attempts >= MAX_RESTART_ATTEMPTS) {
            if (currentTime - lastRestart > RESTART_COOLDOWN_MS) {
                Log.d("AppLockManager", "Cooldown expired for $serviceName, resetting attempts")
                serviceRestartAttempts[serviceName] = 0
                return true
            }
            Log.d("AppLockManager", "Max restart attempts reached for $serviceName, in cooldown")
            return false
        }

        return true
    }

    private fun recordRestartAttempt(serviceName: String) {
        val currentTime = System.currentTimeMillis()
        val currentAttempts = serviceRestartAttempts[serviceName] ?: 0
        serviceRestartAttempts[serviceName] = currentAttempts + 1
        lastRestartTime[serviceName] = currentTime

        Log.d("AppLockManager", "Recorded restart attempt ${currentAttempts + 1} for $serviceName")
    }

    private fun startServiceByBackend(context: Context, backend: BackendImplementation) {
        try {
            // Stop all other services first to ensure only one runs at a time
            stopAllServices(context)

            when (backend) {
                BackendImplementation.SHIZUKU -> {
                    Log.d("AppLockManager", "Starting Shizuku service as fallback")
                    context.startService(Intent(context, ShizukuAppLockService::class.java))
                }

                BackendImplementation.USAGE_STATS -> {
                    Log.d("AppLockManager", "Starting Experimental service as fallback")
                    context.startService(Intent(context, ExperimentalAppLockService::class.java))
                }

                BackendImplementation.ACCESSIBILITY -> {
                    Log.d(
                        "AppLockManager",
                        "Accessibility service runs automatically when enabled, cannot start programmatically"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("AppLockManager", "Failed to start fallback service for backend: $backend", e)
        }
    }

    private fun stopAllServices(context: Context) {
        Log.d("AppLockManager", "Stopping all app lock services before starting new one")

        try {
            context.stopService(Intent(context, ExperimentalAppLockService::class.java))
            context.stopService(Intent(context, ShizukuAppLockService::class.java))
        } catch (e: Exception) {
            Log.e("AppLockManager", "Error stopping services", e)
        }
    }

    fun resetRestartAttempts(serviceName: String) {
        serviceRestartAttempts.remove(serviceName)
        lastRestartTime.remove(serviceName)
        Log.d("AppLockManager", "Reset restart attempts for $serviceName")
    }


    fun Context.isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.Companion.MAX_VALUE)) {
            if (serviceClass.getName() == service.service.className) {
                return true
            }
        }
        return false
    }
}

fun Context.isDeviceLocked(): Boolean {
    val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
    return keyguardManager.isKeyguardLocked
}
