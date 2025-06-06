package dev.pranav.applock

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.activity.addCallback
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dev.pranav.applock.ui.icons.Backspace
import dev.pranav.applock.ui.theme.AppLockTheme
import java.util.concurrent.Executor

class PasswordOverlayScreen : FragmentActivity() {
    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    // Flag to track if biometric prompt is already showing
    private var isBiometricPromptShowing = false

    // Add a flag to track if this activity was moved to the background
    private var movedToBackground = false

    companion object {
        private const val TAG = "PasswordOverlay"

        // Static field to track the currently active instance
        private var activeInstance: PasswordOverlayScreen? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Finish any existing instances of PasswordOverlayScreen
        activeInstance?.let { existingInstance ->
            if (!existingInstance.isFinishing && existingInstance != this) {
                Log.d(TAG, "Finishing previous PasswordOverlayScreen instance")
                existingInstance.finishAndRemoveTask()
            }
        }

        // Register this as the active instance
        activeInstance = this

        enableEdgeToEdge()
        setupWindowFlags()
        setupBiometricPrompt()
        setupBackPressHandler()

        shapes.shuffle()

        // Apply user preferences
        applyUserPreferences()

        // Set up the UI
        setContent {
            AppLockTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PasswordScreen(
                        modifier = Modifier.padding(innerPadding),
                        showBiometricButton = shouldShowBiometricButton(),
                        onBiometricAuth = { showBiometricPromptIfNotShowing() }
                    )
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun setupWindowFlags() {
        // Set the activity to appear over the lock screen and stay on top
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )

        // Set window type - this can ONLY be done during window creation (in onCreate)
        // Cannot be changed in onResume or later lifecycle methods
        val layoutParams = window.attributes
        layoutParams.type =
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        window.attributes = layoutParams
    }

    @Suppress("DEPRECATION")
    private fun setupReapplicableWindowFlags() {
        // Set only the flags that can be safely reapplied after window creation
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this) {
            AppLockService.isOverlayActive = false
            finish()

            // Go to home screen when back is pressed
            val homeIntent = Intent(Intent.ACTION_MAIN)
            homeIntent.addCategory(Intent.CATEGORY_HOME)
            homeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(homeIntent)
        }
    }

    private fun applyUserPreferences() {
        val prefs = getSharedPreferences("app_lock_settings", MODE_PRIVATE)

        // Apply brightness setting
        val useMaxBrightness = prefs.getBoolean("use_max_brightness", false)
        if (useMaxBrightness) {
            window.attributes.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        }

        // Automatically show biometric prompt if enabled
        val useBiometricAuth = prefs.getBoolean("use_biometric_auth", false)
        if (useBiometricAuth && !isBiometricPromptShowing) {
            showBiometricPrompt()
        }
    }

    private fun shouldShowBiometricButton(): Boolean {
        val prefs = getSharedPreferences("app_lock_settings", MODE_PRIVATE)
        return prefs.getBoolean("use_biometric_auth", false)
    }

    private fun showBiometricPromptIfNotShowing() {
        if (!isBiometricPromptShowing) {
            showBiometricPrompt()
        }
    }

    private fun setupBiometricPrompt() {
        executor = ContextCompat.getMainExecutor(this)
        biometricPrompt = BiometricPrompt(this, executor, authenticationCallback)
        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Authenticate to unlock app")
            .setSubtitle("Use your fingerprint to unlock the app")
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
    }

    private val authenticationCallback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            super.onAuthenticationError(errorCode, errString)
            Log.d(TAG, "Biometric authentication error: $errorCode - $errString")
            resetBiometricFlags()

