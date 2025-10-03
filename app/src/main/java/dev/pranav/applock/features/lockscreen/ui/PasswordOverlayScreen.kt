package dev.pranav.applock.features.lockscreen.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.addCallback
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import dev.pranav.applock.R
import dev.pranav.applock.core.ui.shapes
import dev.pranav.applock.core.utils.appLockRepository
import dev.pranav.applock.core.utils.vibrate
import dev.pranav.applock.data.repository.AppLockRepository
import dev.pranav.applock.services.AppLockManager
import dev.pranav.applock.ui.icons.Backspace
import dev.pranav.applock.ui.icons.Fingerprint
import dev.pranav.applock.ui.theme.AppLockTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.Executor


class PasswordOverlayActivity : FragmentActivity() {
    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo
    private lateinit var appLockRepository: AppLockRepository
    internal var lockedPackageNameFromIntent: String? = null
    internal var triggeringPackageNameFromIntent: String? = null

    private var isBiometricPromptShowingLocal = false
    private var movedToBackground = false
    private var appName: String = ""

    private val TAG = "PasswordOverlayActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lockedPackageNameFromIntent = intent.getStringExtra("locked_package")
        triggeringPackageNameFromIntent = intent.getStringExtra("triggering_package")
        if (lockedPackageNameFromIntent == null) {
            Log.e(TAG, "No locked_package name provided in intent. Finishing.")
            finishAffinity()
            return
        }

        enableEdgeToEdge()

        appLockRepository = AppLockRepository(applicationContext)

        onBackPressedDispatcher.addCallback(this) {
            // Prevent back navigation to maintain security
        }

