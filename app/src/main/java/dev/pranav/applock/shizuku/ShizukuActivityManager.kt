package dev.pranav.applock.shizuku

import android.app.ActivityManagerNative
import android.app.IActivityManager
import android.app.IProcessObserver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.Log
import dev.pranav.applock.core.broadcast.DeviceUnlockReceiver
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper


class ShizukuActivityManager(
    private val context: Context,
    private val onForegroundAppChanged: (String, Long) -> Unit
) {
    private val TAG = "ShizukuActivityManager"
    private lateinit var lastForegroundApp: String

    private val processObserver = object : IProcessObserver.Stub() {
        override fun onForegroundActivitiesChanged(pid: Int, uid: Int, foreground: Boolean) {
            if (!foreground) return

            val packageName = getPackageNameForUid(uid)
            if (packageName != null) {
                lastForegroundApp = packageName
                onForegroundAppChanged(packageName, System.currentTimeMillis())
            }
        }

        override fun onProcessDied(pid: Int, uid: Int) {}

        override fun onProcessStateChanged(pid: Int, uid: Int, procState: Int) {}

        override fun onForegroundServicesChanged(pid: Int, uid: Int, serviceTypes: Int) {}
        override fun onProcessStarted(
            pid: Int,
            processUid: Int,
            packageUid: Int,
            packageName: String?,
            processName: String?
        ) {
        }
    }

    private val iActivityManager: IActivityManager?
        get() = ActivityManagerNative.asInterface(
            ShizukuBinderWrapper(
                SystemServiceHelper.getSystemService(
                    Context.ACTIVITY_SERVICE
                )
            )
        )

    private var deviceUnlockReceiver: DeviceUnlockReceiver? = null

    fun start(): Boolean {
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_DENIED) {
            Log.e(TAG, "Shizuku is not available")
            return false
        }
        try {
            iActivityManager?.registerProcessObserver(processObserver)
            Log.d(TAG, "Process observer registered")

            // Register the device unlock receiver
            val filter =
                IntentFilter().apply {
                    addAction(Intent.ACTION_USER_PRESENT)
                    addAction(Intent.ACTION_SCREEN_OFF)
                }
            deviceUnlockReceiver = DeviceUnlockReceiver {
                if (::lastForegroundApp.isInitialized) {
                    onForegroundAppChanged(lastForegroundApp, System.currentTimeMillis())
                }
            }
            context.registerReceiver(deviceUnlockReceiver, filter)
            Log.d(TAG, "Device unlock receiver registered")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to register process observer", e)
            return false
        }
    }

    fun stop() {
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
