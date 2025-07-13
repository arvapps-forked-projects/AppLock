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
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider

class AppLockRepository(context: Context) {

    private val appLockPrefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME_APP_LOCK, Context.MODE_PRIVATE)

    private val settingsPrefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME_SETTINGS, Context.MODE_PRIVATE)

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

    // Legacy methods for backward compatibility - deprecated, use BackendImplementation instead
    @Deprecated(
        "Use BackendImplementation enum instead",
        ReplaceWith("getBackendImplementation() == BackendImplementation.USAGE_STATS")
    )
    fun setExperimentalImplEnabled(enabled: Boolean) {
        if (enabled) {
            setBackendImplementation(BackendImplementation.USAGE_STATS)
        } else if (getBackendImplementation() == BackendImplementation.USAGE_STATS) {
            setBackendImplementation(BackendImplementation.ACCESSIBILITY)
        }
    }

    @Deprecated(
        "Use BackendImplementation enum instead",
        ReplaceWith("getBackendImplementation() == BackendImplementation.USAGE_STATS")
    )
    fun isExperimentalImplEnabled(): Boolean {
        return getBackendImplementation() == BackendImplementation.USAGE_STATS
    }

    @Deprecated(
        "Use BackendImplementation enum instead",
        ReplaceWith("getBackendImplementation() == BackendImplementation.SHIZUKU")
    )
    fun setShizukuImplEnabled(enabled: Boolean) {
        if (enabled) {
            setBackendImplementation(BackendImplementation.SHIZUKU)
        } else if (getBackendImplementation() == BackendImplementation.SHIZUKU) {
            setBackendImplementation(BackendImplementation.ACCESSIBILITY)
        }
    }

    @Deprecated(
        "Use BackendImplementation enum instead",
        ReplaceWith("getBackendImplementation() == BackendImplementation.SHIZUKU")
    )
    fun isShizukuImplEnabled(): Boolean {
        return getBackendImplementation() == BackendImplementation.SHIZUKU
    }

    // Backend implementation management
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
        settingsPrefs.edit { putString(KEY_ACTIVE_BACKEND, backend.name) }
    }

    fun getActiveBackend(): BackendImplementation {
        val backend = settingsPrefs.getString(
            KEY_ACTIVE_BACKEND,
            getBackendImplementation().name // Default to primary backend
        )
        return try {
            BackendImplementation.valueOf(backend ?: getBackendImplementation().name)
        } catch (e: IllegalArgumentException) {
            getBackendImplementation()
        }
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

    // Get the best available backend (primary if available, fallback otherwise)
    fun getBestAvailableBackend(context: Context): BackendImplementation {
        val primary = getBackendImplementation()
        val fallback = getFallbackBackend()

        return when {
            isBackendAvailable(primary, context) -> primary
            primary != fallback && isBackendAvailable(fallback, context) -> fallback
            else -> primary // Return primary even if not available (will trigger permission request)
        }
    }

    fun validateAndSwitchBackend(context: Context): BackendImplementation {
        val currentActive = getActiveBackend()
        val primary = getBackendImplementation()
        val fallback = getFallbackBackend()

        if (isBackendAvailable(currentActive, context)) {
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
        private const val KEY_USE_MAX_BRIGHTNESS = "use_max_brightness"
        private const val KEY_ANTI_UNINSTALL = "anti_uninstall"
        private const val KEY_UNLOCK_TIME_DURATION = "unlock_time_duration"
        private const val KEY_ACCESSIBILITY_PLUS_USAGE_STATS = "accessibility_usage_stats"
        private const val KEY_SHIZUKU_IMPL = "shizuku_impl"
        private const val KEY_BACKEND_IMPLEMENTATION = "backend_implementation"
        private const val KEY_FALLBACK_BACKEND = "fallback_backend"
        private const val KEY_ACTIVE_BACKEND = "active_backend"
    }
}

enum class BackendImplementation {
    ACCESSIBILITY,
    USAGE_STATS,
    SHIZUKU
}
