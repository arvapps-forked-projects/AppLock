package dev.pranav.applock.core.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.pranav.applock.core.utils.appLockRepository
import dev.pranav.applock.data.repository.BackendImplementation
import dev.pranav.applock.services.AppLockAccessibilityService
import dev.pranav.applock.services.ExperimentalAppLockService
import dev.pranav.applock.services.ShizukuAppLockService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val repository = context.appLockRepository()
        if (intent.action == Intent.ACTION_PACKAGE_REPLACED) {
            repository.setShowDonateLink(true)
        }
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            Log.w(TAG, "Invalid context or intent action")
            return
        }

        try {
            val appLockRepository = context.appLockRepository()
            startAppropriateServices(context, appLockRepository)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting services on boot", e)
        }
    }

    private fun startAppropriateServices(
        context: Context,
        repository: dev.pranav.applock.data.repository.AppLockRepository
    ) {
        if (repository.isAntiUninstallEnabled()) {
            startService(context, AppLockAccessibilityService::class.java)
        }

        when (repository.getBackendImplementation()) {
            BackendImplementation.SHIZUKU -> {
                startService(context, ShizukuAppLockService::class.java)
            }

            BackendImplementation.ACCESSIBILITY -> {
                startService(context, AppLockAccessibilityService::class.java)
            }

            BackendImplementation.USAGE_STATS -> {
                startService(context, ExperimentalAppLockService::class.java)
            }
        }
    }

    private fun startService(context: Context, serviceClass: Class<*>) {
        try {
            val serviceIntent = Intent(context, serviceClass)
            context.startService(serviceIntent)
            Log.d(TAG, "Started service: ${serviceClass.simpleName}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service: ${serviceClass.simpleName}", e)
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
