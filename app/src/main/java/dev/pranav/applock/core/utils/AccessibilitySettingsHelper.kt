package dev.pranav.applock.core.utils

import android.content.Context
import android.content.Intent
import android.provider.Settings

fun Context.isAccessibilityServiceEnabled(): Boolean {
    val accessibilityServiceName =
        "$packageName/$packageName.services.AppLockAccessibilityService"
    val enabledServices = Settings.Secure.getString(
        contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    )
    return enabledServices?.contains(accessibilityServiceName) == true
}

fun openAccessibilitySettings(context: Context) {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    context.startActivity(intent)
}