            Log.w(TAG, "Authentication error: $errString")
        }

        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            super.onAuthenticationSucceeded(result)
            Log.d(TAG, "Biometric authentication succeeded")
            resetBiometricFlags()
            handleSuccessfulAuthentication()
        }

    }

    private fun resetBiometricFlags() {
        isBiometricPromptShowing = false
        AppLockService.isBiometricAuthentication = false
        val appLockService = (applicationContext as AppLockApplication).appLockServiceInstance
        appLockService?.isBiometricAuthInProgress = false
    }

    private fun handleSuccessfulAuthentication() {
        val packageToUnlock =
            intent.getStringExtra("locked_package") ?: AppLockService.currentLockedPackage

        val appLockService = (applicationContext as AppLockApplication).appLockServiceInstance
        if (packageToUnlock != null && appLockService != null) {
            Log.d(TAG, "Unlocking package via biometric: $packageToUnlock")
            appLockService.temporarilyUnlockApp(packageToUnlock)
            AppLockService.isOverlayActive = false
            resetBiometricFlags()
            Log.d(TAG, "Finishing lock screen to reveal: $packageToUnlock")

            // FIXED: Use the package manager to launch the correct app instead of creating an intent with the package as action
            try {
                // Get the launcher activity for the package
                val launchIntent = packageManager.getLaunchIntentForPackage(packageToUnlock)

                if (launchIntent != null) {
                    // Add flags to properly launch the app
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(launchIntent)
                    Log.d(TAG, "Launched intent for package: $packageToUnlock")
                } else {
                    Log.w(TAG, "No launch intent found for package: $packageToUnlock")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error launching app: ${e.message}")
            }

        } else {
            Log.w(TAG, "No package to unlock or service not available after biometric success.")
            resetBiometricFlags()
            AppLockService.isOverlayActive = false
        }
    }

    private fun showBiometricPrompt() {
        // Only show if not already showing
        if (!isBiometricPromptShowing) {
            // Set the flags that biometric authentication is in progress
            isBiometricPromptShowing = true
            AppLockService.isBiometricAuthentication = true

            // Set the service flag too
            val appLockService = (applicationContext as AppLockApplication).appLockServiceInstance
            appLockService?.isBiometricAuthInProgress = true

            Log.d(TAG, "Starting biometric authentication, flags set")
            biometricPrompt.authenticate(promptInfo)
        }
    }

    override fun onResume() {
        super.onResume()
        // Only apply flags that can be safely changed after window creation
        setupReapplicableWindowFlags()
        applyUserPreferences()
    }

    @Suppress("DEPRECATION")
    override fun onPause() {
        super.onPause()

        // Mark that we're potentially moving to background
        movedToBackground = true

        // Clear flags
        window.clearFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        // Start a delayed operation to finish the activity if it stays in background
        Handler(Looper.getMainLooper()).postDelayed({
            if (movedToBackground && !isFinishing && !isDestroyed) {
                Log.d(TAG, "Activity remained in background, finishing it")
                AppLockService.isOverlayActive = false
                AppLockService.currentLockedPackage = null
                finishAndRemoveTask()
            }
        }, 500) // Small delay to see if we're truly going to background
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "PasswordOverlayScreen onStop called")

        // If we're stopping without having unlocked the app, we should finish
        // to prevent the lock screen from staying in the back stack
        if (AppLockService.isOverlayActive &&
            !isFinishing &&
            !isChangingConfigurations
        ) {

            Log.d(TAG, "Finishing lock screen in onStop as user navigated away without unlocking")
            AppLockService.isOverlayActive = false
            AppLockService.currentLockedPackage = null
            finishAndRemoveTask() // This completely removes the activity from the back stack
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Clear the static reference if this is the active instance
        if (activeInstance === this) {
            activeInstance = null
        }

        AppLockService.isOverlayActive = false
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val shapes = mutableListOf(
    MaterialShapes.Triangle,
    MaterialShapes.Pentagon,
    MaterialShapes.Circle,
    MaterialShapes.Arrow,
    MaterialShapes.Pill,
    MaterialShapes.Cookie4Sided,
    MaterialShapes.Heart,
    MaterialShapes.PixelTriangle,
    MaterialShapes.PixelCircle,
    MaterialShapes.Gem
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalAnimationApi::class)
@Composable
fun PasswordScreen(
    modifier: Modifier = Modifier,
    showBiometricButton: Boolean = false,
    onBiometricAuth: () -> Unit = {}
) {
    val passwordState = remember { mutableStateOf("") }
    val maxLength = 6
    val appLockService =
        (LocalContext.current.applicationContext as AppLockApplication).appLockServiceInstance
    val activity = LocalActivity.current as PasswordOverlayScreen

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "Enter password to continue",
            style = MaterialTheme.typography.headlineMediumEmphasized,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Password indicators
        PasswordIndicators(
            passwordLength = passwordState.value.length,
            maxLength = maxLength
        )

        Spacer(modifier = Modifier.weight(1f))

        // Keypad
        KeypadSection(
            passwordState = passwordState,
            maxLength = maxLength,
            appLockService = appLockService,
            activity = activity,
            showBiometricButton = showBiometricButton,
            onBiometricAuth = onBiometricAuth
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalAnimationApi::class)
@Composable
fun PasswordIndicators(
    passwordLength: Int,
    maxLength: Int
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(vertical = 16.dp)
    ) {
        repeat(maxLength) { index ->
            val filled = index < passwordLength
            val isNext = index == passwordLength && index < maxLength

            val indicatorState = remember(filled, isNext) {
                when {
                    filled -> "filled"
                    isNext -> "next"
                    else -> "empty"
                }
            }

            val scale by animateFloatAsState(
                targetValue = if (filled) 1.2f else if (isNext) 1.1f else 1.0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioHighBouncy,
                    stiffness = Spring.StiffnessHigh
                ),
                label = "indicatorScale"
            )

            AnimatedContent(
                targetState = indicatorState,
                transitionSpec = {
                    scaleIn(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessHigh
                        )
                    ) togetherWith scaleOut(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessHigh
                        )
                    )

                },
                label = "indicatorAnimation"
            ) { state ->
                val shape = when (state) {
                    "filled" -> shapes[index % shapes.size].toShape()
                    "next" -> MaterialShapes.Diamond.toShape()
                    else -> MaterialShapes.Circle.toShape()
                }

                val color = when (state) {
                    "filled" -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }

                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .size(24.dp)
                        .background(color = color, shape = shape)
                )
            }
        }
    }
}

