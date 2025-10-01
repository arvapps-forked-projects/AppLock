package dev.pranav.applock.core.utils

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder.clearCallingIdentity
import android.os.Binder.restoreCallingIdentity
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.net.toUri
import dev.pranav.applock.R

fun vibrate(context: Context, duration: Long = 500) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager =
            context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    vibrator.vibrate(
        VibrationEffect.createOneShot(
            duration,
            VibrationEffect.DEFAULT_AMPLITUDE
        )
    )
}

/**
 * Launches the battery optimization settings for the app.
 * If the specific request intent is not available, it falls back to the standard settings.
 */
@SuppressLint("BatteryLife")
fun launchBatterySettings(context: Context) {
    val pm = context.packageManager
    val requestIgnoreIntent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = "package:${context.packageName}".toUri()
        }

    if (requestIgnoreIntent.resolveActivity(pm) != null) {
        context.startActivity(requestIgnoreIntent)
        Toast.makeText(
            context,
            context.getString(R.string.utils_battery_ignore_toast),
            Toast.LENGTH_LONG
        ).show()
    } else {
        val standardIntent =
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        if (standardIntent.resolveActivity(pm) != null) {
            context.startActivity(standardIntent)
            Toast.makeText(
                context,
                context.getString(R.string.utils_battery_specific_settings_not_found_toast),
                Toast.LENGTH_LONG
            ).show()
        } else {
            // Very rare case where even the standard settings screen is missing.
            Toast.makeText(
                context,
                context.getString(R.string.utils_battery_settings_not_found_toast),
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

fun Context.hasUsagePermission(): Boolean {
    try {
        val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
        val appOpsManager = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOpsManager.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            applicationInfo.uid,
            applicationInfo.packageName
        )
        return (mode == AppOpsManager.MODE_ALLOWED)
    } catch (e: PackageManager.NameNotFoundException) {
        Log.e("AppLockUtils", getString(R.string.utils_usage_permission_error_log) + e.message)
        return false
    }
}

fun Context.appLockRepository() =
    (applicationContext as? dev.pranav.applock.AppLockApplication)?.appLockRepository
        ?: throw IllegalStateException("AppLockRepository not initialized")


fun withCleanCallingIdentity(action: Runnable) {
    var throwableToPropagate: Throwable? = null
    val callingIdentity: Long = clearCallingIdentity()
    try {
        action.run()
    } catch (throwable: Throwable) {
        throwableToPropagate = throwable
    } finally {
        restoreCallingIdentity(callingIdentity)
        if (throwableToPropagate != null) {
            throw RuntimeException(throwableToPropagate)
        }
    }
}
