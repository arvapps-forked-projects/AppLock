package dev.pranav.applock.features.lockscreen.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dev.pranav.applock.AppLockApplication
import dev.pranav.applock.core.utils.vibrate
import dev.pranav.applock.data.repository.AppLockRepository
import dev.pranav.applock.services.AppLockService
import dev.pranav.applock.ui.icons.Backspace
import dev.pranav.applock.ui.icons.Fingerprint
import dev.pranav.applock.ui.theme.AppLockTheme
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

class PasswordOverlayActivity : FragmentActivity() {
    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo
    private lateinit var appLockRepository: AppLockRepository
    private var appLockService: AppLockService? = null
    internal var lockedPackageNameFromIntent: String? = null

    private var isBiometricPromptShowingLocal = false
    private var movedToBackground = false
    private val activityHandler = Handler(Looper.getMainLooper())
    private var finishActivityRunnable: Runnable? = null

    companion object {
        private const val TAG = "PasswordOverlay"
        private var activeInstance: PasswordOverlayActivity? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        activeInstance?.let {
            if (!it.isFinishing && it != this) it.finishAndRemoveTaskSafely()
        }
        activeInstance = this

        appLockRepository = AppLockRepository(applicationContext)
        appLockService = (applicationContext as? AppLockApplication)?.appLockServiceInstance

        lockedPackageNameFromIntent = intent.getStringExtra("locked_package")
        if (lockedPackageNameFromIntent == null) {
            Log.e(TAG, "No locked_package name provided in intent. Finishing.")
            finishAndRemoveTaskSafely()
            return
        }

        enableEdgeToEdge()
        setupWindowFlags()
        setupBiometricPromptInternal()
        setupBackPressHandler()

        shapes.shuffle()

        val onPinAttemptCallback = { pin: String ->
            val isValid = appLockService?.validatePassword(pin) == true
            if (isValid) {
                lockedPackageNameFromIntent?.let { pkgName ->
                    Log.d(TAG, "PIN correct for $pkgName via callback. Unlocking.")
                    appLockService?.unlockApp(pkgName)
                    launchAppAndFinish(pkgName)
                }
            }
            isValid
        }

        setContent {
            AppLockTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PasswordOverlayScreen(
                        modifier = Modifier.padding(innerPadding),
                        showBiometricButton = appLockRepository.isBiometricAuthEnabled(),
                        fromMainActivity = false,
                        onBiometricAuth = { triggerBiometricPromptIfNeeded() },
                        onAuthSuccess = {
                            Log.d(
                                TAG,
                                "Generic onAuthSuccess from Activity - likely unused in this context."
                            )
                        },
                        lockedAppName = packageManager.getApplicationLabel(
                            packageManager.getApplicationInfo(
                                lockedPackageNameFromIntent!!, 0
                            )
                        ).toString(),
                        onPinAttempt = onPinAttemptCallback
                    )
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun setupWindowFlags() {
        // Remove FLAG_SHOW_WHEN_LOCKED and FLAG_DISMISS_KEYGUARD to prevent showing over system lock
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SECURE
        )
        val layoutParams = window.attributes
        layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        window.attributes = layoutParams
    }

    @Suppress("DEPRECATION")
    private fun setupReapplicableWindowFlags() {
        // Remove FLAG_SHOW_WHEN_LOCKED and FLAG_DISMISS_KEYGUARD to prevent showing over system lock
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SECURE
        )
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this) {
            finishAndRemoveTaskSafely()
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
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
        if (appLockRepository.isBiometricAuthEnabled() && !isBiometricPromptShowingLocal && appLockService != null) {
            triggerBiometricPromptIfNeeded()
        }
    }

