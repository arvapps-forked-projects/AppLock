package dev.pranav.applock.features.setpassword.ui

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import dev.pranav.applock.AppLockApplication
import dev.pranav.applock.core.navigation.Screen
import dev.pranav.applock.features.lockscreen.ui.KeypadRow
import dev.pranav.applock.features.lockscreen.ui.PasswordIndicators
import dev.pranav.applock.ui.icons.Backspace

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
fun SetPasswordScreen(
    navController: NavController,
    isFirstTimeSetup: Boolean
) {
    var passwordState by remember { mutableStateOf("") }
    var confirmPasswordState by remember { mutableStateOf("") }
    var isConfirmationMode by remember { mutableStateOf(false) }

    var isVerifyOldPasswordMode by remember { mutableStateOf(!isFirstTimeSetup) }

    var showMismatchError by remember { mutableStateOf(false) }
    var showLengthError by remember { mutableStateOf(false) }
    var showInvalidOldPasswordError by remember { mutableStateOf(false) }
    val minLength = 4

    val context = LocalContext.current
    val activity = LocalActivity.current as? ComponentActivity
    val appLockRepository = remember {
        (context.applicationContext as? AppLockApplication)?.appLockRepository
    }

    BackHandler {
        if (isFirstTimeSetup) {
            Toast.makeText(context, "Please set a PIN to continue", Toast.LENGTH_SHORT).show()
        } else {
            if (navController.previousBackStackEntry != null) {
                navController.popBackStack()
            } else {
                activity?.finish()
            }
        }
    }

    val fragmentActivity = LocalActivity.current as? androidx.fragment.app.FragmentActivity

    fun launchDeviceCredentialAuth() {
        if (fragmentActivity == null) return
        val executor = ContextCompat.getMainExecutor(context)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Authenticate to reset PIN")
            .setSubtitle("Use your device PIN, pattern, or password")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        val biometricPrompt = BiometricPrompt(
            fragmentActivity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    isVerifyOldPasswordMode = false
                    passwordState = ""
                    confirmPasswordState = ""
                    showInvalidOldPasswordError = false
                }

            })
        biometricPrompt.authenticate(promptInfo)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when {
                            isFirstTimeSetup -> "Welcome to App Lock"
                            isVerifyOldPasswordMode -> "Enter Current PIN"
                            isConfirmationMode -> "Confirm PIN"
                            else -> "Set New PIN"
                        },
                        style = MaterialTheme.typography.titleLargeEmphasized,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            if (isFirstTimeSetup && !isConfirmationMode && !isVerifyOldPasswordMode) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Secure Your Apps",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Please create a PIN to protect your locked apps. This PIN will be required whenever you try to access a locked app.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = when {
                        isVerifyOldPasswordMode -> "Enter your current PIN"
                        isConfirmationMode -> "Confirm your new PIN"
                        else -> "Create a new PIN"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
                    tooltip = {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(
                                text = when {
                                    isVerifyOldPasswordMode -> "Enter your current PIN to continue"
                                    isConfirmationMode -> "Please enter the same PIN again to confirm"
                                    else -> "Create a PIN with at least 4 digits to protect your apps"
                                },
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    },
                    state = rememberTooltipState()
                ) {
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Information",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (showMismatchError) {
                Text(
                    text = "PINs don't match. Try again.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(8.dp)
                )
            }
            if (showLengthError) {
                Text(
                    text = "PIN must be at least 4 digits",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(8.dp)
                )
            }
            if (showInvalidOldPasswordError) {
                Text(
                    text = "Incorrect PIN. Please try again.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(8.dp)
                )
            }

            val currentPassword = when {
                isVerifyOldPasswordMode -> passwordState
                isConfirmationMode -> confirmPasswordState
                else -> passwordState
            }

            PasswordIndicators(
                passwordLength = currentPassword.length
            )

            Text(
                text = when {
                    isVerifyOldPasswordMode -> "Enter your current PIN"
                    isConfirmationMode -> "Re-enter your new PIN to confirm"
                    else -> "Enter a PIN with at least 4 digits"
                },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.alpha(0.8f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(1f))

            if (isVerifyOldPasswordMode) {
                TextButton(onClick = { launchDeviceCredentialAuth() }) {
                    Text("Reset using device password")
                }
            }

            if (isVerifyOldPasswordMode || isConfirmationMode) {
                TextButton(
                    onClick = {
                        if (isVerifyOldPasswordMode) {
                            if (navController.previousBackStackEntry != null) {
                                navController.popBackStack()
                            } else {
                                activity?.finish()
                            }
                        } else {
                            isConfirmationMode = false
                            if (!isFirstTimeSetup) {
                                isVerifyOldPasswordMode = true
                            }
                        }
                        passwordState = ""
                        confirmPasswordState = ""
                        showMismatchError = false
                        showLengthError = false
                        showInvalidOldPasswordError = false
                    },
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Text(if (isVerifyOldPasswordMode) "Cancel" else "Start Over")
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val onKeyClick: (String) -> Unit = { key ->
                    val currentActivePassword = when {
                        isVerifyOldPasswordMode -> passwordState
                        isConfirmationMode -> confirmPasswordState
                        else -> passwordState
                    }
                    val updatePassword: (String) -> Unit = when {
                        isVerifyOldPasswordMode -> { newPass -> passwordState = newPass }
                        isConfirmationMode -> { newPass -> confirmPasswordState = newPass }
                        else -> { newPass -> passwordState = newPass }
                    }

                    when (key) {
                        "0", "1", "2", "3", "4", "5", "6", "7", "8", "9" -> {
                            updatePassword(currentActivePassword + key)
                        }

                        "backspace" -> {
                            if (currentActivePassword.isNotEmpty()) {
                                updatePassword(currentActivePassword.dropLast(1))
                            }
                            showMismatchError = false
                            showLengthError = false
                            showInvalidOldPasswordError = false
                        }

                        "proceed" -> {
                            if (currentActivePassword.length >= minLength) {
                                when {
                                    isVerifyOldPasswordMode -> {
                                        if (appLockRepository!!.validatePassword(passwordState)) {
                                            isVerifyOldPasswordMode = false
                                            passwordState = ""
                                            showInvalidOldPasswordError = false
                                        } else {
                                            showInvalidOldPasswordError = true
                                            passwordState = ""
                                        }
                                    }

                                    !isConfirmationMode -> {
                                        isConfirmationMode = true
                                        showLengthError = false
                                    }

                                    else -> {
                                        if (passwordState == confirmPasswordState) {
                                            appLockRepository?.setPassword(passwordState)
                                            Toast.makeText(
                                                context,
                                                "Password set successfully",
                                                Toast.LENGTH_SHORT
                                            ).show()

                                            navController.navigate(Screen.Main.route) {
                                                popUpTo(Screen.SetPassword.route) {
                                                    inclusive = true
                                                }
                                                if (isFirstTimeSetup) {
                                                    popUpTo(Screen.AppIntro.route) {
                                                        inclusive = true
                                                    }
                                                }
                                            }
                                        } else {
                                            showMismatchError = true
                                            confirmPasswordState = ""
                                        }
                                    }
                                }
                            } else {
                                showLengthError = true
                            }
                        }
                    }
                }

                KeypadRow(
                    keys = listOf("1", "2", "3"),
                    onKeyClick = onKeyClick
                )
                KeypadRow(
                    keys = listOf("4", "5", "6"),
                    onKeyClick = onKeyClick
                )
                KeypadRow(
                    keys = listOf("7", "8", "9"),
                    onKeyClick = onKeyClick
                )
                KeypadRow(
                    keys = listOf("backspace", "0", "proceed"),
                    icons = listOf(
                        Backspace,
                        null,
                        if (isConfirmationMode || isVerifyOldPasswordMode) Icons.Default.Check else Icons.AutoMirrored.Rounded.KeyboardArrowRight
                    ),
                    onKeyClick = onKeyClick
                )
            }
        }
    }
}
