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
import androidx.core.net.toUri

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
    val manufacturer = Build.MANUFACTURER.lowercase()
    val pm = context.packageManager

    val intentsToTry: List<Intent> = when (manufacturer) {
        "xiaomi" -> listOf(
            Intent().setComponent(
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            ),
            Intent().setComponent(
                ComponentName(
                    "com.miui.powerkeeper",
                    "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                )
            )
        )

        "oppo" -> listOf(
            Intent().setComponent(
                ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                )
            ),
            Intent().setComponent(
                ComponentName(
                    "com.oplus.safecenter",
                    "com.oplus.safecenter.permission.startup.StartupAppListActivity"
                )
            ),
            Intent().setComponent(
                ComponentName(
                    "com.coloros.oppoguardelf",
                    "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity"
                )
            )
        )

        "vivo" -> listOf(
            Intent().setComponent(
                ComponentName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                )
            ),
            Intent().setComponent(
                ComponentName(
                    "com.iqoo.secure",
                    "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
                )
            )
        )

        "oneplus" -> listOf(
            // For newer OnePlus (Android 12+), going to app details is most reliable.
            // For older ones, the specific intents below might work.
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            },
            Intent().setComponent(
                ComponentName(
                    "com.oneplus.security",
                    "com.oneplus.security.background.BackgroundProcessManagerActivity"
                )
            )
        )

        "huawei" -> listOf(
            Intent().setComponent(
                ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
            ),
            Intent().setComponent(
                ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.bootstart.BootStartActivity"
                )
            )
        )

        "samsung" -> listOf(
            // Samsung guides users to "Sleeping apps" which is inside the main battery settings.
            Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
        )

        else -> emptyList() // No specific intents for other manufacturers
    }

    // Find the first intent in the list that can be resolved.
    val resolvableOemIntent: Intent? = intentsToTry.firstOrNull {
        it.resolveActivity(pm) != null
    }

    // --- Launch the best possible intent ---
    try {
        if (resolvableOemIntent != null) {
            // A specific OEM intent was found and is launchable.
            context.startActivity(resolvableOemIntent)
            Toast.makeText(
                context,
                "Please find our app and enable auto-start/don't optimize.",
                Toast.LENGTH_LONG
            ).show()

        } else {
            // No specific OEM intent worked, now try ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val requestIgnoreIntent =
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = "package:${context.packageName}".toUri()
                    }

                if (requestIgnoreIntent.resolveActivity(pm) != null) {
                    context.startActivity(requestIgnoreIntent)
                    Toast.makeText(
                        context,
                        "Please allow our app to ignore battery optimizations.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Log.w(
                        "AppLockUtils", // Changed tag from "AppIntroActivity" to a more relevant one
                        "ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS not resolvable, trying ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS"
                    )
                    // Fallback to the general battery optimization settings if ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS also fails
                    val standardIntent =
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    if (standardIntent.resolveActivity(pm) != null) {
                        context.startActivity(standardIntent)
                        Toast.makeText(
                            context,
                            "Could not find specific settings. Please remove app from battery restrictions.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        // Very rare case where even the standard settings screen is missing.
                        Toast.makeText(
                            context,
                            "Could not open any battery settings.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } else {
                // For Android versions below M, ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS is not available.
                // Fallback to the general battery optimization settings directly.
                val standardIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                if (standardIntent.resolveActivity(pm) != null) {
                    context.startActivity(standardIntent)
                    Toast.makeText(
                        context,
                        "Could not find specific settings. Please remove app from battery restrictions.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        "Could not open any battery settings.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    } catch (e: SecurityException) {
        // Some OEMs might protect their settings with permissions.
        e.printStackTrace()
        Toast.makeText(
            context,
            "Could not open settings due to a security restriction.",
            Toast.LENGTH_LONG
        ).show()
    } catch (e: Exception) {
        // Catch any other unexpected exceptions.
        e.printStackTrace()
        Toast.makeText(
            context,
            "An unexpected error occurred while opening settings.",
            Toast.LENGTH_LONG
        ).show()
    }
}
