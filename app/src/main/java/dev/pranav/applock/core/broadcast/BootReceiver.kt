package dev.pranav.applock.core.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.pranav.applock.services.AppLockAccessibilityService


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
