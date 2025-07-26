package dev.pranav.applock.features.settings.ui

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavController
import dev.pranav.applock.core.broadcast.DeviceAdmin
import dev.pranav.applock.core.navigation.Screen
import dev.pranav.applock.core.utils.hasUsagePermission
import dev.pranav.applock.core.utils.isAccessibilityServiceEnabled
import dev.pranav.applock.core.utils.openAccessibilitySettings
import dev.pranav.applock.data.repository.AppLockRepository
import dev.pranav.applock.data.repository.BackendImplementation
import dev.pranav.applock.services.ExperimentalAppLockService
import dev.pranav.applock.services.ShizukuAppLockService
import dev.pranav.applock.ui.icons.Accessibility
import dev.pranav.applock.ui.icons.BrightnessHigh
import dev.pranav.applock.ui.icons.Fingerprint
import dev.pranav.applock.ui.icons.FingerprintOff
import dev.pranav.applock.ui.icons.Github
import dev.pranav.applock.ui.icons.Timer
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider
import kotlin.math.abs


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val appLockRepository = remember { AppLockRepository(context) }
    var showDialog by remember { mutableStateOf(false) }
    var showUnlockTimeDialog by remember { mutableStateOf(false) }

    val shizukuPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Toast.makeText(context, "Shizuku permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    context,
                    "Shizuku permission is required for advanced features.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    var useMaxBrightness by remember {
        mutableStateOf(appLockRepository.shouldUseMaxBrightness())
    }
    var useBiometricAuth by remember {
        mutableStateOf(appLockRepository.isBiometricAuthEnabled())
    }
    var popBiometricAuth by remember {
        mutableStateOf(appLockRepository.shouldPromptForBiometricAuth())
    }
    var unlockTimeDuration by remember {
        mutableIntStateOf(appLockRepository.getUnlockTimeDuration())
    }

    var antiUninstallEnabled by remember {
        mutableStateOf(appLockRepository.isAntiUninstallEnabled())
    }
    var shizukuExperimental by remember {
        mutableStateOf(appLockRepository.isShizukuExperimentalEnabled())
    }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showDeviceAdminDialog by remember { mutableStateOf(false) }
    var showAccessibilityDialog by remember { mutableStateOf(false) }

    val biometricManager = BiometricManager.from(context)
    val isBiometricAvailable = remember {
        biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Support Development") },
            text = { Text("Hi, I'm Pranav, the developer of App Lock. I'm a student developer passionate about creating useful apps. If you find it helpful, please consider supporting its development with a small donation. Any amount is greatly appreciated and helps me continue to improve the app and work on new features. Thank you for your support!") },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                "https://paypal.me/pranavpurwar".toUri()
                            )
                        )
                        showDialog = false
                    }
                ) { Text("Donate") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } },
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    }

    if (showUnlockTimeDialog) {
        UnlockTimeDurationDialog(
            currentDuration = unlockTimeDuration,
            onDismiss = { showUnlockTimeDialog = false },
            onConfirm = { newDuration ->
                unlockTimeDuration = newDuration
                appLockRepository.setUnlockTimeDuration(newDuration)
                showUnlockTimeDialog = false
            }
        )
    }

    if (showPermissionDialog) {
        PermissionRequiredDialog(
            onDismiss = { showPermissionDialog = false },
            onConfirm = {
                showPermissionDialog = false
                showDeviceAdminDialog = true
            }
        )
    }

    if (showDeviceAdminDialog) {
        DeviceAdminDialog(
            onDismiss = { showDeviceAdminDialog = false },
            onConfirm = {
                showDeviceAdminDialog = false
                val component = ComponentName(context, DeviceAdmin::class.java)
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
                    putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "App Lock requires Device Admin permission to prevent uninstallation. It will not affect your device's functionality."
                    )
                }
                context.startActivity(intent)
            }
        )
    }

    if (showAccessibilityDialog) {
        AccessibilityDialog(
            onDismiss = { showAccessibilityDialog = false },
            onConfirm = {
                showAccessibilityDialog = false
                openAccessibilitySettings(context)

                // Check if device admin is still needed after accessibility is granted
                val dpm =
                    context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val component = ComponentName(context, DeviceAdmin::class.java)
                if (!dpm.isAdminActive(component)) {
                    showDeviceAdminDialog = true
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLargeEmphasized) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Lock Screen Customization",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column {
                        SettingItem(
                            icon = BrightnessHigh,
                            title = "Maximum Brightness",
                            description = "Display lock screen at maximum brightness for clarity",
                            checked = useMaxBrightness,
                            onCheckedChange = { isChecked ->
                                useMaxBrightness = isChecked
                                appLockRepository.setUseMaxBrightness(isChecked)
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingItem(
                            icon = if (useBiometricAuth) Fingerprint else FingerprintOff,
                            title = "Biometric Unlock",
                            description = if (isBiometricAvailable) "Use your fingerprint to unlock apps" else "Biometric authentication not available on this device",
                            checked = useBiometricAuth && isBiometricAvailable,
                            enabled = isBiometricAvailable,
                            onCheckedChange = { isChecked ->
                                useBiometricAuth = isChecked
                                appLockRepository.setBiometricAuthEnabled(isChecked)
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingItem(
                            icon = Icons.Default.Person,
                            title = "Prompt for Biometric",
                            description = "Prompt for biometric authentication before entering PIN",
                            checked = popBiometricAuth,
                            enabled = useBiometricAuth,
                            onCheckedChange = { isChecked ->
                                popBiometricAuth = isChecked
                                appLockRepository.setPromptForBiometricAuth(isChecked)
                            }
                        )
                    }
                }
            }
            item {
                Text(
                    text = "Security",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth(),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column {
                        SettingItem(
                            icon = Icons.Default.AutoAwesome,
                            title = "Experimental Shizuku",
                            description = "A more robust app lock implementation using Shizuku",
                            checked = shizukuExperimental,
                            onCheckedChange = { isChecked ->
                                if (isChecked) {
                                    if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_DENIED) {
                                        if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
                                            shizukuPermissionLauncher.launch(ShizukuProvider.PERMISSION)
                                        } else {
                                            Shizuku.requestPermission(423)
                                        }
                                    } else {
                                        shizukuExperimental = true
                                        appLockRepository.setShizukuExperimentalEnabled(true)
                                        context.stopService(
                                            Intent(context, ShizukuAppLockService::class.java)
                                        )
                                        context.startService(
                                            Intent(context, ShizukuAppLockService::class.java)
                                        )
                                    }
                                } else {
                                    shizukuExperimental = false
                                    appLockRepository.setShizukuExperimentalEnabled(false)
                                    context.stopService(
                                        Intent(context, ShizukuAppLockService::class.java)
                                    )
                                    context.startService(
                                        Intent(context, ShizukuAppLockService::class.java)
                                    )
                                }
                            },
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                        ActionSettingItem(
                            icon = Icons.Default.Lock,
                            title = "Change PIN",
                            description = "Change your App Lock PIN",
                            onClick = {
                                navController.navigate(Screen.ChangePassword.route)
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                        ActionSettingItem(
                            icon = Timer,
                            title = "Unlock Duration",
                            description = if (unlockTimeDuration > 0) "Apps remain unlocked for $unlockTimeDuration minutes" else "Apps lock immediately after exit",
                            onClick = { showUnlockTimeDialog = true }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                        SettingItem(
                            icon = Icons.Default.Lock,
                            title = "Anti Uninstall",
                            description = "Prevents uninstallation of App Lock",
                            checked = antiUninstallEnabled,
                            onCheckedChange = { isChecked ->
                                if (isChecked) {
                                    val dpm =
                                        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                                    val component = ComponentName(context, DeviceAdmin::class.java)
                                    val hasDeviceAdmin = dpm.isAdminActive(component)
                                    val hasAccessibility = context.isAccessibilityServiceEnabled()

                                    when {
                                        !hasDeviceAdmin && !hasAccessibility -> {
                                            showPermissionDialog = true
                                        }

                                        !hasDeviceAdmin -> {
                                            showDeviceAdminDialog = true
                                        }

                                        !hasAccessibility -> {
                                            showAccessibilityDialog = true
                                        }

                                        else -> {
                                            antiUninstallEnabled = true
                                            appLockRepository.setAntiUninstallEnabled(true)
                                        }
                                    }
                                } else {
                                    antiUninstallEnabled = false
                                    appLockRepository.setAntiUninstallEnabled(false)
                                }
                            }
                        )
                    }
                }
            }

            // New Backend Selection Section
            item {
                BackendSelectionCard(
                    appLockRepository = appLockRepository,
                    context = context,
                    shizukuPermissionLauncher = shizukuPermissionLauncher
                )
            }
            item {
                Text(
                    text = "About & Support",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 0.dp, bottom = 12.dp)
                )
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column {
                        ActionSettingItem(
                            icon = Icons.Filled.Favorite,
                            title = "Support Development",
                            onClick = { showDialog = true })
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        ActionSettingItem(icon = Github, title = "View Source Code", onClick = {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    "https://github.com/PranavPurwar/AppLock".toUri()
                                )
                            )
                        })
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        ActionSettingItem(
                            icon = Icons.Filled.Person,
                            title = "Developer Profile",
                            onClick = {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        "https://github.com/PranavPurwar".toUri()
                                    )
                                )
                            })
                    }
                }
            }
        }
    }
}