        setupWindow()
        loadAppNameAndSetupUI()
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        setupBiometricPromptInternal()
    }

    override fun onPostResume() {
        super.onPostResume()
        setupBiometricPromptInternal()
        if (appLockRepository.isBiometricAuthEnabled()) {
            triggerBiometricPrompt()
        }
    }

    private fun setupWindow() {
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        val layoutParams = window.attributes
        layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        if (appLockRepository.shouldUseMaxBrightness()) {
            layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        }
        window.attributes = layoutParams
    }

    private fun loadAppNameAndSetupUI() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                appName = packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(lockedPackageNameFromIntent!!, 0)
                ).toString()
            } catch (e: Exception) {
                Log.e(TAG, "Error loading app name: ${e.message}")
                appName = getString(R.string.default_app_name)
            }
        }
        setupUI()
    }

    private fun setupUI() {
        val onPinAttemptCallback = { pin: String ->
            val isValid = appLockRepository.validatePassword(pin)
            if (isValid) {
                lockedPackageNameFromIntent?.let { pkgName ->
                    AppLockManager.unlockApp(pkgName)

                    when (appLockRepository.getUnlockBehavior()) {
                        0 -> {
                            finishAndRemoveTask()
                        }

                        1 -> {
                            val intent = packageManager.getLaunchIntentForPackage(pkgName)
                            if (intent != null) {
                                intent.addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK or
                                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                                )
                                startActivity(intent)
                            } else {
                                Log.e(TAG, "No launch intent found for package: $pkgName")
                            }
                            finishAffinity()
                        }
                    }
                }
            }
            isValid
        }

        setContent {
            AppLockTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentColor = MaterialTheme.colorScheme.primaryContainer
                ) { innerPadding ->
                    PasswordOverlayScreen(
                        modifier = Modifier.padding(innerPadding),
                        showBiometricButton = appLockRepository.isBiometricAuthEnabled(),
                        fromMainActivity = false,
                        onBiometricAuth = { triggerBiometricPrompt() },
                        onAuthSuccess = {},
                        lockedAppName = appName,
                        triggeringPackageName = triggeringPackageNameFromIntent,
                        onPinAttempt = onPinAttemptCallback
                    )
                }
            }
        }
    }

    private fun setupBiometricPromptInternal() {
        executor = ContextCompat.getMainExecutor(this)
        biometricPrompt =
            BiometricPrompt(this@PasswordOverlayActivity, executor, authenticationCallbackInternal)

        val appNameForPrompt = appName.ifEmpty { getString(R.string.this_app) }
        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.unlock_app_title, appNameForPrompt))
            .setSubtitle(getString(R.string.confirm_biometric_subtitle))
            .setNegativeButtonText(getString(R.string.use_pin_button))
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.BIOMETRIC_STRONG
            )
            .setConfirmationRequired(false)
            .build()
    }

    private val authenticationCallbackInternal =
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                isBiometricPromptShowingLocal = false
                AppLockManager.reportBiometricAuthFinished()
                Log.w(TAG, "Authentication error: $errString ($errorCode)")
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                isBiometricPromptShowingLocal = false
                lockedPackageNameFromIntent?.let { pkgName ->
                    AppLockManager.temporarilyUnlockAppWithBiometrics(pkgName)
                    val intent = packageManager.getLaunchIntentForPackage(pkgName)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                    } else {
                        Log.e(TAG, "No launch intent found for package: $pkgName")
                    }
                }
                finishAndRemoveTask()
            }
        }

    override fun onResume() {
        super.onResume()
        movedToBackground = false
        AppLockManager.isLockScreenShown.set(true) // Set to true when activity is visible
        lifecycleScope.launch {
            applyUserPreferences()
        }
    }

    private fun applyUserPreferences() {
        if (appLockRepository.shouldUseMaxBrightness()) {
            window.attributes = window.attributes.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
            }
            if (window.decorView.isAttachedToWindow) {
                windowManager.updateViewLayout(window.decorView, window.attributes)
            }
        }
    }

    fun triggerBiometricPrompt() {
        if (appLockRepository.isBiometricAuthEnabled()) {
            AppLockManager.reportBiometricAuthStarted()
            isBiometricPromptShowingLocal = true
            try {
                biometricPrompt.authenticate(promptInfo)
            } catch (e: Exception) {
                Log.e(TAG, "Error calling biometricPrompt.authenticate: ${e.message}", e)
                isBiometricPromptShowingLocal = false
                AppLockManager.reportBiometricAuthFinished()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        movedToBackground = true
        AppLockManager.isLockScreenShown.set(false)
        if (!isFinishing && !isDestroyed) {
            AppLockManager.reportBiometricAuthFinished()
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        AppLockManager.isLockScreenShown.set(false)
        AppLockManager.reportBiometricAuthFinished()
        Log.d(TAG, "PasswordOverlayActivity onDestroy for $lockedPackageNameFromIntent")
    }
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalAnimationApi::class)
@Composable
fun PasswordOverlayScreen(
    modifier: Modifier = Modifier,
    showBiometricButton: Boolean = false,
    fromMainActivity: Boolean = false,
    onBiometricAuth: () -> Unit = {},
    onAuthSuccess: () -> Unit,
    lockedAppName: String? = null,
    triggeringPackageName: String? = null,
    onPinAttempt: ((pin: String) -> Boolean)? = null
) {
    val appLockRepository = LocalContext.current.appLockRepository()
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        val passwordState = remember { mutableStateOf("") }
        var showError by remember { mutableStateOf(false) }
        val minLength = 4

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = if (fromMainActivity) 40.dp else 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = if (!fromMainActivity && !lockedAppName.isNullOrEmpty())
                    lockedAppName
                else
                    stringResource(R.string.enter_password_to_continue),
                style = if (!fromMainActivity && !lockedAppName.isNullOrEmpty())
                    MaterialTheme.typography.titleLargeEmphasized
                else
                    MaterialTheme.typography.headlineMediumEmphasized,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            PasswordIndicators(
                passwordLength = passwordState.value.length,
            )

            if (showError) {
                Text(
                    text = stringResource(R.string.incorrect_pin_try_again),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            KeypadSection(
                passwordState = passwordState,
                minLength = minLength,
                showBiometricButton = showBiometricButton,
                fromMainActivity = fromMainActivity,
                onBiometricAuth = onBiometricAuth,
                onAuthSuccess = onAuthSuccess,
                onPinAttempt = onPinAttempt,
                onPasswordChange = {
                    showError = false

                    if (appLockRepository.isAutoUnlockEnabled()) {
                        onPinAttempt?.invoke(passwordState.value)
                    }
                },
                onPinIncorrect = { showError = true }
            )

            if (!fromMainActivity && !triggeringPackageName.isNullOrEmpty()) {
                Text(
                    text = triggeringPackageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }
    }

    if (fromMainActivity) {
        BackHandler {}
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalAnimationApi::class)
@Composable
fun PasswordIndicators(
    passwordLength: Int
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(vertical = 16.dp)
    ) {
        repeat(passwordLength) { index ->
            key("digit_$index") {
                val isNewest = index == passwordLength - 1
                var animStarted by remember { mutableStateOf(false) }

                LaunchedEffect(isNewest) {
                    if (isNewest) {
                        animStarted = true
                    }
                }

                val animationProgress by animateFloatAsState(
                    targetValue = if (animStarted && isNewest) 1f else if (isNewest) 0f else 1f,
                    animationSpec = tween(
                        durationMillis = 600,
                        easing = FastOutSlowInEasing
                    ),
                    label = "indicatorProgress"
                )

                val scale = when {
                    isNewest && animationProgress < 0.6f -> 1.1f + (1f - animationProgress) * 0.4f
                    isNewest && animationProgress < 0.9f -> 1.1f + (1f - animationProgress) * 0.2f
                    else -> 1f
                }

                val shape = when {
                    isNewest -> shapes[index % shapes.size].toShape()
                    else -> CircleShape
                }

                val color = when {
                    isNewest && animationProgress < 0.6f -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.primary
                }

                val collapseProgress = if (isNewest && animationProgress > 0.6f) {
                    ((animationProgress - 0.6f) / 0.4f).coerceIn(0f, 1f)
                } else {
                    0f
                }

                val originalShapeScale = 1f - collapseProgress

                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .size(22.dp)
                ) {
                    if (collapseProgress > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(color = color, shape = CircleShape)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = originalShapeScale
                                scaleY = originalShapeScale
                            }
                            .background(color = color, shape = shape)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KeypadSection(
    passwordState: MutableState<String>,
    minLength: Int,
    showBiometricButton: Boolean,
    fromMainActivity: Boolean = false,
    onBiometricAuth: () -> Unit,
    onAuthSuccess: () -> Unit,
    onPinAttempt: ((pin: String) -> Boolean)? = null,
    onPasswordChange: () -> Unit,
    onPinIncorrect: () -> Unit
) {
    val context = LocalContext.current

    val onDigitKeyClick = remember(passwordState, minLength, onPasswordChange) {
        { key: String ->
            addDigitToPassword(
                passwordState,
                key,
                onPasswordChange
            )
        }
    }

    val disableHaptics = context.appLockRepository().shouldDisableHaptics()

    val onSpecialKeyClick = remember(
        passwordState,
        minLength,
        fromMainActivity,
        onAuthSuccess,
        onPinAttempt,
        context,
        onPasswordChange,
        onPinIncorrect
    ) {
        { key: String ->
            handleKeypadSpecialButtonLogic(
                key = key,
                passwordState = passwordState,
                minLength = minLength,
                fromMainActivity = fromMainActivity,
                onAuthSuccess = onAuthSuccess,
                onPinAttempt = onPinAttempt,
                context = context,
                onPasswordChange = onPasswordChange,
                onPinIncorrect = onPinIncorrect
            )
        }
    }

    val screenWidth = LocalConfiguration.current.screenWidthDp.dp

    Column(
        verticalArrangement = Arrangement.spacedBy(screenWidth / 24),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
    ) {
        KeypadRow(
            disableHaptics = disableHaptics,
            keys = listOf("1", "2", "3"),
            onKeyClick = onDigitKeyClick
        )
        KeypadRow(
            disableHaptics = disableHaptics,
            keys = listOf("4", "5", "6"),
            onKeyClick = onDigitKeyClick
        )
        KeypadRow(
            disableHaptics = disableHaptics,
            keys = listOf("7", "8", "9"),
            onKeyClick = onDigitKeyClick
        )
        KeypadRow(
            disableHaptics = disableHaptics,
            keys = listOf("backspace", "0", "proceed"),
            icons = listOf(Backspace, null, Icons.AutoMirrored.Rounded.KeyboardArrowRight),
            onKeyClick = onSpecialKeyClick
        )
        if (showBiometricButton) {
            Spacer(modifier = Modifier.height(8.dp))
            FilledTonalIconButton(
                onClick = onBiometricAuth,
                modifier = Modifier
                    .size(52.dp),
                shape = RoundedCornerShape(40),
            ) {
                Icon(
                    imageVector = Fingerprint,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    contentDescription = stringResource(R.string.biometric_authentication_cd),
                    tint = MaterialTheme.colorScheme.surfaceTint
                )
            }
        }
    }
}

private fun addDigitToPassword(
    passwordState: MutableState<String>,
    digit: String,
    onPasswordChange: () -> Unit
) {
    passwordState.value += digit
    onPasswordChange()
}

private fun handleKeypadSpecialButtonLogic(
    key: String,
    passwordState: MutableState<String>,
    minLength: Int,
    fromMainActivity: Boolean,
    onAuthSuccess: () -> Unit,
    onPinAttempt: ((pin: String) -> Boolean)?,
    context: Context,
    onPasswordChange: () -> Unit,
    onPinIncorrect: () -> Unit
) {
    val appLockRepository = context.appLockRepository()

    when (key) {
        "0" -> addDigitToPassword(passwordState, key, onPasswordChange)
        "backspace" -> {
            if (passwordState.value.isNotEmpty()) {
                passwordState.value = passwordState.value.dropLast(1)
                onPasswordChange()
            }
        }

        "proceed" -> {
            if (passwordState.value.length < minLength) {
                if (!appLockRepository.shouldDisableHaptics()) {
                    vibrate(context, 100)
                }
                passwordState.value = ""
                return
            }
            if (passwordState.value.length >= minLength) {
                if (fromMainActivity) {
                    if (appLockRepository.validatePassword(passwordState.value)) {
                        onAuthSuccess()
                    } else {
                        passwordState.value = ""
                        if (!appLockRepository.shouldDisableHaptics()) {
                            vibrate(context, 100)
                        }
                        onPinIncorrect()
                    }
                } else {
                    onPinAttempt?.let { attempt ->
                        val pinWasCorrectAndProcessed = attempt(passwordState.value)
                        if (!pinWasCorrectAndProcessed) {
                            passwordState.value = ""
                            if (!appLockRepository.shouldDisableHaptics()) {
                                vibrate(context, 100)
                            }
                        }
                    } ?: run {
                        Log.e(
                            "PasswordOverlayScreen",
                            "onPinAttempt callback is null for app unlock path."
                        )
                        passwordState.value = ""
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KeypadRow(
    disableHaptics: Boolean = false,
    keys: List<String>,
    icons: List<ImageVector?> = emptyList(),
    onKeyClick: (String) -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val numKeys = keys.size.toFloat()

    val totalSpacing = screenWidth / 3
    val maxButtonDiameter = (screenWidth - totalSpacing) / numKeys
    val buttonSize = maxButtonDiameter.coerceIn(minimumValue = 40.dp, maximumValue = 100.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = (totalSpacing / 6)),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        keys.forEachIndexed { index, key ->
            val interactionSource = remember { MutableInteractionSource() }

            val isPressed by interactionSource.collectIsPressedAsState()

            val targetColor = if (isPressed) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                if (icons.isNotEmpty() && index < icons.size && icons[index] != null) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceBright
            }

            val animatedContainerColor by animateColorAsState(
                targetValue = targetColor,
                animationSpec = tween(durationMillis = 150),
                label = "ButtonContainerColorAnimation"
            )

            val normalTextSize = MaterialTheme.typography.headlineLargeEmphasized.fontSize

            val targetFontSize = if (isPressed) normalTextSize * 1.2f else normalTextSize

            val animatedFontSize by animateFloatAsState(
                targetValue = targetFontSize.value,
                animationSpec = tween(durationMillis = 100),
                label = "ButtonTextSizeAnimation"
            )

            FilledTonalButton(
                onClick = {
                    if (!disableHaptics) vibrate(context, 100)
                    onKeyClick(key)
                },
                modifier = Modifier
                    .size(buttonSize),

                interactionSource = interactionSource,

                shapes = ButtonShapes(
                    shape = CircleShape,
                    pressedShape = RoundedCornerShape(25),
                ),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = animatedContainerColor,
                ),
                elevation = ButtonDefaults.filledTonalButtonElevation()
            ) {
                val contentColor = MaterialTheme.colorScheme.onPrimaryContainer

                if (icons.isNotEmpty() && index < icons.size && icons[index] != null) {
                    Icon(
                        imageVector = icons[index]!!,
                        contentDescription = key,
                        modifier = Modifier
                            .size(buttonSize * 0.45f),
                        tint = contentColor
                    )
                } else {
                    Text(
                        text = key,
                        style = MaterialTheme.typography.headlineLargeEmphasized.copy(
                            fontSize = animatedFontSize.sp
                        ),
                    )
                }
            }
        }
    }
}
