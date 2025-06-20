package dev.pranav.applock.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit

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
        Log.d(
            "AppLockRepository",
            "Validating password: $inputPassword against stored: $storedPassword"
        )
        return storedPassword != null && inputPassword == storedPassword
    }

    // Unlock time duration
    fun setUnlockTimeDuration(minutes: Int) {
        settingsPrefs.edit { putInt(KEY_UNLOCK_TIME_DURATION, minutes) }
    }

    fun getUnlockTimeDuration(): Int {
        return settingsPrefs.getInt(KEY_UNLOCK_TIME_DURATION, 0) // Default to 0 (immediate lock)
    }

    // Settings
    fun setBiometricAuthEnabled(enabled: Boolean) {
        settingsPrefs.edit { putBoolean(KEY_BIOMETRIC_AUTH_ENABLED, enabled) }
    }

    fun isBiometricAuthEnabled(): Boolean {
        return settingsPrefs.getBoolean(KEY_BIOMETRIC_AUTH_ENABLED, false)
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

    companion object {
        private const val PREFS_NAME_APP_LOCK = "app_lock_prefs"
        private const val PREFS_NAME_SETTINGS = "app_lock_settings"

        private const val KEY_LOCKED_APPS = "locked_apps"
        private const val KEY_PASSWORD = "password"
        private const val KEY_BIOMETRIC_AUTH_ENABLED = "use_biometric_auth"
        private const val KEY_USE_MAX_BRIGHTNESS = "use_max_brightness"
        private const val KEY_ANTI_UNINSTALL = "anti_uninstall"
        private const val KEY_UNLOCK_TIME_DURATION = "unlock_time_duration"
    }
}
