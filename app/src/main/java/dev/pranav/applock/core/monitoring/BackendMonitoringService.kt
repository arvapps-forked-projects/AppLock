package dev.pranav.applock.core.monitoring

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import dev.pranav.applock.core.utils.appLockRepository
import dev.pranav.applock.data.repository.BackendImplementation
import dev.pranav.applock.services.ExperimentalAppLockService
import dev.pranav.applock.services.ShizukuAppLockService

class BackendMonitoringService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var monitoringRunnable: Runnable? = null
    private val monitoringInterval = 5000L // Check every 5 seconds

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startMonitoring()
        return START_STICKY // Restart if killed
    }

    override fun onDestroy() {
        stopMonitoring()
        super.onDestroy()
    }

    private fun startMonitoring() {
        Log.d("BackendMonitor", "Starting backend monitoring")

        monitoringRunnable = object : Runnable {
            override fun run() {
                try {
                    checkAndSwitchBackend()
                } catch (e: Exception) {
                    Log.e("BackendMonitor", "Error during backend check", e)
                }

                // Schedule next check
                handler.postDelayed(this, monitoringInterval)
            }
        }

        // Start monitoring
        handler.post(monitoringRunnable!!)
    }

    private fun stopMonitoring() {
        Log.d("BackendMonitor", "Stopping backend monitoring")
        monitoringRunnable?.let { handler.removeCallbacks(it) }
        monitoringRunnable = null
    }

    private fun checkAndSwitchBackend() {
        val appLockRepository = applicationContext.appLockRepository()
        val currentActive = appLockRepository.getActiveBackend()

        // Validate and switch if needed
        val newActiveBackend = appLockRepository.validateAndSwitchBackend(applicationContext)

        // If backend changed, we need to start/stop appropriate services
        if (newActiveBackend != currentActive) {
            Log.i("BackendMonitor", "Backend switched from $currentActive to $newActiveBackend")
            handleBackendSwitch(currentActive!!, newActiveBackend)
        }
    }

    private fun handleBackendSwitch(
        oldBackend: BackendImplementation,
        newBackend: BackendImplementation
    ) {
        Log.d("BackendMonitor", "Switching from $oldBackend to $newBackend")

        // Stop all services first to ensure only one runs at a time
        stopAllServices()

        // Start new backend service
        startBackendService(newBackend)
    }

    private fun stopAllServices() {
        Log.d("BackendMonitor", "Stopping all app lock services")

        // Stop all possible services
        stopService(Intent(this, ExperimentalAppLockService::class.java))
        stopService(Intent(this, ShizukuAppLockService::class.java))

        // Note: Accessibility service cannot be stopped programmatically
        Log.d("BackendMonitor", "All stoppable services have been stopped")
    }

    private fun stopBackendService(backend: BackendImplementation) {
        when (backend) {
            BackendImplementation.USAGE_STATS -> {
                Log.d("BackendMonitor", "Stopping ExperimentalAppLockService")
                stopService(Intent(this, ExperimentalAppLockService::class.java))
            }

            BackendImplementation.SHIZUKU -> {
                Log.d("BackendMonitor", "Stopping ShizukuAppLockService")
                stopService(Intent(this, ShizukuAppLockService::class.java))
            }

            BackendImplementation.ACCESSIBILITY -> {
                // Accessibility service is managed by the system, can't stop it programmatically
                Log.d("BackendMonitor", "Cannot stop accessibility service programmatically")
            }
        }
    }

    private fun startBackendService(backend: BackendImplementation) {
        when (backend) {
            BackendImplementation.USAGE_STATS -> {
                Log.d("BackendMonitor", "Starting ExperimentalAppLockService")
                startService(Intent(this, ExperimentalAppLockService::class.java))
            }

            BackendImplementation.SHIZUKU -> {
                Log.d("BackendMonitor", "Starting ShizukuAppLockService")
                startService(Intent(this, ShizukuAppLockService::class.java))
            }

            BackendImplementation.ACCESSIBILITY -> {
                // Accessibility service is managed by the system
                Log.d("BackendMonitor", "Accessibility service managed by system")
            }
        }
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, BackendMonitoringService::class.java)
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, BackendMonitoringService::class.java)
            context.stopService(intent)
        }
    }
}
