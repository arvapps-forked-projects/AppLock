package dev.pranav.applock

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.pranav.applock.ui.Backspace
import dev.pranav.applock.ui.theme.AppLockTheme

class SetPasswordActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check if this is the first time setup
        val isFirstTimeSetup = intent.getBooleanExtra("FIRST_TIME_SETUP", false)

        onBackPressedDispatcher.addCallback {
            val isFirstTimeSetup = intent.getBooleanExtra("FIRST_TIME_SETUP", false)

            if (isFirstTimeSetup) {
                Toast.makeText(
                    this@SetPasswordActivity,
                    "Please set a PIN to continue",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                finish() // Allow back press to exit if not first time setup
            }
        }

        shapes.shuffle()

        setContent {
            AppLockTheme {
                SetPasswordScreen(
                    isFirstTimeSetup = isFirstTimeSetup,
                    onPasswordSet = { password ->
                        val appLockService =
                            (applicationContext as AppLockApplication).appLockServiceInstance
                        appLockService?.setPassword(password)
                        Toast.makeText(this, "Password set successfully", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                )
            }
        }
    }
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalAnimationApi::class
)
@Composable
fun SetPasswordScreen(
    isFirstTimeSetup: Boolean = false,
    onPasswordSet: (String) -> Unit
) {
    var passwordState by remember { mutableStateOf("") }
    var confirmPasswordState by remember { mutableStateOf("") }
    var isConfirmationMode by remember { mutableStateOf(false) }
    var showMismatchError by remember { mutableStateOf(false) }
    var showLengthError by remember { mutableStateOf(false) }
    val maxLength = 6

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = if (isFirstTimeSetup) "Welcome to App Lock" else if (isConfirmationMode) "Confirm PIN" else "Set PIN") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // First-time setup welcome message
            if (isFirstTimeSetup && !isConfirmationMode) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Secure Your Apps",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Please create a PIN to protect your locked apps. This PIN will be required whenever you try to access a locked app.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Title with tooltip
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (isConfirmationMode) "Confirm your PIN" else "Create a secure PIN",
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
                                text = if (isConfirmationMode)
                                    "Please enter the same PIN again to confirm"
                                else
                                    "Create a 6-digit PIN to protect your apps",
                                modifier = Modifier.padding(16.dp),
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

            Spacer(modifier = Modifier.height(8.dp))

            // Error messages
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
                    text = "PIN must be 6 digits",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(8.dp)
                )
            }

            // PIN dots indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(vertical = 24.dp)
            ) {
                val currentPassword =
                    if (isConfirmationMode) confirmPasswordState else passwordState

                repeat(maxLength) { index ->
                    val filled = index < currentPassword.length
                    val isNext = index == currentPassword.length && index < maxLength

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
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "indicatorScale"
                    )

                    AnimatedContent(
                        targetState = indicatorState, transitionSpec = {
                            fadeIn(animationSpec = tween(300)) togetherWith fadeOut(
                                animationSpec = tween(150)
                            )
                        }, label = "indicatorAnimation"
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
                                .background(
                                    color = color, shape = shape
                                )
                        )
                    }
                }
            }

            // Instruction text
            Text(
                text = if (isConfirmationMode) "Re-enter your PIN to confirm" else "Enter a 6-digit PIN",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.alpha(0.8f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(1f))

            // Keypad
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 48.dp)
            ) {
                KeypadRow(
                    keys = listOf("1", "2", "3"), onKeyClick = { key ->
                        handleKeyPress(
                            key,
                            isConfirmationMode,
                            maxLength,
                            passwordState,
                            confirmPasswordState
                        ) { newPassword, newConfirmPassword ->
                            passwordState = newPassword
                            confirmPasswordState = newConfirmPassword
                        }
                    }
                )

                KeypadRow(
                    keys = listOf("4", "5", "6"), onKeyClick = { key ->
                        handleKeyPress(
                            key,
                            isConfirmationMode,
                            maxLength,
                            passwordState,
                            confirmPasswordState
                        ) { newPassword, newConfirmPassword ->
                            passwordState = newPassword
                            confirmPasswordState = newConfirmPassword
                        }
                    }
                )

                KeypadRow(
                    keys = listOf("7", "8", "9"), onKeyClick = { key ->
                        handleKeyPress(
                            key,
                            isConfirmationMode,
                            maxLength,
                            passwordState,
                            confirmPasswordState
                        ) { newPassword, newConfirmPassword ->
                            passwordState = newPassword
                            confirmPasswordState = newConfirmPassword
                        }
                    }
                )

                KeypadRow(
                    keys = listOf("backspace", "0", "proceed"),
                    icons = listOf(
                        Backspace,
                        null,
                        if (isConfirmationMode) Icons.Default.Check else Icons.AutoMirrored.Rounded.KeyboardArrowRight
                    ),
                    onKeyClick = { key ->
                        when (key) {
                            "0" -> {
                                handleKeyPress(
                                    key,
                                    isConfirmationMode,
                                    maxLength,
                                    passwordState,
                                    confirmPasswordState
                                ) { newPassword, newConfirmPassword ->
                                    passwordState = newPassword
                                    confirmPasswordState = newConfirmPassword
                                }
                            }

                            "backspace" -> {
                                if (isConfirmationMode) {
                                    if (confirmPasswordState.isNotEmpty()) {
                                        confirmPasswordState = confirmPasswordState.dropLast(1)
                                    }
                                } else {
                                    if (passwordState.isNotEmpty()) {
                                        passwordState = passwordState.dropLast(1)
                                    }
                                }
                                showMismatchError = false
                                showLengthError = false
                            }

                            "proceed" -> {
                                if (!isConfirmationMode) {
                                    if (passwordState.length == maxLength) {
                                        isConfirmationMode = true
                                        showLengthError = false
                                    } else {
                                        showLengthError = true
                                    }
                                } else {
                                    if (confirmPasswordState.length == maxLength) {
                                        if (passwordState == confirmPasswordState) {
                                            // Password confirmed
                                            onPasswordSet(passwordState)
                                        } else {
                                            // Password mismatch
                                            showMismatchError = true
                                            confirmPasswordState = ""
                                        }
                                    } else {
                                        showLengthError = true
                                    }
                                }
                            }
                        }
                    }
                )
            }

            // Cancel button (only in confirmation mode)
            if (isConfirmationMode) {
                TextButton(
                    onClick = {
                        isConfirmationMode = false
                        confirmPasswordState = ""
                        showMismatchError = false
                        showLengthError = false
                    },
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Text("Start Over")
                }
            }
        }
    }
}

// Helper function to handle key presses
private fun handleKeyPress(
    key: String,
    isConfirmationMode: Boolean,
    maxLength: Int,
    passwordState: String,
    confirmPasswordState: String,
    updateStates: (String, String) -> Unit
) {
    if (key.length == 1 && key[0].isDigit()) {
        if (isConfirmationMode) {
            if (confirmPasswordState.length < maxLength) {
                updateStates(passwordState, confirmPasswordState + key)
            }
        } else {
            if (passwordState.length < maxLength) {
                updateStates(passwordState + key, confirmPasswordState)
            }
        }
    }
}
