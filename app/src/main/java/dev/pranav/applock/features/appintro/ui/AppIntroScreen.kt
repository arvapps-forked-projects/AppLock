package dev.pranav.applock.features.appintro.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import dev.pranav.appintro.AppIntro
import dev.pranav.appintro.IntroPage
import dev.pranav.applock.core.navigation.Screen
import dev.pranav.applock.core.utils.hasUsagePermission
import dev.pranav.applock.core.utils.launchProprietaryOemSettings
import dev.pranav.applock.features.appintro.domain.AppIntroManager
import dev.pranav.applock.ui.icons.BatterySaver

@SuppressLint("BatteryLife")
@Composable
fun AppIntroScreen(navController: NavController) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    var usagePermissionGranted by remember { mutableStateOf(context.hasUsagePermission()) }
    var overlayPermissionGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var notificationPermissionGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }
    var batteryOptimizationDisabled by remember {
        mutableStateOf(
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
                .isIgnoringBatteryOptimizations(context.packageName)
        )
    }

    val requestPermissionLauncher: ActivityResultLauncher<String>? =
        if (activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
                onResult = { isGranted: Boolean ->
                    notificationPermissionGranted = isGranted
                }
            )
        } else {
            null
        }

    LaunchedEffect(key1 = context) { // Re-check permissions when context changes or on resume
        usagePermissionGranted = context.hasUsagePermission()
        overlayPermissionGranted = Settings.canDrawOverlays(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        batteryOptimizationDisabled =
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    val onFinishCallback = {
        AppIntroManager.markIntroAsCompleted(context)
        navController.navigate(Screen.SetPassword.route) {
            popUpTo(Screen.AppIntro.route) { inclusive = true }
        }
    }

    val introPages = listOf(
        IntroPage(
            title = "Welcome to AppLock",
            description = "Protect your apps and privacy with AppLock. We'll guide you through a quick setup.",
            icon = Icons.Filled.Lock,
            backgroundColor = Color(0xFF3F51B5), // welcomeBlue
            contentColor = Color.White,
            onNext = { true }
        ),
        IntroPage(
            title = "Secure Your Apps",
            description = "Keep your private apps protected with advanced locking mechanisms",
            icon = Icons.Default.Lock,
            backgroundColor = Color(0xFF1A73E8), // securityBlue
            contentColor = Color.White,
            onNext = { true }
        ),
        IntroPage(
            title = "Usage Stats Permission",
            description = "AppLock needs permission to monitor app usage to protect your apps. Tap 'Allow' and enable usage access for AppLock.",
            icon = Icons.Default.Lock,
            backgroundColor = Color(0xFFF57C00), // permissionOrange
            contentColor = Color.White,
            onNext = {
                usagePermissionGranted = context.hasUsagePermission()
                if (!usagePermissionGranted) {
                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    false
                } else {
                    true
                }
            }
        ),
        IntroPage(
            title = "Display Over Apps",
            description = "AppLock needs permission to display over other apps to show the lock screen. Tap 'Allow' and enable the permission.",
            icon = Icons.Default.Lock,
            backgroundColor = Color(0xFFF57C00), // permissionOrange
            contentColor = Color.White,
            onNext = {
                overlayPermissionGranted = Settings.canDrawOverlays(context)
                if (!overlayPermissionGranted) {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    false
                } else {
                    true
                }
            }
        ),
        IntroPage(
            title = "Disable Battery Optimization",
            description = "To ensure AppLock runs reliably in the background, please disable battery optimizations for the app. Tap 'Next' to open settings.",
            icon = BatterySaver,
            backgroundColor = Color(0xFFFF9800), // batterySaverOrange
            contentColor = Color.White,
            onNext = {
                val powerManager =
                    context.getSystemService(Context.POWER_SERVICE) as PowerManager
                val isIgnoringOptimizations =
                    powerManager.isIgnoringBatteryOptimizations(context.packageName)
                batteryOptimizationDisabled = isIgnoringOptimizations

                if (!isIgnoringOptimizations) {
                    launchProprietaryOemSettings(context)
                    return@IntroPage false
                }
                return@IntroPage true
            }
        ),
        IntroPage(
            title = "Notification Permission",
            description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                "AppLock needs permission to show notifications to keep you informed. Tap 'Next' to grant permission."
            else
                "Notification permission is automatically granted on your Android version.",
            icon = Icons.Default.Notifications,
            backgroundColor = Color(0xFFFFB300), // notificationYellow
            contentColor = Color.White,
            onNext = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val isGrantedCurrently = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                    notificationPermissionGranted = isGrantedCurrently

                    if (!isGrantedCurrently) {
                        requestPermissionLauncher?.launch(Manifest.permission.POST_NOTIFICATIONS)
                        return@IntroPage false
                    } else {
                        return@IntroPage true
                    }
                } else {
                    true
                }
            }
        ),
        IntroPage(
            title = "Complete Privacy",
            description = "Your data never leaves your device. AppLock protects your privacy at all times.",
            icon = Icons.Default.Lock,
            backgroundColor = Color(0xFF129E5E), // safetyGreen
            contentColor = Color.White,
            onNext = {
                // Re-check all permissions before finishing
                usagePermissionGranted = context.hasUsagePermission()
                overlayPermissionGranted = Settings.canDrawOverlays(context)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionGranted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                }
                val powerManager =
                    context.getSystemService(Context.POWER_SERVICE) as PowerManager
                batteryOptimizationDisabled =
                    powerManager.isIgnoringBatteryOptimizations(context.packageName)

                val allPermissionsGranted = usagePermissionGranted && overlayPermissionGranted &&
                        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || notificationPermissionGranted) &&
                        batteryOptimizationDisabled

                if (!allPermissionsGranted) {
                    Toast.makeText(
                        context,
                        "All permissions are required to proceed.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                allPermissionsGranted
            }
        )
    )

    AppIntro(
        pages = introPages,
        onSkip = { /* Decide if skip should navigate to main or set password */
            AppIntroManager.markIntroAsCompleted(context)
            // For now, skipping also leads to password setup or main screen if password already set.
            // This logic will be handled by MainActivity's initial routing.
            navController.navigate(Screen.SetPassword.route) { // Or Screen.Main.route if password exists
                popUpTo(Screen.AppIntro.route) { inclusive = true }
            }
        },
        onFinish = onFinishCallback,
        showSkipButton = false, // As per original AppIntroActivity, skip was not directly leading to MainActivity
        useAnimatedPager = true,
        nextButtonText = "Next",
        skipButtonText = "Skip",
        finishButtonText = "Get Started"
    )
}

