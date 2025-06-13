package dev.pranav.applock

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import dev.pranav.applock.core.broadcast.DeviceAdmin
import dev.pranav.applock.core.navigation.AppNavHost
import dev.pranav.applock.core.navigation.Screen
import dev.pranav.applock.features.appintro.domain.AppIntroManager
import dev.pranav.applock.ui.components.AdminPasswordVerificationDialog
import dev.pranav.applock.ui.theme.AppLockTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check if we're being launched for admin disable verification
        val isAdminDisableVerification = intent.getBooleanExtra("verify_admin_disable", false)
        val isAdminDisabledWithoutAuth =
            intent.getBooleanExtra("admin_disabled_without_auth", false)

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

                    // Show password verification dialog if needed
                    val showVerificationDialog =
                        remember { mutableStateOf(isAdminDisableVerification) }

                    if (showVerificationDialog.value) {
                        AdminPasswordVerificationDialog(
                            onPasswordVerified = {
                                // Mark as verified in DeviceAdmin
                                val deviceAdmin = DeviceAdmin()
                                deviceAdmin.setPasswordVerified(this@MainActivity, true)

                                // Show success message
                                Toast.makeText(
                                    this@MainActivity,
                                    "Password verified. You may now disable admin permissions.",
                                    Toast.LENGTH_LONG
                                ).show()

                                showVerificationDialog.value = false
                            },
                            onDismiss = {
                                showVerificationDialog.value = false
                                // If user cancels, they shouldn't disable admin
                                Toast.makeText(
                                    this@MainActivity,
                                    "Verification cancelled. Admin permissions will remain enabled.",
                                    Toast.LENGTH_LONG
                                ).show()
                            },
                            validatePassword = { input ->
                                (application as AppLockApplication).appLockRepository.validatePassword(
                                    input
                                )
                            }
                        )
                    }

                    // Show security compromise alert if admin was disabled without auth
                    LaunchedEffect(isAdminDisabledWithoutAuth) {
                        if (isAdminDisabledWithoutAuth) {
                            Toast.makeText(
                                this@MainActivity,
                                "SECURITY ALERT: Admin permissions were disabled without verification!",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }

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