@Composable
fun KeypadSection(
    passwordState: MutableState<String>,
    maxLength: Int,
    appLockService: AppLockService?,
    activity: FragmentActivity,
    showBiometricButton: Boolean,
    onBiometricAuth: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(bottom = 48.dp)
    ) {
        // Number rows
        KeypadRow(
            keys = listOf("1", "2", "3"),
            onKeyClick = { key -> addDigitToPassword(passwordState, key, maxLength) }
        )

        KeypadRow(
            keys = listOf("4", "5", "6"),
            onKeyClick = { key -> addDigitToPassword(passwordState, key, maxLength) }
        )

        KeypadRow(
            keys = listOf("7", "8", "9"),
            onKeyClick = { key -> addDigitToPassword(passwordState, key, maxLength) }
        )

        // Special buttons row (backspace, 0, proceed)
        KeypadRow(
            keys = listOf("backspace", "0", "proceed"),
            icons = listOf(
                Backspace,
                null,
                Icons.AutoMirrored.Rounded.KeyboardArrowRight
            ),
            onKeyClick = { key ->
                handleKeypadSpecialButton(
                    key = key,
                    passwordState = passwordState,
                    maxLength = maxLength,
                    appLockService = appLockService,
                    activity = activity
                )
            }
        )

        // Biometric button
        if (showBiometricButton) {
            Spacer(modifier = Modifier.height(8.dp))
            ElevatedButton(
                onClick = onBiometricAuth,
                modifier = Modifier.padding(8.dp),
                shape = CircleShape,
                content = {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "Biometric Authentication",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            )
        }
    }
}

private fun addDigitToPassword(
    passwordState: MutableState<String>,
    digit: String,
    maxLength: Int
) {
    if (passwordState.value.length < maxLength) {
        passwordState.value += digit
    }
}

private fun handleKeypadSpecialButton(
    key: String,
    passwordState: MutableState<String>,
    maxLength: Int,
    appLockService: AppLockService?,
    activity: FragmentActivity
) {
    when (key) {
        "0" -> addDigitToPassword(passwordState, key, maxLength)
        "backspace" -> {
            if (passwordState.value.isNotEmpty()) {
                passwordState.value = passwordState.value.dropLast(1)
            }
        }

        "proceed" -> {
            if (passwordState.value.length == maxLength) {
                appLockService?.let {
                    if (it.validatePassword(passwordState.value)) {
                        val packageToUnlock = AppLockService.currentLockedPackage ?: ""
                        it.unlockApp(packageToUnlock)
                        try {
                            // Get the launcher activity for the package
                            val launchIntent =
                                activity.packageManager.getLaunchIntentForPackage(packageToUnlock)

                            if (launchIntent != null) {
                                // Add flags to properly launch the app
                                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                activity.startActivity(launchIntent)
                                Log.d(
                                    "PasswordOverlayScreen",
                                    "Launched intent for package: $packageToUnlock"
                                )
                            } else {
                                Log.w(
                                    "PasswordOverlayScreen",
                                    "No launch intent found for package: $packageToUnlock"
                                )
                            }
                        } catch (e: Exception) {
                            Log.e("PasswordOverlayScreen", "Error launching app: ${e.message}")
                        }
                    } else {
                        passwordState.value = ""
                    }
                } ?: run {
                    passwordState.value = ""
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KeypadRow(
    keys: List<String>,
    icons: List<ImageVector?> = emptyList(),
    onKeyClick: (String) -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        keys.forEach { key ->
            val interactionSource = remember { MutableInteractionSource() }

            ElevatedButton(
                onClick = {
                    vibrate(context, 50)
                    onKeyClick(key)
                },
                modifier = Modifier
                    .padding(8.dp)
                    .weight(1f),
                shape = CircleShape,
                interactionSource = interactionSource,
                content = {
                    if (icons.isNotEmpty() && icons[keys.indexOf(key)] != null) {
                        Icon(
                            imageVector = icons[keys.indexOf(key)]!!,
                            contentDescription = key,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        Text(
                            text = key,
                            style = MaterialTheme.typography.displaySmallEmphasized,
                        )
                    }
                }
            )
        }
    }
}
