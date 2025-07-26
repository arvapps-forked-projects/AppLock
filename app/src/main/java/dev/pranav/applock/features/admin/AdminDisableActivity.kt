package dev.pranav.applock.features.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.pranav.applock.core.broadcast.DeviceAdmin
import dev.pranav.applock.core.utils.appLockRepository
import dev.pranav.applock.data.repository.AppLockRepository
import dev.pranav.applock.features.lockscreen.ui.KeypadSection
import dev.pranav.applock.features.lockscreen.ui.PasswordIndicators
import dev.pranav.applock.ui.theme.AppLockTheme

class AdminDisableActivity : ComponentActivity() {
    private lateinit var appLockRepository: AppLockRepository
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var deviceAdminComponentName: ComponentName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        devicePolicyManager = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        deviceAdminComponentName = ComponentName(this, DeviceAdmin::class.java)

        appLockRepository = appLockRepository()

        // Set up back press callback to prevent admin disabling
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val deviceAdmin = DeviceAdmin()
                deviceAdmin.setPasswordVerified(this@AdminDisableActivity, false)
                finish()
            }
        })

        setContent {
            AppLockTheme {
                Scaffold { padding ->
                    AdminDisableScreen(
                        modifier = Modifier.padding(padding),
                        onPasswordVerified = {
                            val deviceAdmin = DeviceAdmin()
                            deviceAdmin.setPasswordVerified(this, true)

                            Toast.makeText(
                                this,
                                "Password verified, you can now disable admin permission",
                                Toast.LENGTH_SHORT
                            ).show()
                            appLockRepository.setAntiUninstallEnabled(false)
                            finish()
                        },
                        onCancel = {
                            val deviceAdmin = DeviceAdmin()
                            deviceAdmin.setPasswordVerified(this, false)
                            finish()
                        },
                        validatePassword = { inputPassword ->
                            appLockRepository.validatePassword(inputPassword).also { isValid ->
                                if (!isValid) {
                                    Toast.makeText(
                                        this,
                                        "Incorrect password. Please try again.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AdminDisableScreen(
    modifier: Modifier = Modifier,
    onPasswordVerified: () -> Unit,
    onCancel: () -> Unit,
    validatePassword: (String) -> Boolean
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        val passwordState = remember { mutableStateOf("") }
        val showError = remember { mutableStateOf(false) }
        val maxLength = 6

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = "Enter your password to disable admin permission",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            PasswordIndicators(
                passwordLength = passwordState.value.length,
                maxLength = maxLength
            )

            if (showError.value) {
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
                showBiometricButton = false,
                fromMainActivity = false,
                onBiometricAuth = {},
                onAuthSuccess = {},
                onPinAttempt = { pin ->
                    val isValid = validatePassword(pin)
                    if (isValid) {
                        onPasswordVerified()
                    } else {
                        onCancel()
                    }
                    isValid
                },
                onPasswordChange = { showError.value = false },
                onPinIncorrect = { showError.value = true }
            )
        }
    }
}