@Composable
fun SettingItem(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { if (enabled) onCheckedChange(!checked) }
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            modifier = Modifier.size(28.dp),
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(
                alpha = 0.38f
            )
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(
                    alpha = 0.38f
                )
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(
                    alpha = 0.38f
                )
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
fun ActionSettingItem(
    icon: ImageVector,
    title: String,
    description: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.primary
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            modifier = Modifier.size(28.dp),
            tint = iconTint
        )
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun UnlockTimeDurationDialog(
    currentDuration: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val durations = listOf(0, 1, 5, 15, 30, 60)
    var selectedDuration by remember { mutableIntStateOf(currentDuration) }

    // If the current duration is not in our list, default to the closest value
    if (!durations.contains(selectedDuration)) {
        selectedDuration = durations.minByOrNull { abs(it - currentDuration) } ?: 0
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("App Unlock Duration") },
        text = {
            Column {
                Text("Choose how long apps should remain unlocked after entering the PIN:")

                durations.forEach { duration ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedDuration = duration }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedDuration == duration,
                            onClick = { selectedDuration = duration }
                        )
                        Text(
                            text = when (duration) {
                                0 -> "Lock immediately"
                                1 -> "1 minute"
                                60 -> "1 hour"
                                else -> "$duration minutes"
                            },
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedDuration) }) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun BackendSelectionCard(
    appLockRepository: AppLockRepository,
    context: Context,
    shizukuPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>
) {
    var selectedBackend by remember { mutableStateOf(appLockRepository.getBackendImplementation()) }
    var selectedFallback by remember { mutableStateOf(appLockRepository.getFallbackBackend()) }
    var showFallbackDialog by remember { mutableStateOf(false) }

    if (showFallbackDialog) {
        FallbackSelectionDialog(
            currentFallback = selectedFallback,
            excludeBackend = selectedBackend,
            onDismiss = { showFallbackDialog = false },
            onConfirm = { fallback ->
                selectedFallback = fallback
                appLockRepository.setFallbackBackend(fallback)
                showFallbackDialog = false
            }
        )
    }

    Column {
        Text(
            text = "Backend Implementation",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column {
                Text(
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp),
                    text = "Primary Method",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                BackendImplementation.entries.forEach { backend ->
                    BackendSelectionItem(
                        backend = backend,
                        isSelected = selectedBackend == backend,
                        onClick = {
                            when (backend) {
                                BackendImplementation.SHIZUKU -> {
                                    if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_DENIED) {
                                        if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
                                            shizukuPermissionLauncher.launch(ShizukuProvider.PERMISSION)
                                        } else {
                                            Shizuku.requestPermission(423)
                                        }
                                    } else {
                                        selectedBackend = backend
                                        appLockRepository.setBackendImplementation(
                                            BackendImplementation.SHIZUKU
                                        )
                                        context.startService(
                                            Intent(
                                                context,
                                                ShizukuAppLockService::class.java
                                            )
                                        )
                                    }
                                }

                                BackendImplementation.USAGE_STATS -> {
                                    if (!context.hasUsagePermission()) {
                                        val intent = Intent(
                                            android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS
                                        )
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                        Toast.makeText(
                                            context,
                                            "Please grant usage access permission.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        return@BackendSelectionItem
                                    }

                                    selectedBackend = backend

                                    appLockRepository.setBackendImplementation(BackendImplementation.USAGE_STATS)
                                    context.startService(
                                        Intent(
                                            context,
                                            ExperimentalAppLockService::class.java
                                        )
                                    )
                                }

                                BackendImplementation.ACCESSIBILITY -> {
                                    if (!context.isAccessibilityServiceEnabled()) {
                                        openAccessibilitySettings(context)
                                        return@BackendSelectionItem
                                    }
                                    selectedBackend = backend

                                    appLockRepository.setBackendImplementation(BackendImplementation.ACCESSIBILITY)
                                }
                            }
                        }
                    )
                    if (backend != BackendImplementation.entries.last()) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showFallbackDialog = true }
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                    ) {
                        Text(
                            text = "Fallback Method",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "Used when primary method fails",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = { showFallbackDialog = true }
                    ) {
                        Text(getBackendDisplayName(selectedFallback))
                    }
                }
            }
        }
    }
}

