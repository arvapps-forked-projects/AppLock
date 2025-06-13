package dev.pranav.applock.core.broadcast

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class DeviceAdmin : DeviceAdminReceiver() {
    companion object {
        private const val PREFS_NAME = "dev.pranav.applock.admin_prefs"
        private const val KEY_PASSWORD_VERIFIED = "password_verified"
    }

    fun setPasswordVerified(context: Context, verified: Boolean) {
        getSharedPreferences(context).edit { putBoolean(KEY_PASSWORD_VERIFIED, verified) }
    }

    private fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
