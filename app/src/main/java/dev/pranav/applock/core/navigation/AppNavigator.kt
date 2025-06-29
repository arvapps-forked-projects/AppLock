package dev.pranav.applock.core.navigation

import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import dev.pranav.applock.AppLockApplication
import dev.pranav.applock.features.appintro.ui.AppIntroScreen
import dev.pranav.applock.features.applist.ui.MainScreen
import dev.pranav.applock.features.lockscreen.ui.PasswordOverlayScreen
import dev.pranav.applock.features.setpassword.ui.SetPasswordScreen
import dev.pranav.applock.features.settings.ui.SettingsScreen

@Composable
fun AppNavHost(navController: NavHostController, startDestination: String) {
    val duration = 700
    val slideAnimationSpec: FiniteAnimationSpec<IntOffset> = tween(durationMillis = duration)
    val fadeAndScaleAnimationSpec: FiniteAnimationSpec<Float> = tween(durationMillis = duration)

    val application = LocalContext.current.applicationContext as AppLockApplication

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { it / 2 },
                animationSpec = slideAnimationSpec
            ) +
                    fadeIn(animationSpec = fadeAndScaleAnimationSpec) +
                    scaleIn(initialScale = 0.9f, animationSpec = fadeAndScaleAnimationSpec)
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { -it / 2 },
                animationSpec = slideAnimationSpec
            ) +
                    fadeOut(animationSpec = fadeAndScaleAnimationSpec) +
                    scaleOut(targetScale = 0.9f, animationSpec = fadeAndScaleAnimationSpec)
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { -it / 2 },
                animationSpec = slideAnimationSpec
            ) +
                    fadeIn(animationSpec = fadeAndScaleAnimationSpec) +
                    scaleIn(initialScale = 0.9f, animationSpec = fadeAndScaleAnimationSpec)
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { it / 2 },
                animationSpec = slideAnimationSpec
            ) +
                    fadeOut(animationSpec = fadeAndScaleAnimationSpec) +
                    scaleOut(targetScale = 0.9f, animationSpec = fadeAndScaleAnimationSpec)
        }
    ) {
        composable(Screen.AppIntro.route) { AppIntroScreen(navController) }

        composable(Screen.SetPassword.route) { SetPasswordScreen(navController, true) }

        composable(Screen.ChangePassword.route) { SetPasswordScreen(navController, false) }

        composable(Screen.Main.route) { MainScreen(navController) }

        composable(Screen.PasswordOverlay.route) {
            val context = LocalActivity.current as FragmentActivity
            PasswordOverlayScreen(
                showBiometricButton = application.appLockRepository.isBiometricAuthEnabled(),
                fromMainActivity = true,
                onBiometricAuth = {
                    val executor = ContextCompat.getMainExecutor(context)
                    val biometricPrompt =
                        BiometricPrompt(
                            context,
                            executor,
                            object : BiometricPrompt.AuthenticationCallback() {
                                override fun onAuthenticationError(
                                    errorCode: Int,
                                    errString: CharSequence
                                ) {
                                    super.onAuthenticationError(errorCode, errString)
                                    Log.w(
                                        "AppNavigator",
                                        "Authentication error: $errString ($errorCode)"
                                    )
                                }

                                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                    super.onAuthenticationSucceeded(result)
                                    navController.navigate(Screen.Main.route) {
                                        popUpTo(Screen.PasswordOverlay.route) { inclusive = true }
                                    }
                                }

                                override fun onAuthenticationFailed() {
                                    super.onAuthenticationFailed()
                                    Log.w(
                                        "AppNavigator",
                                        "Authentication failed (fingerprint not recognized)"
                                    )
                                }
                            })

                    val promptInfo = BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Confirm password")
                        .setSubtitle("Confirm biometric to continue")
                        .setNegativeButtonText("Use PIN")
                        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                        .setConfirmationRequired(false)
                        .build()

                    biometricPrompt.authenticate(promptInfo)
                },
                onAuthSuccess = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.PasswordOverlay.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Settings.route) { SettingsScreen(navController) }
    }
}

