package dev.pranav.applock

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import dev.pranav.appintro.AppIntro
import dev.pranav.appintro.IntroPage
import dev.pranav.applock.ui.theme.AppLockTheme

class AppIntroActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set a flag to track if we've started the transition to prevent double launches
        var isTransitioning by mutableStateOf(false)

        setContent {
            AppLockTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Create and display the app intro with custom Material 3 colors
                    AppIntroWithTheme(
                        onFinish = {
                            // Only start transition if we haven't already started one
                            if (!isTransitioning) {
                                isTransitioning = true

                                // Mark intro as completed before transition
                                markIntroAsCompleted()

                                // Create a smooth transition to the main activity
                                val intent = Intent(this, SetPasswordActivity::class.java)
                                intent.putExtra("FIRST_TIME_SETUP", true)

                                // Add flag to clear the activity stack to prevent back navigation to intro
                                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

                                // Use an activity transition animation for smoother experience
                                val options = ActivityOptionsCompat.makeCustomAnimation(
                                    this,
                                    android.R.anim.fade_in,
                                    android.R.anim.fade_out
                                )

                                // Start the activity with transition
                                startActivity(intent, options.toBundle())

                                // Finish with a fade-out transition
                                finishAfterTransition()
                            }
                        },
                        onSkip = {
                            // Only start transition if we haven't already started one
                            if (!isTransitioning) {
                                isTransitioning = true

                                // Mark intro as completed before transition
                                markIntroAsCompleted()

                                // Similar smooth transition for skip
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
        // Save a flag indicating that the intro has been shown
        val sharedPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit { putBoolean(PREF_INTRO_SHOWN, true) }
    }

    companion object {
        private const val PREF_INTRO_SHOWN = "intro_shown"

        // Helper method to check if the intro has been shown
        fun shouldShowIntro(context: Context): Boolean {
            val sharedPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            return !sharedPrefs.getBoolean(PREF_INTRO_SHOWN, false)
        }
    }
}

@Composable
fun AppIntroWithTheme(
    onSkip: () -> Unit,
    onFinish: () -> Unit
) {
    // Create pages with proper Material 3 colors and modern design
    val securityBlue = Color(0xFF1A73E8)
    val privacyPurple = Color(0xFF6200EE)
    val safetyGreen = Color(0xFF129E5E)
    val permissionOrange = Color(0xFFF57C00)
    val notificationYellow = Color(0xFFFFB300)

    val context = androidx.compose.ui.platform.LocalContext.current
    var usagePermissionGranted by remember { mutableStateOf(context.hasUsagePermission()) }
    var overlayPermissionGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var notificationPermissionGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val introPages = listOf(
        // First page - Security
        IntroPage(
            title = "Secure Your Apps",
            description = "Keep your private apps protected with advanced locking mechanisms",
            icon = Icons.Default.Lock,
            backgroundColor = securityBlue,
            contentColor = Color.White,
            // Return true to allow navigation
            onNext = { true }
        ),

        // Usage Stats Permission page
        IntroPage(
            title = "Usage Stats Permission",
            description = "AppLock needs permission to monitor app usage to protect your apps. Tap 'Allow' and enable usage access for AppLock.",
            icon = Icons.Default.Lock,
            backgroundColor = permissionOrange,
            contentColor = Color.White,
            onNext = {
                // Check if usage permission is granted
                usagePermissionGranted = context.hasUsagePermission()

                if (!usagePermissionGranted) {
                    // If not, redirect to settings
                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    false // Do not proceed to next page
                } else {
                    true // Proceed to next page
                }
            }
        ),

        // Overlay Permission page
        IntroPage(
            title = "Display Over Apps",
            description = "AppLock needs permission to display over other apps to show the lock screen. Tap 'Allow' and enable the permission.",
            icon = Icons.Default.Lock,
            backgroundColor = permissionOrange,
            contentColor = Color.White,
            onNext = {
                // Check if overlay permission is granted
                overlayPermissionGranted = Settings.canDrawOverlays(context)

                if (!overlayPermissionGranted) {
                    // If not, redirect to settings
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    false // Do not proceed to next page
                } else {
                    true // Proceed to next page
                }
            }
        ),

        // Notification Permission page (Android 14+)
        IntroPage(
            title = "Notification Permission",
            description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                "AppLock needs permission to show notifications to keep you informed. Tap 'Allow' and enable notifications."
            else
                "Notification permission is automatically granted on your Android version.",
            icon = Icons.Default.Notifications,
            backgroundColor = notificationYellow,
            contentColor = Color.White,
            onNext = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    // Check if notification permission is granted
                    notificationPermissionGranted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED

                    if (!notificationPermissionGranted) {
                        // If not, redirect to settings
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        false // Do not proceed to next page
                    } else {
                        true // Proceed to next page
                    }
                } else {
                    true // Always proceed on older Android versions
                }
            }
        ),

        // Final page - Privacy
        IntroPage(
            title = "Complete Privacy",
            description = "Your data never leaves your device. AppLock protects your privacy at all times.",
            icon = Icons.Default.Lock,
            backgroundColor = safetyGreen,
            contentColor = Color.White,
            onNext = {
                // Final check to ensure all permissions are granted
                usagePermissionGranted = context.hasUsagePermission()
                overlayPermissionGranted = Settings.canDrawOverlays(context)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    notificationPermissionGranted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                }

                val allPermissionsGranted = usagePermissionGranted && overlayPermissionGranted &&
                        (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || notificationPermissionGranted)

                allPermissionsGranted
            }
        )
    )

    // Display the app intro with custom button text
    AppIntro(
        pages = introPages,
        onSkip = onSkip,
        onFinish = onFinish,
        showSkipButton = true,
        useAnimatedPager = true,
        nextButtonText = "Next",
        skipButtonText = "Skip",
        finishButtonText = "Get Started"
    )
}
