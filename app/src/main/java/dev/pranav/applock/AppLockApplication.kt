package dev.pranav.applock

import android.app.Application
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import dev.pranav.applock.core.utils.isAccessibilityServiceEnabled
import dev.pranav.applock.core.utils.vibrate
import dev.pranav.applock.data.repository.AppLockRepository

class AppLockApplication : Application() {
    lateinit var appLockRepository: AppLockRepository

    companion object {
        private const val TAG = "AppLockApplication"
    }

    override fun onCreate() {
        super.onCreate()
        appLockRepository = AppLockRepository(this)

        checkAccessibilityServiceStatus()
    }

    private fun checkAccessibilityServiceStatus() {
        if (!isAccessibilityServiceEnabled()) {
            Log.d(TAG, "Accessibility service is not enabled, prompting user")
            Toast.makeText(
                this,
                "Please enable the accessibility service for App Lock to function properly",
                Toast.LENGTH_LONG
            ).show()

            vibrate(this, 300)

            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        } else {
            Log.d(TAG, "Accessibility service is already enabled")
        }
    }
}
