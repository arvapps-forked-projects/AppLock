package dev.pranav.applock.data.manager

import android.content.Context
import android.content.Intent
import android.util.Log
import dev.pranav.applock.data.repository.BackendImplementation
import dev.pranav.applock.services.AppLockAccessibilityService
import dev.pranav.applock.services.ExperimentalAppLockService
import dev.pranav.applock.services.ShizukuAppLockService

/**
 * Manages backend service operations and switching between different implementations.
 * Provides a centralized way to handle service lifecycle and backend selection.
 */
class BackendServiceManager(private val context: Context) {

    private var activeBackend: BackendImplementation? = null

    fun setActiveBackend(backend: BackendImplementation) {
        activeBackend = backend
        Log.d(TAG, "Active backend set to: ${backend.name}")
    }

    fun getActiveBackend(): BackendImplementation? = activeBackend

    fun startService(backend: BackendImplementation): Boolean {
        val serviceClass = getServiceClass(backend)
        return if (serviceClass != null) {
            startServiceInternal(serviceClass)
        } else {
            Log.w(TAG, "Unknown backend implementation: $backend")
            false
        }
    }

    fun stopService(backend: BackendImplementation): Boolean {
        val serviceClass = getServiceClass(backend)
        return if (serviceClass != null) {
            stopServiceInternal(serviceClass)
        } else {
            Log.w(TAG, "Unknown backend implementation: $backend")
            false
        }
    }

    fun shouldStartService(
        serviceClass: Class<*>,
        chosenBackend: BackendImplementation
    ): Boolean {
        Log.d(TAG, "Checking if service ${serviceClass.simpleName} should start")
        Log.d(TAG, "Active backend: ${activeBackend?.name}, Chosen backend: ${chosenBackend.name}")

        val serviceBackend = getBackendForService(serviceClass)
        if (serviceBackend == null) {
            Log.d(TAG, "Unknown service class: ${serviceClass.simpleName}")
            return false
        }

        // Service should start if it matches the chosen backend
        if (serviceBackend == chosenBackend) {
            Log.d(TAG, "Service ${serviceClass.simpleName} matches chosen backend")
            return true
        }

        // Service should start if it matches the active backend (fallback scenario)
        if (activeBackend != null && serviceBackend == activeBackend) {
            Log.d(TAG, "Service ${serviceClass.simpleName} matches active backend")
            return true
        }

        Log.d(TAG, "Service ${serviceClass.simpleName} should not start")
        return false
    }

    private fun getServiceClass(backend: BackendImplementation): Class<*>? {
        return when (backend) {
            BackendImplementation.ACCESSIBILITY -> AppLockAccessibilityService::class.java
            BackendImplementation.USAGE_STATS -> ExperimentalAppLockService::class.java
            BackendImplementation.SHIZUKU -> ShizukuAppLockService::class.java
        }
    }

    private fun getBackendForService(serviceClass: Class<*>): BackendImplementation? {
        return when (serviceClass) {
            AppLockAccessibilityService::class.java -> BackendImplementation.ACCESSIBILITY
            ExperimentalAppLockService::class.java -> BackendImplementation.USAGE_STATS
            ShizukuAppLockService::class.java -> BackendImplementation.SHIZUKU
            else -> null
        }
    }

    private fun startServiceInternal(serviceClass: Class<*>): Boolean {
        return try {
            val serviceIntent = Intent(context, serviceClass)
            context.startService(serviceIntent)
            Log.d(TAG, "Successfully started service: ${serviceClass.simpleName}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service: ${serviceClass.simpleName}", e)
            false
        }
    }

    private fun stopServiceInternal(serviceClass: Class<*>): Boolean {
        return try {
            val serviceIntent = Intent(context, serviceClass)
            context.stopService(serviceIntent)
            Log.d(TAG, "Successfully stopped service: ${serviceClass.simpleName}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop service: ${serviceClass.simpleName}", e)
            false
        }
    }

    companion object {
        private const val TAG = "BackendServiceManager"
    }
}
