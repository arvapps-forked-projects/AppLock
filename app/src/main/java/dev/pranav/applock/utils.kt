package dev.pranav.applock

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager


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
        return false
    }
}
