package dev.pranav.applock.core.utils

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast

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
        Log.e("AppLockUtils", "Error checking usage permission: ${e.message}")
        return false
    }
}

/**
 * Utility function to launch the appropriate OEM-specific settings screen for battery optimization.
 * This function handles various manufacturers and their specific intents for managing auto-start
 * and battery optimization settings.
 *
 * @param context The application context used to start the activity.
 */
fun launchProprietaryOemSettings(context: Context) {
    val intents = listOf(
        Intent().setComponent(
            ComponentName(
                "com.miui.powerkeeper",
                "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
            )
        )
            .putExtra("package_name", context.packageName)
            .putExtra(
                "package_label",
                context.applicationInfo.loadLabel(context.packageManager).toString()
            ),
        Intent().setComponent(
            ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.optimize.process.ProtectActivity"
            )
        ),
        Intent().setComponent(
            ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"
            )
        ),
        Intent().setComponent(
            ComponentName(
                "com.oppo.safe",
                "com.oppo.safe.permission.startup.StartupAppListActivity"
            )
        ),
        Intent().setComponent(
            ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            )
        ),
        Intent().setComponent(
            ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.startupapp.StartupAppListActivity"
            )
        ),
        Intent().setComponent(
            ComponentName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            )
        ),
        Intent().setComponent(
            ComponentName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.ui.battery.BatteryActivity"
            )
        ),
        Intent().setComponent(
            ComponentName(
                "com.samsung.android.sm",
                "com.samsung.android.sm.battery.ui.BatteryActivity"
            )
        ),
        Intent().setComponent(
            ComponentName(
                "com.samsung.android.sm",
                "com.samsung.android.sm.ui.battery.AppSleepListActivity"
            )
        ),
        Intent().setComponent(
            ComponentName(
                "com.asus.mobilemanager",
                "com.asus.mobilemanager.powersaver.PowerSavingSettingsActivity"
            )
        ),
        Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS), // General battery saver settings
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) // General ignore battery optimization
    )

    var launched = false
    for (intent in intents) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            launched = true
            Log.d("OemSettings", "Launched OEM settings with intent: $intent")
            break // Exit loop if an intent is successfully launched
        } catch (e: Exception) {
            Log.w(
                "OemSettings",
                "Failed to launch OEM settings with intent: $intent, error: ${e.message}"
            )
            // Try the next intent
        }
    }

    if (!launched) {
        // Fallback if no specific OEM intent worked
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.fromParts("package", context.packageName, null)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Toast.makeText(
                context,
                "Please find battery optimization settings for AppLock and disable them.",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Log.e("OemSettings", "Failed to launch generic app details settings: ${e.message}")
            Toast.makeText(
                context,
                "Could not open battery settings. Please do it manually.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

