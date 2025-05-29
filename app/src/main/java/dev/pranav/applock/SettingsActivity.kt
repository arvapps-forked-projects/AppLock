package dev.pranav.applock

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.pranav.applock.ui.theme.AppLockTheme

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AppLockTheme {
                SettingsScreen(
                    onBackPressed = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val appLockService = (context.applicationContext as AppLockApplication).appLockServiceInstance

    // Load settings values
    val prefs = context.getSharedPreferences("app_lock_settings", Context.MODE_PRIVATE)
    var useMaxBrightness by remember {
        mutableStateOf(
            prefs.getBoolean(
                "use_max_brightness",
                false
            )
        )
    }
    var useBiometricAuth by remember {
        mutableStateOf(
            prefs.getBoolean(
                "use_biometric_auth",
                false
            )
        )
    }

    // Check if biometric authentication is available on the device
    val biometricManager = BiometricManager.from(context)
    val isBiometricAvailable = remember {
        biometricManager.canAuthenticate(Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Lock Screen Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        // Maximum Brightness Option
                        SettingItem(
                            icon = Icons.Default.Star,
                            title = "Maximum Brightness",
                            description = "Display lock screen at maximum brightness",
                            checked = useMaxBrightness,
                            onCheckedChange = { isChecked ->
                                useMaxBrightness = isChecked
                                prefs.edit().putBoolean("use_max_brightness", isChecked).apply()
                            }
                        )

                        Divider(modifier = Modifier.padding(vertical = 8.dp))

                        // Fingerprint Authentication Option
                        SettingItem(
                            icon = Icons.Default.Lock,
                            title = "Fingerprint Authentication",
                            description = if (isBiometricAvailable)
                                "Use fingerprint to unlock apps"
                            else
                                "Fingerprint not available on this device",
                            checked = useBiometricAuth && isBiometricAvailable,
                            enabled = isBiometricAvailable,
                            onCheckedChange = { isChecked ->
                                useBiometricAuth = isChecked
                                prefs.edit().putBoolean("use_biometric_auth", isChecked).apply()
                            }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    androidx.compose.material3.Button(
                        onClick = {
                            context.startActivity(Intent(context, SetPasswordActivity::class.java))
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Change PIN",
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("Change PIN")
                    }
                }
            }
        }
    }
}

@Composable
fun SettingItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 16.dp)
        )

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = 0.6f
                )
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}
