package dev.pranav.applock.shizuku

import android.app.ActivityManagerNative
import android.app.IActivityManager
import android.app.IProcessObserver
import android.content.Context
import android.util.Log
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper


class ShizukuActivityManager(
    private val context: Context,
    private val onForegroundAppChanged: (String, Long) -> Unit
) {

    private val processObserver = object : IProcessObserver.Stub() {

        override fun onForegroundActivitiesChanged(pid: Int, uid: Int, foreground: Boolean) {
            if (!foreground) return

            val packageName = getPackageNameForUid(uid)
            if (packageName != null) {
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


    fun start() {
        try {
            iActivityManager?.registerProcessObserver(processObserver)
            Log.d("ShizukuActivityManager", "Process observer registered")
        } catch (e: Exception) {
            Log.e("ShizukuActivityManager", "Failed to register process observer", e)
        }
    }

    fun stop() {
        try {
            iActivityManager?.unregisterProcessObserver(processObserver)
            Log.d("ShizukuActivityManager", "Process observer unregistered")
        } catch (e: Exception) {
            Log.e("ShizukuActivityManager", "Failed to unregister process observer", e)
        }
    }

    private fun getPackageNameForUid(uid: Int): String? {
        return try {
            context.packageManager.getNameForUid(uid)
        } catch (e: Exception) {
            Log.e("ShizukuActivityManager", "Error getting package name for uid $uid", e)
            null
        }
    }
}
