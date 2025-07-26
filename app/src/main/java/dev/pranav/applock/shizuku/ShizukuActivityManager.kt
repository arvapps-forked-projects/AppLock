package dev.pranav.applock.shizuku

import android.app.ActivityManager
import android.app.ActivityManagerNative
import android.app.IActivityManager
import android.app.IActivityTaskManager
import android.app.IProcessObserver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.view.Display
import dev.pranav.applock.core.broadcast.DeviceUnlockReceiver
import dev.pranav.applock.data.repository.AppLockRepository
import dev.pranav.applock.data.repository.BackendImplementation
import dev.pranav.applock.services.AppLockManager
import dev.pranav.applock.services.isDeviceLocked
import dev.pranav.applock.services.knownRecentsClasses
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.util.Timer
import java.util.TimerTask


class ShizukuActivityManager(
    private val context: Context,
    private val appLockRepository: AppLockRepository,
    private val onForegroundAppChanged: (String, String, Long) -> Unit
) {
    private val TAG = "ShizukuActivityManager"
    private var lastForegroundApp = ""
    private var timer: Timer? = null

    private val processObserver = object : IProcessObserver.Stub() {
        override fun onForegroundActivitiesChanged(
            pid: Int, uid: Int, foregroundActivity: Boolean
        ) {
            val packageName = getPackageNameForUid(uid)
            if (packageName == null) return

            lastForegroundApp = packageName
            onForegroundAppChanged(packageName, "", System.currentTimeMillis())

            if (appLockRepository.isShizukuExperimentalEnabled()) {
                start()
            }
        }

        override fun onProcessDied(pid: Int, uid: Int) {}

        override fun onProcessStateChanged(pid: Int, uid: Int, procState: Int) {}

        override fun onForegroundServicesChanged(pid: Int, uid: Int, serviceTypes: Int) {}
        override fun onProcessStarted(
            pid: Int, processUid: Int, packageUid: Int, packageName: String, processName: String
        ) {
        }
    }

    private val iActivityManager: IActivityManager? by lazy(LazyThreadSafetyMode.NONE) {
        ActivityManagerNative.asInterface(
            ShizukuBinderWrapper(
                SystemServiceHelper.getSystemService(
                    Context.ACTIVITY_SERVICE
                )
            )
        )
    }

    private var deviceUnlockReceiver: DeviceUnlockReceiver? = null

    fun start(): Boolean {
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_DENIED) {
            Log.e(TAG, "Shizuku is not available")
            return false
        }
        try {
            // Register the device unlock receiver
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            deviceUnlockReceiver = DeviceUnlockReceiver {
                onForegroundAppChanged(lastForegroundApp, "", System.currentTimeMillis())
            }
            context.registerReceiver(deviceUnlockReceiver, filter)
            Log.d(TAG, "Device unlock receiver registered")


            if (appLockRepository.isShizukuExperimentalEnabled()) {
                // Unregister the process observer if it was previously registered
                iActivityManager?.unregisterProcessObserver(processObserver)

                timer = Timer()
                timer?.schedule(object : TimerTask() {
                    override fun run() {
                        if (!appLockRepository.isShizukuExperimentalEnabled()) {
                            cancel()
                            return
                        }

                        if (appLockRepository.getBackendImplementation() != BackendImplementation.SHIZUKU) {
                            Log.w(TAG, "Shizuku backend is not active, stopping timer")
                            cancel()
                            return
                        }
                        if (context.isDeviceLocked()) {
                            return
                        }
                        val activity = topActivity
                        if (activity != null) {
                            val packageName = activity.packageName
                            val className = activity.className

                            // TODO: Maybe add a less sophisticated "anti-uninstall" check here

                            if (packageName == lastForegroundApp && AppLockManager.isAppTemporarilyUnlocked(
                                    packageName
                                )
                            ) {
                                return
                            }

                            if (className in knownRecentsClasses) return

                            if (packageName == context.packageName) return // Skip this service itself

                            Log.d(TAG, "Foreground app changed to: $packageName, class: $className")

                            val timeMillis = System.currentTimeMillis()

                            lastForegroundApp = packageName

                            onForegroundAppChanged(packageName, className, timeMillis)
                        }
                    }
                }, 0, 500)
            } else {
                iActivityManager?.registerProcessObserver(processObserver)
                Log.d(TAG, "Process observer registered")
            }

            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start ShizukuActivityManager", e)
            return false
        }
    }

    fun stop() {
        context.unregisterReceiver(deviceUnlockReceiver ?: return)

        timer?.cancel()
        try {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_DENIED) {
                Log.e(TAG, "Shizuku is not available")
                return
            }
            iActivityManager?.unregisterProcessObserver(processObserver)
            Log.d(TAG, "Process observer unregistered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister process observer", e)
        }
    }

    private fun getPackageNameForUid(uid: Int): String? {
        return try {
            context.packageManager.getNameForUid(uid)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting package name for uid $uid", e)
            null
        }
    }
}

val topActivity: ComponentName?
    get() = getTasksWrapper().first().topActivity

private val activityTaskManager: IActivityTaskManager =
    SystemServiceHelper.getSystemService("activity_task")
        .let(::ShizukuBinderWrapper)
        .let(IActivityTaskManager.Stub::asInterface)

private fun getTasksWrapper(): List<ActivityManager.RunningTaskInfo> = when {
    Build.VERSION.SDK_INT < 31 -> activityTaskManager.getTasks(1)
    else -> runCatching { activityTaskManager.getTasks(1, false, false, Display.INVALID_DISPLAY) }
        .getOrElse { activityTaskManager.getTasks(1, false, false) }
}
