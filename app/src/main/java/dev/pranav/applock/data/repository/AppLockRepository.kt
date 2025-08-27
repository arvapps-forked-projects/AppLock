package dev.pranav.applock.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import dev.pranav.applock.services.AppLockAccessibilityService
import dev.pranav.applock.services.ExperimentalAppLockService
import dev.pranav.applock.services.ShizukuAppLockService

class AppLockRepository(private val context: Context) {

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
        } catch (_: IllegalArgumentException) {
            BackendImplementation.ACCESSIBILITY
        }
    }

    fun getFallbackBackend(): BackendImplementation {
        val fallback =
            settingsPrefs.getString(KEY_FALLBACK_BACKEND, BackendImplementation.ACCESSIBILITY.name)
        return try {
            BackendImplementation.valueOf(fallback ?: BackendImplementation.ACCESSIBILITY.name)
        } catch (_: IllegalArgumentException) {
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

    fun isShowCommunityLink(): Boolean {
        return !settingsPrefs.getBoolean(KEY_COMMUNITY_LINK_SHOWN, false)
    }

    fun setCommunityLinkShown(shown: Boolean) {
        settingsPrefs.edit { putBoolean(KEY_COMMUNITY_LINK_SHOWN, shown) }
    }

    fun isShowDonateLink(): Boolean {
        val currentVersionCode =
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode
        val savedVersionCode = settingsPrefs.getInt(LAST_VERSION_CODE, -1)

        if (currentVersionCode > savedVersionCode) {
            settingsPrefs.edit {
                putInt(LAST_VERSION_CODE, currentVersionCode)
            }
            return true
        }
        return false
    }

    fun isProtectEnabled(): Boolean {
        return settingsPrefs.getBoolean(KEY_APPLOCK_ENABLED, true)
    }

    fun setProtectEnabled(enabled: Boolean) {
        settingsPrefs.edit { putBoolean(KEY_APPLOCK_ENABLED, enabled) }
    }

    fun setShizukuExperimentalEnabled(enabled: Boolean) {
        settingsPrefs.edit { putBoolean(KEY_SHIZUKU_EXPERIMENTAL, enabled) }
    }

    fun isShizukuExperimentalEnabled(): Boolean {
        return settingsPrefs.getBoolean(KEY_SHIZUKU_EXPERIMENTAL, true)
    }

    fun isAutoUnlockEnabled(): Boolean {
        return settingsPrefs.getBoolean(KEY_AUTO_UNLOCK, false)
    }

    fun setAutoUnlockEnabled(enabled: Boolean) {
        settingsPrefs.edit { putBoolean(KEY_AUTO_UNLOCK, enabled) }
    }

    fun setUnlockBehavior(behavior: Int) {
        settingsPrefs.edit { putInt(KEY_UNLOCK_BEHAVIOR, behavior) }
    }

    fun getUnlockBehavior(): Int {
        return settingsPrefs.getInt(KEY_UNLOCK_BEHAVIOR, 1)
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
        private const val KEY_COMMUNITY_LINK_SHOWN = "community_link_shown"
        private const val LAST_VERSION_CODE = "last_version_code"
        private const val KEY_SHIZUKU_EXPERIMENTAL = "shizuku_experimental"
        private const val KEY_APPLOCK_ENABLED = "applock_enabled"
        private const val KEY_AUTO_UNLOCK = "auto_unlock"
        private const val KEY_UNLOCK_BEHAVIOR = "unlock_behavior"


        fun shouldStartService(rep: AppLockRepository, serviceClass: Class<*>): Boolean {
            val activeBackend = rep.getActiveBackend()
            val chosenBackend = rep.getBackendImplementation()

            Log.d(
                "AppLockRepository",
                "activeBackend: ${activeBackend?.name}, requested service: ${serviceClass.simpleName}, chosen backend: ${chosenBackend.name}"
            )

            val serviceBackend = when (serviceClass) {
                AppLockAccessibilityService::class.java -> BackendImplementation.ACCESSIBILITY
                ExperimentalAppLockService::class.java -> BackendImplementation.USAGE_STATS
                ShizukuAppLockService::class.java -> BackendImplementation.SHIZUKU
                else -> {
                    Log.d("AppLockRepository", "Unknown service class: ${serviceClass.simpleName}")
                    return false
                }
            }

            // If this service matches the chosen backend, it should start
            if (serviceBackend == chosenBackend) {
                Log.d(
                    "AppLockRepository",
                    "Service ${serviceClass.simpleName} matches chosen backend, should start"
                )
                return true
            }

            // If this service matches the active backend (fallback scenario), it should start
            if (activeBackend != null && serviceBackend == activeBackend) {
                Log.d(
                    "AppLockRepository",
                    "Service ${serviceClass.simpleName} matches active backend, should start"
                )
                return true
            }

            Log.d(
                "AppLockRepository",
                "Service ${serviceClass.simpleName} should not start - not matching chosen or active backend"
            )
            return false
        }
    }
}

enum class BackendImplementation {
    ACCESSIBILITY,
    USAGE_STATS,
    SHIZUKU
}
