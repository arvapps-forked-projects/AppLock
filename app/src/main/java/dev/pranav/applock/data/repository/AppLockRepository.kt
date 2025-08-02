package dev.pranav.applock.data.repository

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.PermissionChecker
import androidx.core.content.PermissionChecker.checkSelfPermission
import androidx.core.content.edit
import dev.pranav.applock.core.utils.hasUsagePermission
import dev.pranav.applock.core.utils.isAccessibilityServiceEnabled
import dev.pranav.applock.services.AppLockAccessibilityService
import dev.pranav.applock.services.ExperimentalAppLockService
import dev.pranav.applock.services.ShizukuAppLockService
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider

class AppLockRepository(context: Context) {

    private val appLockPrefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME_APP_LOCK, Context.MODE_PRIVATE)

    private val settingsPrefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME_SETTINGS, Context.MODE_PRIVATE)

    private var activeBackend: BackendImplementation? = null

    // Locked Apps
    fun getLockedApps(): Set<String> {
        return appLockPrefs.getStringSet(KEY_LOCKED_APPS, emptySet()) ?: emptySet()
    }

    fun addLockedApp(packageName: String) {
        val currentApps = getLockedApps().toMutableSet()
        currentApps.add(packageName)
        appLockPrefs.edit { putStringSet(KEY_LOCKED_APPS, currentApps) }
    }

    fun removeLockedApp(packageName: String) {
        val currentApps = getLockedApps().toMutableSet()
        currentApps.remove(packageName)
        appLockPrefs.edit { putStringSet(KEY_LOCKED_APPS, currentApps) }
    }

    // Password
    fun getPassword(): String? {
        return appLockPrefs.getString(KEY_PASSWORD, null)
    }

    fun setPassword(password: String) {
        appLockPrefs.edit { putString(KEY_PASSWORD, password) }
    }

    // Password validation
    fun validatePassword(inputPassword: String): Boolean {
        val storedPassword = getPassword()
        return storedPassword != null && inputPassword == storedPassword
    }

    // Unlock time duration
    fun setUnlockTimeDuration(minutes: Int) {
        settingsPrefs.edit { putInt(KEY_UNLOCK_TIME_DURATION, minutes) }
    }

    fun getUnlockTimeDuration(): Int {
        return settingsPrefs.getInt(KEY_UNLOCK_TIME_DURATION, 0)
    }

    // Settings
    fun setBiometricAuthEnabled(enabled: Boolean) {
        settingsPrefs.edit { putBoolean(KEY_BIOMETRIC_AUTH_ENABLED, enabled) }
    }

    fun isBiometricAuthEnabled(): Boolean {
        return settingsPrefs.getBoolean(KEY_BIOMETRIC_AUTH_ENABLED, false)
    }

    fun setPromptForBiometricAuth(enabled: Boolean) {
        settingsPrefs.edit { putBoolean(KEY_PROMPT_FOR_BIOMETRIC_AUTH, enabled) }
    }

    fun shouldPromptForBiometricAuth(): Boolean {
        return isBiometricAuthEnabled() && settingsPrefs.getBoolean(
            KEY_PROMPT_FOR_BIOMETRIC_AUTH,
            true
        )
    }

    fun setUseMaxBrightness(enabled: Boolean) {
        settingsPrefs.edit { putBoolean(KEY_USE_MAX_BRIGHTNESS, enabled) }
    }

    fun shouldUseMaxBrightness(): Boolean {
        return settingsPrefs.getBoolean(KEY_USE_MAX_BRIGHTNESS, false)
    }

    fun setAntiUninstallEnabled(enabled: Boolean) {
        settingsPrefs.edit { putBoolean(KEY_ANTI_UNINSTALL, enabled) }
    }

    fun isAntiUninstallEnabled(): Boolean {
        return settingsPrefs.getBoolean(KEY_ANTI_UNINSTALL, false)
    }

    fun setDisableHaptics(enabled: Boolean) {
        settingsPrefs.edit { putBoolean(KEY_DISABLE_HAPTICS, enabled) }
    }

    fun shouldDisableHaptics(): Boolean {
        return settingsPrefs.getBoolean(KEY_DISABLE_HAPTICS, false)
    }

    fun setBackendImplementation(backend: BackendImplementation) {
        settingsPrefs.edit { putString(KEY_BACKEND_IMPLEMENTATION, backend.name) }
    }

    fun getBackendImplementation(): BackendImplementation {
        val backend = settingsPrefs.getString(
            KEY_BACKEND_IMPLEMENTATION,
            BackendImplementation.ACCESSIBILITY.name
        )
        return try {
            BackendImplementation.valueOf(backend ?: BackendImplementation.ACCESSIBILITY.name)
        } catch (e: IllegalArgumentException) {
            BackendImplementation.ACCESSIBILITY
        }
    }

    fun setFallbackBackend(fallback: BackendImplementation) {
        settingsPrefs.edit { putString(KEY_FALLBACK_BACKEND, fallback.name) }
    }

    fun getFallbackBackend(): BackendImplementation {
        val fallback =
            settingsPrefs.getString(KEY_FALLBACK_BACKEND, BackendImplementation.ACCESSIBILITY.name)
        return try {
            BackendImplementation.valueOf(fallback ?: BackendImplementation.ACCESSIBILITY.name)
        } catch (e: IllegalArgumentException) {
            BackendImplementation.ACCESSIBILITY
        }
    }

    // Active backend tracking (runtime switching)
    fun setActiveBackend(backend: BackendImplementation) {
        activeBackend = backend
    }

    fun getActiveBackend(): BackendImplementation? {
        return activeBackend
    }

    // Backend status checking
    fun isBackendAvailable(backend: BackendImplementation, context: Context): Boolean {
        return when (backend) {
            BackendImplementation.ACCESSIBILITY -> context.isAccessibilityServiceEnabled()
            BackendImplementation.USAGE_STATS -> context.hasUsagePermission()
            BackendImplementation.SHIZUKU -> {
                try {
                    if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
                        checkSelfPermission(
                            context,
                            ShizukuProvider.PERMISSION
                        ) == PermissionChecker.PERMISSION_GRANTED
                    } else {
                        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                    }
                } catch (e: Exception) {
                    false
                }
            }
        }
    }

    fun setShizukuExperimentalEnabled(enabled: Boolean) {
        settingsPrefs.edit { putBoolean(KEY_SHIZUKU_EXPERIMENTAL, enabled) }
    }

    fun isShizukuExperimentalEnabled(): Boolean {
        return settingsPrefs.getBoolean(KEY_SHIZUKU_EXPERIMENTAL, true)
    }

    fun validateAndSwitchBackend(context: Context): BackendImplementation {
        val currentActive = getActiveBackend()
        val primary = getBackendImplementation()
        val fallback = getFallbackBackend()

        if (isBackendAvailable(currentActive!!, context)) {
            Log.d("AppLockRepository", "Current active backend is available: $currentActive")
            return currentActive // Still working, keep using it
        }

        Log.w("AppLockRepository", "Current active backend is not available: $currentActive")

        // Current active backend failed, find next best option
        val newBackend = when {
            // Try primary first (if different from current)
            currentActive != primary && isBackendAvailable(primary, context) -> primary
            // Try fallback if primary also fails
            primary != fallback && isBackendAvailable(fallback, context) -> fallback
            // If both fail, return primary to trigger permission request
            else -> primary
        }

        // Switch to new backend if it's different
        if (newBackend != currentActive) {
            setActiveBackend(newBackend)
            // Start monitoring service to handle future backend switches
            startBackendMonitoring(context)
        }

        return newBackend
    }

    // Start the backend monitoring service
    private fun startBackendMonitoring(context: Context) {
        try {
            val intent = Intent(
                context,
                Class.forName("dev.pranav.applock.core.monitoring.BackendMonitoringService")
            )
            context.startService(intent)
        } catch (e: Exception) {
            // Service class not found or other error, ignore
        }
    }

    companion object {
        private const val PREFS_NAME_APP_LOCK = "app_lock_prefs"
        private const val PREFS_NAME_SETTINGS = "app_lock_settings"

        private const val KEY_LOCKED_APPS = "locked_apps"
        private const val KEY_PASSWORD = "password"
        private const val KEY_BIOMETRIC_AUTH_ENABLED = "use_biometric_auth"
        private const val KEY_PROMPT_FOR_BIOMETRIC_AUTH = "prompt_for_biometric_auth"
        private const val KEY_DISABLE_HAPTICS = "disable_haptics"
        private const val KEY_USE_MAX_BRIGHTNESS = "use_max_brightness"
        private const val KEY_ANTI_UNINSTALL = "anti_uninstall"
        private const val KEY_UNLOCK_TIME_DURATION = "unlock_time_duration"
        private const val KEY_BACKEND_IMPLEMENTATION = "backend_implementation"
        private const val KEY_FALLBACK_BACKEND = "fallback_backend"
        private const val KEY_ACTIVE_BACKEND = "active_backend"
        private const val KEY_SHIZUKU_EXPERIMENTAL = "shizuku_experimental"


        fun shouldStartService(rep: AppLockRepository, serviceClass: Class<*>): Boolean {
            rep.getActiveBackend().let { activeBackend ->
                Log.d(
                    "AppLockRepository",
                    "activeBackend: ${activeBackend?.name}, requested service: ${serviceClass.simpleName}, chosen backend: ${rep.getBackendImplementation().name}, fallback: ${rep.getFallbackBackend().name}"
                )

                val backendClass = when (serviceClass) {
                    AppLockAccessibilityService::class.java -> BackendImplementation.ACCESSIBILITY
                    ExperimentalAppLockService::class.java -> BackendImplementation.USAGE_STATS
                    ShizukuAppLockService::class.java -> BackendImplementation.SHIZUKU
                    else -> return false // Unknown service class, do not start
                }

                val chosenBackend = rep.getBackendImplementation()
                val fallbackBackend = rep.getFallbackBackend()

                // If this service matches the chosen backend, it should start
                if (backendClass == chosenBackend) {
                    Log.d(
                        "AppLockRepository",
                        "Service ${serviceClass.simpleName} matches chosen backend, should start"
                    )
                    return true
                }

                // If the chosen backend is not available and this service matches the fallback, it should start
                if (backendClass == fallbackBackend && activeBackend != chosenBackend) {
                    Log.d(
                        "AppLockRepository",
                        "Service ${serviceClass.simpleName} matches fallback backend and chosen backend is not active, should start"
                    )
                    return true
                }

                // Otherwise, this service should not start
                Log.d(
                    "AppLockRepository",
                    "Service ${serviceClass.simpleName} should not start - not matching chosen or fallback backend"
                )
                return false
            }
        }
    }
}

enum class BackendImplementation {
    ACCESSIBILITY,
    USAGE_STATS,
    SHIZUKU
}
