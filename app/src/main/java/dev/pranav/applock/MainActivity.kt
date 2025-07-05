package dev.pranav.applock

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.compose.rememberNavController
import dev.pranav.applock.core.navigation.AppNavHost
import dev.pranav.applock.core.navigation.Screen
import dev.pranav.applock.core.utils.isAccessibilityServiceEnabled
import dev.pranav.applock.core.utils.vibrate
import dev.pranav.applock.features.appintro.domain.AppIntroManager
import dev.pranav.applock.ui.theme.AppLockTheme

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AppLockTheme {
                // Add a background Box that fills the entire screen
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    val navController = rememberNavController()
                    val startDestination = determineStartDestination()

                    AppNavHost(navController = navController, startDestination = startDestination)

                    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                        if (navController.currentDestination?.route == Screen.AppIntro.route || navController.currentDestination?.route == Screen.SetPassword.route) {
                            // If we are on the App Intro screen, we don't need to check for accessibility service
                            return@LifecycleEventEffect
                        }
                        if (navController.currentDestination?.route != Screen.PasswordOverlay.route) {
                            navController.navigate(Screen.PasswordOverlay.route)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (AppIntroManager.shouldShowIntro(this)) {
            return
        }
        checkAccessibilityServiceStatus()
    }

    private fun checkAccessibilityServiceStatus() {
        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(
                this,
                "Please enable the accessibility service for App Lock to function properly",
                Toast.LENGTH_LONG
            ).show()

            vibrate(this, 300)

            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        }
    }

    private fun determineStartDestination(): String {
        // Check if we should show the app intro
        if (AppIntroManager.shouldShowIntro(this)) {
            return Screen.AppIntro.route
        }

        // Check if password is set, if not, redirect to SetPasswordActivity
        val sharedPrefs = getSharedPreferences("app_lock_prefs", MODE_PRIVATE)
        val isPasswordSet = sharedPrefs.contains("password")

        return if (!isPasswordSet) {
            Screen.SetPassword.route
        } else {
            Screen.PasswordOverlay.route
        }
    }
}
