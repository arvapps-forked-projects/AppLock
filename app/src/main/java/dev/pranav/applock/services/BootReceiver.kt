package dev.pranav.applock.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent


class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (Intent.ACTION_BOOT_COMPLETED == intent!!.action) {
            val serviceIntent = Intent(
                context,
                AppLockAccessibilityService::class.java
            )
            context!!.startService(serviceIntent)
        }
    }

}
