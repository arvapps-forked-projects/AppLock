package dev.pranav.applock.data.repository

import android.content.Context
import dev.pranav.applock.data.manager.BackendServiceManager

/**
 * Main repository that coordinates between different specialized repositories and managers.
 * Provides a unified interface for all app lock functionality.
 */
class AppLockRepository(private val context: Context) {

    private val preferencesRepository = PreferencesRepository(context)
    private val lockedAppsRepository = LockedAppsRepository(context)
    private val backendServiceManager = BackendServiceManager(context)

    // Delegate locked apps operations
    fun getLockedApps(): Set<String> = lockedAppsRepository.getLockedApps()
    fun addLockedApp(packageName: String) = lockedAppsRepository.addLockedApp(packageName)
    fun removeLockedApp(packageName: String) = lockedAppsRepository.removeLockedApp(packageName)
    fun isAppLocked(packageName: String): Boolean = lockedAppsRepository.isAppLocked(packageName)

    // Delegate trigger exclusions operations
    fun getTriggerExcludedApps(): Set<String> = lockedAppsRepository.getTriggerExcludedApps()
    fun addTriggerExcludedApp(packageName: String) =
        lockedAppsRepository.addTriggerExcludedApp(packageName)

    fun removeTriggerExcludedApp(packageName: String) =
        lockedAppsRepository.removeTriggerExcludedApp(packageName)

    fun isAppTriggerExcluded(packageName: String): Boolean =
        lockedAppsRepository.isAppTriggerExcluded(packageName)

    // Delegate authentication operations
    fun getPassword(): String? = preferencesRepository.getPassword()
    fun setPassword(password: String) = preferencesRepository.setPassword(password)
    fun validatePassword(inputPassword: String): Boolean =
        preferencesRepository.validatePassword(inputPassword)

    // Delegate biometric operations
    fun setBiometricAuthEnabled(enabled: Boolean) =
        preferencesRepository.setBiometricAuthEnabled(enabled)

    fun isBiometricAuthEnabled(): Boolean = preferencesRepository.isBiometricAuthEnabled()
    fun setPromptForBiometricAuth(enabled: Boolean) =
        preferencesRepository.setPromptForBiometricAuth(enabled)

    fun shouldPromptForBiometricAuth(): Boolean =
        preferencesRepository.shouldPromptForBiometricAuth()

    // Delegate UI settings
    fun setUseMaxBrightness(enabled: Boolean) = preferencesRepository.setUseMaxBrightness(enabled)
    fun shouldUseMaxBrightness(): Boolean = preferencesRepository.shouldUseMaxBrightness()
    fun setDisableHaptics(enabled: Boolean) = preferencesRepository.setDisableHaptics(enabled)
    fun shouldDisableHaptics(): Boolean = preferencesRepository.shouldDisableHaptics()

    // Delegate security settings
    fun setAntiUninstallEnabled(enabled: Boolean) =
        preferencesRepository.setAntiUninstallEnabled(enabled)

    fun isAntiUninstallEnabled(): Boolean = preferencesRepository.isAntiUninstallEnabled()
    fun setProtectEnabled(enabled: Boolean) = preferencesRepository.setProtectEnabled(enabled)
    fun isProtectEnabled(): Boolean = preferencesRepository.isProtectEnabled()

    // Delegate unlock settings
    fun setUnlockTimeDuration(minutes: Int) = preferencesRepository.setUnlockTimeDuration(minutes)
    fun getUnlockTimeDuration(): Int = preferencesRepository.getUnlockTimeDuration()
    fun setAutoUnlockEnabled(enabled: Boolean) = preferencesRepository.setAutoUnlockEnabled(enabled)
    fun isAutoUnlockEnabled(): Boolean = preferencesRepository.isAutoUnlockEnabled()
    fun setUnlockBehavior(behavior: Int) = preferencesRepository.setUnlockBehavior(behavior)
    fun getUnlockBehavior(): Int = preferencesRepository.getUnlockBehavior()

    // Delegate backend operations
    fun setBackendImplementation(backend: BackendImplementation) =
        preferencesRepository.setBackendImplementation(backend)

    fun getBackendImplementation(): BackendImplementation =
        preferencesRepository.getBackendImplementation()

    fun getFallbackBackend(): BackendImplementation = preferencesRepository.getFallbackBackend()

    // Delegate app state operations
    fun isShowCommunityLink(): Boolean = preferencesRepository.isShowCommunityLink()
    fun setCommunityLinkShown(shown: Boolean) = preferencesRepository.setCommunityLinkShown(shown)
    fun isShowDonateLink(): Boolean = preferencesRepository.isShowDonateLink(context)

    // Backend service management
    fun setActiveBackend(backend: BackendImplementation) =
        backendServiceManager.setActiveBackend(backend)

    fun getActiveBackend(): BackendImplementation? = backendServiceManager.getActiveBackend()
    fun startBackendService(backend: BackendImplementation): Boolean =
        backendServiceManager.startService(backend)

    fun stopBackendService(backend: BackendImplementation): Boolean =
        backendServiceManager.stopService(backend)

    companion object {
        private const val TAG = "AppLockRepository"

        fun shouldStartService(repository: AppLockRepository, serviceClass: Class<*>): Boolean {
            return repository.backendServiceManager.shouldStartService(
                serviceClass,
                repository.getBackendImplementation()
            )
        }
    }
}

enum class BackendImplementation {
    ACCESSIBILITY,
    USAGE_STATS,
    SHIZUKU
}
