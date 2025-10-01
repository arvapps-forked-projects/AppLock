package dev.pranav.applock.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Repository for managing locked applications and trigger exclusions.
 * Handles all app-related locking functionality.
 */
class LockedAppsRepository(context: Context) {

    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Locked Apps Management
    fun getLockedApps(): Set<String> {
        return preferences.getStringSet(KEY_LOCKED_APPS, emptySet()) ?: emptySet()
    }

    fun addLockedApp(packageName: String) {
        if (packageName.isBlank()) return

        val currentApps = getLockedApps().toMutableSet()
        if (currentApps.add(packageName)) {
            preferences.edit { putStringSet(KEY_LOCKED_APPS, currentApps) }
        }
    }

    fun removeLockedApp(packageName: String) {
        val currentApps = getLockedApps().toMutableSet()
        if (currentApps.remove(packageName)) {
            preferences.edit { putStringSet(KEY_LOCKED_APPS, currentApps) }
        }
    }

    fun isAppLocked(packageName: String): Boolean {
        return getLockedApps().contains(packageName)
    }

    fun clearAllLockedApps() {
        preferences.edit { putStringSet(KEY_LOCKED_APPS, emptySet()) }
    }

    // Trigger Exclusions Management
    fun getTriggerExcludedApps(): Set<String> {
        return preferences.getStringSet(KEY_TRIGGER_EXCLUDED_APPS, emptySet()) ?: emptySet()
    }

    fun addTriggerExcludedApp(packageName: String) {
        if (packageName.isBlank()) return

        val currentApps = getTriggerExcludedApps().toMutableSet()
        if (currentApps.add(packageName)) {
            preferences.edit { putStringSet(KEY_TRIGGER_EXCLUDED_APPS, currentApps) }
        }
    }

    fun removeTriggerExcludedApp(packageName: String) {
        val currentApps = getTriggerExcludedApps().toMutableSet()
        if (currentApps.remove(packageName)) {
            preferences.edit { putStringSet(KEY_TRIGGER_EXCLUDED_APPS, currentApps) }
        }
    }

    fun isAppTriggerExcluded(packageName: String): Boolean {
        return getTriggerExcludedApps().contains(packageName)
    }

    fun clearAllTriggerExclusions() {
        preferences.edit { putStringSet(KEY_TRIGGER_EXCLUDED_APPS, emptySet()) }
    }

    // Bulk operations
    fun addMultipleLockedApps(packageNames: Set<String>) {
        val validPackageNames = packageNames.filter { it.isNotBlank() }.toSet()
        if (validPackageNames.isEmpty()) return

        val currentApps = getLockedApps().toMutableSet()
        if (currentApps.addAll(validPackageNames)) {
            preferences.edit { putStringSet(KEY_LOCKED_APPS, currentApps) }
        }
    }

    fun removeMultipleLockedApps(packageNames: Set<String>) {
        val currentApps = getLockedApps().toMutableSet()
        if (currentApps.removeAll(packageNames)) {
            preferences.edit { putStringSet(KEY_LOCKED_APPS, currentApps) }
        }
    }

    companion object {
        private const val PREFS_NAME = "app_lock_prefs"
        private const val KEY_LOCKED_APPS = "locked_apps"
        private const val KEY_TRIGGER_EXCLUDED_APPS = "trigger_excluded_apps"
    }
}