@Composable
fun BackendSelectionItem(
    backend: BackendImplementation,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(12.dp))
        Icon(
            imageVector = getBackendIcon(backend),
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = getBackendDisplayName(backend),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                if (backend == BackendImplementation.SHIZUKU) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Badge(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary
                    ) {
                        Text(
                            text = "Advanced",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            Text(
                text = getBackendDescription(backend),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        RadioButton(
            selected = isSelected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
fun FallbackSelectionDialog(
    currentFallback: BackendImplementation,
    excludeBackend: BackendImplementation,
    onDismiss: () -> Unit,
    onConfirm: (BackendImplementation) -> Unit
) {
    var selectedFallback by remember { mutableStateOf(currentFallback) }
    val availableBackends = BackendImplementation.entries.filter { it != excludeBackend }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Fallback Method") },
        text = {
            Column {
                Text("Choose a fallback method to use when the primary method fails:")
                Spacer(modifier = Modifier.height(16.dp))

                availableBackends.forEach { backend ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedFallback = backend }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedFallback == backend,
                            onClick = { selectedFallback = backend }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = getBackendIcon(backend),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = getBackendDisplayName(backend),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedFallback) }) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun getBackendDisplayName(backend: BackendImplementation): String {
    return when (backend) {
        BackendImplementation.ACCESSIBILITY -> "Accessibility Service"
        BackendImplementation.USAGE_STATS -> "Usage Statistics"
        BackendImplementation.SHIZUKU -> "Shizuku Service"
    }
}

private fun getBackendDescription(backend: BackendImplementation): String {
    return when (backend) {
        BackendImplementation.ACCESSIBILITY -> "Standard method that works on most devices"
        BackendImplementation.USAGE_STATS -> "Experimental method with better performance"
        BackendImplementation.SHIZUKU -> "Advanced method with superior experience"
    }
}

private fun getBackendIcon(backend: BackendImplementation): ImageVector {
    return when (backend) {
        BackendImplementation.ACCESSIBILITY -> Accessibility
        BackendImplementation.USAGE_STATS -> Icons.Default.QueryStats
        BackendImplementation.SHIZUKU -> Icons.Default.AutoAwesome
    }
}

@Composable
fun PermissionRequiredDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Permissions Required") },
        text = {
            Text(
                "To enable Anti-Uninstall protection, App Lock needs two permissions:\n\n" +
                        "1. Device Administrator - Prevents uninstallation\n" +
                        "2. Accessibility Service - Monitors app usage\n\n" +
                        "These permissions help protect the app from being removed without your knowledge."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Grant Permissions")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DeviceAdminDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Device Administrator") },
        text = {
            Text(
                "App Lock needs Device Administrator permission to prevent uninstallation.\n\n" +
                        "This permission allows the app to:\n" +
                        "• Prevent itself from being uninstalled\n" +
                        "• Protect your app lock settings\n\n" +
                        "It will not affect your device's normal functionality."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Enable")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AccessibilityDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Accessibility Service") },
        text = {
            Text(
                "App Lock needs Accessibility Service permission to monitor app usage.\n\n" +
                        "This permission allows the app to:\n" +
                        "• Detect when apps are opened\n" +
                        "• Show lock screen when needed\n" +
                        "• Provide seamless app protection\n\n" +
                        "Your privacy is protected - no data is collected or shared."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Enable")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
