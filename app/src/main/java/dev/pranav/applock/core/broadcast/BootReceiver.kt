package dev.pranav.applock.core.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import dev.pranav.applock.services.AppLockService

class BootReceiver : BroadcastReceiver() {

    private val TAG = "BootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action || Intent.ACTION_LOCKED_BOOT_COMPLETED == intent.action) {
            Log.d(TAG, "Boot completed broadcast received. Starting AppLockService...")

            val serviceIntent = Intent(context, AppLockService::class.java)

            ContextCompat.startForegroundService(context, serviceIntent)
            Log.d(TAG, "AppLockService start command sent.")
        } else {
            Log.d(TAG, "Received unexpected intent action: ${intent.action}")
        }
    }
}

