package dev.pranav.applock

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import dev.pranav.applock.core.navigation.AppNavHost
import dev.pranav.applock.core.navigation.Screen
import dev.pranav.applock.features.appintro.domain.AppIntroManager
import dev.pranav.applock.services.AppLockService
import dev.pranav.applock.ui.theme.AppLockTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Start the app lock service
        startForegroundService(Intent(this, AppLockService::class.java))

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
                }
            }
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