    private fun triggerBiometricPromptIfNeeded() {
        if (!isBiometricPromptShowingLocal && appLockRepository.isBiometricAuthEnabled() && appLockService != null) {
            val biometricManager = BiometricManager.from(this)
            if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS) {
                appLockService?.reportBiometricAuthStarted()
                isBiometricPromptShowingLocal = true
                try {
                    biometricPrompt.authenticate(promptInfo)
                } catch (e: Exception) {
                    Log.e(TAG, "Error calling biometricPrompt.authenticate: ${e.message}", e)
                    isBiometricPromptShowingLocal = false
                    appLockService?.reportBiometricAuthFinished()
                }
            } else {
                Log.w(TAG, "Biometric authentication not available on this device.")
            }
        }
    }

    private fun setupBiometricPromptInternal() {
        executor = ContextCompat.getMainExecutor(this)
        biometricPrompt =
            BiometricPrompt(this, executor, authenticationCallbackInternal)

        val appName =
            lockedPackageNameFromIntent?.let { getAppNameFromPackageManager(it) } ?: "this app"
        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock $appName")
            .setSubtitle("Confirm biometric to continue")
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setConfirmationRequired(false)
            .build()
    }

    private val authenticationCallbackInternal =
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                isBiometricPromptShowingLocal = false
                appLockService?.reportBiometricAuthFinished()
                Log.w(TAG, "Authentication error: $errString ($errorCode)")
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                isBiometricPromptShowingLocal = false
                lockedPackageNameFromIntent?.let { pkgName ->
                    appLockService?.temporarilyUnlockAppWithBiometrics(pkgName)
                    launchAppAndFinish(pkgName)
                } ?: run {
                    Log.e(TAG, "lockedPackageNameFromIntent is null on biometric success")
                    finishAndRemoveTaskSafely()
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Log.w(TAG, "Authentication failed (fingerprint not recognized)")
            }
        }


    private fun getAppNameFromPackageManager(packageName: String): String? {
        return try {
            val pm = applicationContext.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "App not found for package name $packageName: ${e.message}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting app name for $packageName: ${e.message}")
            null
        }
    }

    internal fun launchAppAndFinish(packageName: String) {
        Log.d(TAG, "Attempting to launch $packageName and finish overlay.")
        try {
            packageManager.getLaunchIntentForPackage(packageName)?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(it)
            } ?: Log.w(TAG, "No launch intent for $packageName")
        } catch (e: Exception) {
            Log.e(TAG, "Error launching app $packageName: ${e.message}")
        }
        finishAndRemoveTaskSafely()
    }

    internal fun finishAndRemoveTaskSafely() {
        if (!isFinishing && !isDestroyed) {
            Log.d(TAG, "finishAndRemoveTaskSafely called for $lockedPackageNameFromIntent")
            finishAndRemoveTask()
        }
    }

    override fun onResume() {
        super.onResume()
        movedToBackground = false
        finishActivityRunnable?.let { activityHandler.removeCallbacks(it) }
        setupReapplicableWindowFlags()
        applyUserPreferences()
    }

    @Suppress("DEPRECATION")
    override fun onPause() {
        super.onPause()
        movedToBackground = true
        finishActivityRunnable?.let { activityHandler.removeCallbacks(it) }
        finishActivityRunnable = Runnable {
            if (movedToBackground && !isFinishing && !isDestroyed) {
                Log.d(
                    TAG,
                    "Finishing activity due to onPause timeout for $lockedPackageNameFromIntent"
                )
                finishAndRemoveTaskSafely()
            }
        }
        activityHandler.postDelayed(finishActivityRunnable!!, 500)
    }

    override fun onStop() {
        super.onStop()
        finishActivityRunnable?.let { activityHandler.removeCallbacks(it) }
        if (movedToBackground && !isFinishing && !isDestroyed) {
            Log.d(
                TAG,
                "Finishing activity onStop because it was moved to background: $lockedPackageNameFromIntent"
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (activeInstance === this) activeInstance = null
        finishActivityRunnable?.let { activityHandler.removeCallbacks(it) }

        Log.d(TAG, "PasswordOverlayActivity onDestroy for $lockedPackageNameFromIntent")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val newLockedPackage = intent.getStringExtra("locked_package")
        Log.d(TAG, "onNewIntent. Current: $lockedPackageNameFromIntent, New: $newLockedPackage")

        if (newLockedPackage != null && newLockedPackage != lockedPackageNameFromIntent) {
            Log.d(TAG, "Overlay target changed to: $newLockedPackage")
            lockedPackageNameFromIntent = newLockedPackage

            val appName =
                lockedPackageNameFromIntent?.let { getAppNameFromPackageManager(it) } ?: "this app"
            promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock $appName")
                .setSubtitle("Confirm biometric to continue")
                .setNegativeButtonText("Use PIN")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setConfirmationRequired(false)
                .build()

            isBiometricPromptShowingLocal = false
        } else if (newLockedPackage != null) {
            Log.d(TAG, "Overlay refreshed for: $newLockedPackage. Re-applying preferences.")
        }

        if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            applyUserPreferences()
        }
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
fun PasswordOverlayScreen(
    modifier: Modifier = Modifier,
    showBiometricButton: Boolean = false,
    fromMainActivity: Boolean = false,
    onBiometricAuth: () -> Unit = {},
    onAuthSuccess: () -> Unit,
    lockedAppName: String? = null,
    onPinAttempt: ((pin: String) -> Boolean)? = null
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        val passwordState = remember { mutableStateOf("") }
        var showError by remember { mutableStateOf(false) }
        val maxLength = 6
        val context = LocalContext.current
        val appLockService =
            (context.applicationContext as? AppLockApplication)?.appLockServiceInstance

        val onPasswordChangeLambda = remember { { showError = false } }
        val onPinIncorrectLambda = remember { { showError = true } }

        val onPinAttemptForSection = remember(onPinAttempt) {
            { pin: String ->
                if (onPinAttempt == null) false
                else {
                    val result = onPinAttempt.invoke(pin)
                    showError = !result
                    result
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = if (fromMainActivity) 40.dp else 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            if (!fromMainActivity && !lockedAppName.isNullOrEmpty()) {
                Text(
                    text = "Unlock: $lockedAppName",
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    text = "Enter password to continue",
                    style = MaterialTheme.typography.headlineMediumEmphasized,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            PasswordIndicators(
                passwordLength = passwordState.value.length,
                maxLength = maxLength
            )

            if (showError) {
                Text(
                    text = "Incorrect PIN. Please try again.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            KeypadSection(
                passwordState = passwordState,
                maxLength = maxLength,
                appLockService = appLockService,
                showBiometricButton = showBiometricButton,
                fromMainActivity = fromMainActivity,
                onBiometricAuth = onBiometricAuth,
                onAuthSuccess = onAuthSuccess,
                onPinAttempt = onPinAttemptForSection,
                onPasswordChange = onPasswordChangeLambda,
                onPinIncorrect = onPinIncorrectLambda
            )
        }
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
                animationSpec = tween(
                    durationMillis = 100, // Further reduced duration
                    easing = FastOutSlowInEasing
                ),
                label = "indicatorScale"
            )

            AnimatedContent(
                targetState = indicatorState,
                transitionSpec = {
                    fadeIn(animationSpec = tween(durationMillis = 100)) togetherWith
                            fadeOut(animationSpec = tween(durationMillis = 100))
                },
                label = "indicatorStateAnimation"
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
                        .graphicsLayer { scaleX = scale; scaleY = scale }
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
    showBiometricButton: Boolean,
    fromMainActivity: Boolean = false,
    onBiometricAuth: () -> Unit,
    onAuthSuccess: () -> Unit,
    onPinAttempt: ((pin: String) -> Boolean)? = null,
    onPasswordChange: () -> Unit,
    onPinIncorrect: () -> Unit
) {
    val context = LocalContext.current

    val onDigitKeyClick = remember(passwordState, maxLength, onPasswordChange) {
        { key: String ->
            addDigitToPassword(
                passwordState,
                key,
                maxLength,
                onPasswordChange
            )
        }
    }

    val onSpecialKeyClick = remember(
        passwordState,
        maxLength,
        appLockService,
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
                maxLength = maxLength,
                appLockService = appLockService,
                fromMainActivity = fromMainActivity,
                onAuthSuccess = onAuthSuccess,
                onPinAttempt = onPinAttempt,
                contextForVibrate = context,
                onPasswordChange = onPasswordChange,
                onPinIncorrect = onPinIncorrect
            )
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
    ) {
        KeypadRow(
            keys = listOf("1", "2", "3"),
            onKeyClick = onDigitKeyClick
        )
        KeypadRow(
            keys = listOf("4", "5", "6"),
            onKeyClick = onDigitKeyClick
        )
        KeypadRow(
            keys = listOf("7", "8", "9"),
            onKeyClick = onDigitKeyClick
        )
        KeypadRow(
            keys = listOf("backspace", "0", "proceed"),
            icons = listOf(Backspace, null, Icons.AutoMirrored.Rounded.KeyboardArrowRight),
            onKeyClick = onSpecialKeyClick
        )
        if (showBiometricButton) {
            Spacer(modifier = Modifier.height(8.dp))
            ElevatedButton(
                onClick = onBiometricAuth,
                modifier = Modifier.padding(8.dp),
                shape = CircleShape,
            ) {
                Icon(
                    imageVector = Fingerprint,
                    contentDescription = "Biometric Authentication",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private fun addDigitToPassword(
    passwordState: MutableState<String>,
    digit: String,
    maxLength: Int,
    onPasswordChange: () -> Unit
) {
    if (passwordState.value.length < maxLength) {
        passwordState.value += digit
        onPasswordChange()
    }
}

private fun handleKeypadSpecialButtonLogic(
    key: String,
    passwordState: MutableState<String>,
    maxLength: Int,
    appLockService: AppLockService?,
    fromMainActivity: Boolean,
    onAuthSuccess: () -> Unit,
    onPinAttempt: ((pin: String) -> Boolean)?,
    contextForVibrate: Context,
    onPasswordChange: () -> Unit,
    onPinIncorrect: () -> Unit
) {

    when (key) {
        "0" -> addDigitToPassword(passwordState, key, maxLength, onPasswordChange)
        "backspace" -> {
            if (passwordState.value.isNotEmpty()) {
                passwordState.value = passwordState.value.dropLast(1)
                onPasswordChange()
            }
        }

        "proceed" -> {
            if (passwordState.value.length == maxLength) {
                if (fromMainActivity) {
                    appLockService?.let { service ->
                        if (service.validatePassword(passwordState.value)) {
                            onAuthSuccess()
                        } else {
                            passwordState.value = ""
                            vibrate(contextForVibrate, 100)
                            onPinIncorrect()
                        }
                    } ?: run {
                        passwordState.value = ""
                        onPinIncorrect()
                    }
                } else {
                    onPinAttempt?.let { attempt ->
                        val pinWasCorrectAndProcessed = attempt(passwordState.value)
                        if (!pinWasCorrectAndProcessed) {
                            passwordState.value = ""
                            vibrate(contextForVibrate, 100)
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
    keys: List<String>,
    icons: List<ImageVector?> = emptyList(),
    onKeyClick: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        keys.forEachIndexed { index, key ->
            val interactionSource = remember { MutableInteractionSource() }
            ElevatedButton(
                onClick = {
                    scope.launch {
                        vibrate(context, 50)
                    }
                    onKeyClick(key)
                },
                modifier = Modifier
                    .padding(8.dp)
                    .weight(1f),
                shape = CircleShape,
                interactionSource = interactionSource,
            ) {
                if (icons.isNotEmpty() && index < icons.size && icons[index] != null) {
                    Icon(
                        imageVector = icons[index]!!,
                        contentDescription = key,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = key,
                        style = MaterialTheme.typography.displaySmallEmphasized,
                    )
                }
            }
        }
    }
}
