package dev.pranav.applock

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import dev.pranav.appintro.AppIntro
import dev.pranav.appintro.IntroPage
import dev.pranav.applock.ui.icons.BatterySaver
import dev.pranav.applock.ui.theme.AppLockTheme
import dev.pranav.applock.utils.launchProprietaryOemSettings

class AppIntroActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        var isTransitioning by mutableStateOf(false)

        setContent {
            AppLockTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppIntroWithTheme(
                        onFinish = {
                            if (!isTransitioning) {
                                isTransitioning = true

                                markIntroAsCompleted()

                                val intent = Intent(this, SetPasswordActivity::class.java)
                                intent.putExtra("FIRST_TIME_SETUP", true)
                                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

                                val options = ActivityOptionsCompat.makeCustomAnimation(
                                    this,
                                    android.R.anim.fade_in,
                                    android.R.anim.fade_out
                                )
                                startActivity(intent, options.toBundle())
                                finishAfterTransition()
                            }
                        },
                        onSkip = {
                            if (!isTransitioning) {
                                isTransitioning = true
                                markIntroAsCompleted()
                                val intent = Intent(this, MainActivity::class.java)
                                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                val options = ActivityOptionsCompat.makeCustomAnimation(
                                    this,
                                    android.R.anim.fade_in,
                                    android.R.anim.fade_out
                                )
                                startActivity(intent, options.toBundle())
                                finishAfterTransition()
                            }
                        }
                    )
                }
            }
        }
    }

    private fun markIntroAsCompleted() {
        val sharedPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        sharedPrefs.edit { putBoolean(PREF_INTRO_SHOWN, true) }
    }

    companion object {
        private const val PREF_INTRO_SHOWN = "intro_shown"

        fun shouldShowIntro(context: Context): Boolean {
            val sharedPrefs = context.getSharedPreferences("app_prefs", MODE_PRIVATE)
            return !sharedPrefs.getBoolean(PREF_INTRO_SHOWN, false)
        }
    }
}

@SuppressLint("BatteryLife")
@Composable
fun AppIntroWithTheme(
    onSkip: () -> Unit,
    onFinish: () -> Unit
) {
    val securityBlue = Color(0xFF1A73E8)
    val safetyGreen = Color(0xFF129E5E)
    val permissionOrange = Color(0xFFF57C00)
    val notificationYellow = Color(0xFFFFB300)
    val welcomeBlue = Color(0xFF3F51B5)
    val batterySaverOrange = Color(0xFFFF9800)

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

    LaunchedEffect(key1 = context) {
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


    val introPages = listOf(
        IntroPage(
            title = "Welcome to AppLock",
            description = "Protect your apps and privacy with AppLock. We'll guide you through a quick setup.",
            icon = Icons.Filled.Lock,
            backgroundColor = welcomeBlue,
            contentColor = Color.White,
            onNext = { true }
        ),
        IntroPage(
            title = "Secure Your Apps",
            description = "Keep your private apps protected with advanced locking mechanisms",
            icon = Icons.Default.Lock,
            backgroundColor = securityBlue,
            contentColor = Color.White,
            onNext = { true }
        ),
        IntroPage(
            title = "Usage Stats Permission",
            description = "AppLock needs permission to monitor app usage to protect your apps. Tap 'Allow' and enable usage access for AppLock.",
            icon = Icons.Default.Lock,
            backgroundColor = permissionOrange,
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
            backgroundColor = permissionOrange,
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
            backgroundColor = batterySaverOrange,
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
            backgroundColor = notificationYellow,
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
            backgroundColor = safetyGreen,
            contentColor = Color.White,
            onNext = {
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
        onSkip = onSkip,
        onFinish = onFinish,
        showSkipButton = false,
        useAnimatedPager = true,
        nextButtonText = "Next",
        skipButtonText = "Skip",
        finishButtonText = "Get Started"
    )
}
