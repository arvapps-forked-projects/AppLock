package dev.pranav.applock.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Repository for managing application preferences and settings.
 * Handles all SharedPreferences operations with proper separation of concerns.
 */
class PreferencesRepository(context: Context) {

    private val appLockPrefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME_APP_LOCK, Context.MODE_PRIVATE)

    private val settingsPrefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME_SETTINGS, Context.MODE_PRIVATE)

    // Authentication Settings
    fun setPassword(password: String) {
        appLockPrefs.edit { putString(KEY_PASSWORD, password) }
    }

    fun getPassword(): String? {
        return appLockPrefs.getString(KEY_PASSWORD, null)
    }

    fun validatePassword(inputPassword: String): Boolean {
        val storedPassword = getPassword()
        return storedPassword != null && inputPassword == storedPassword
    }

    // Biometric Settings
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
            DEFAULT_PROMPT_BIOMETRIC
        )
    }

    // UI Settings
    fun setUseMaxBrightness(enabled: Boolean) {
        settingsPrefs.edit { putBoolean(KEY_USE_MAX_BRIGHTNESS, enabled) }
    }

    fun shouldUseMaxBrightness(): Boolean {
        return settingsPrefs.getBoolean(KEY_USE_MAX_BRIGHTNESS, false)
    }

    fun setDisableHaptics(enabled: Boolean) {
        settingsPrefs.edit { putBoolean(KEY_DISABLE_HAPTICS, enabled) }
    }

    fun shouldDisableHaptics(): Boolean {
        return settingsPrefs.getBoolean(KEY_DISABLE_HAPTICS, false)
    }

    // Security Settings
    fun setAntiUninstallEnabled(enabled: Boolean) {
        settingsPrefs.edit { putBoolean(KEY_ANTI_UNINSTALL, enabled) }
    }

    fun isAntiUninstallEnabled(): Boolean {
        return settingsPrefs.getBoolean(KEY_ANTI_UNINSTALL, false)
    }

    fun setProtectEnabled(enabled: Boolean) {
        settingsPrefs.edit { putBoolean(KEY_APPLOCK_ENABLED, enabled) }
    }

    fun isProtectEnabled(): Boolean {
        return settingsPrefs.getBoolean(KEY_APPLOCK_ENABLED, DEFAULT_PROTECT_ENABLED)
    }

    // Unlock Settings
    fun setUnlockTimeDuration(minutes: Int) {
        settingsPrefs.edit { putInt(KEY_UNLOCK_TIME_DURATION, minutes) }
    }

    fun getUnlockTimeDuration(): Int {
        return settingsPrefs.getInt(KEY_UNLOCK_TIME_DURATION, DEFAULT_UNLOCK_DURATION)
    }

    fun setAutoUnlockEnabled(enabled: Boolean) {
        settingsPrefs.edit { putBoolean(KEY_AUTO_UNLOCK, enabled) }
    }

    fun isAutoUnlockEnabled(): Boolean {
        return settingsPrefs.getBoolean(KEY_AUTO_UNLOCK, false)
    }

    fun setUnlockBehavior(behavior: Int) {
        settingsPrefs.edit { putInt(KEY_UNLOCK_BEHAVIOR, behavior) }
    }

    fun getUnlockBehavior(): Int {
        return settingsPrefs.getInt(KEY_UNLOCK_BEHAVIOR, DEFAULT_UNLOCK_BEHAVIOR)
    }

    // Backend Settings
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
        val fallback = settingsPrefs.getString(
            KEY_FALLBACK_BACKEND,
            BackendImplementation.ACCESSIBILITY.name
        )
        return try {
            BackendImplementation.valueOf(fallback ?: BackendImplementation.ACCESSIBILITY.name)
        } catch (_: IllegalArgumentException) {
            BackendImplementation.ACCESSIBILITY
        }
    }

    // App State
    fun isShowCommunityLink(): Boolean {
        return !settingsPrefs.getBoolean(KEY_COMMUNITY_LINK_SHOWN, false)
    }

    fun setCommunityLinkShown(shown: Boolean) {
        settingsPrefs.edit { putBoolean(KEY_COMMUNITY_LINK_SHOWN, shown) }
    }

    fun isShowDonateLink(context: Context): Boolean {
        val currentVersionCode = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode
        } catch (e: Exception) {
            return false
        }

        val savedVersionCode = settingsPrefs.getInt(LAST_VERSION_CODE, -1)

        if (currentVersionCode > savedVersionCode) {
            settingsPrefs.edit { putInt(LAST_VERSION_CODE, currentVersionCode) }
            return true
        }
        return false
    }

    companion object {
        private const val PREFS_NAME_APP_LOCK = "app_lock_prefs"
        private const val PREFS_NAME_SETTINGS = "app_lock_settings"

        // Keys
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
        private const val KEY_APPLOCK_ENABLED = "applock_enabled"
        private const val KEY_AUTO_UNLOCK = "auto_unlock"
        private const val KEY_UNLOCK_BEHAVIOR = "unlock_behavior"

        // Default values
        private const val DEFAULT_PROMPT_BIOMETRIC = true
        private const val DEFAULT_PROTECT_ENABLED = true
        private const val DEFAULT_UNLOCK_DURATION = 0
        private const val DEFAULT_UNLOCK_BEHAVIOR = 1
    }
}
