package dev.pranav.applock.features.applist.ui

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import dev.pranav.applock.R
import dev.pranav.applock.core.broadcast.DeviceAdmin
import dev.pranav.applock.core.navigation.Screen
import dev.pranav.applock.core.utils.appLockRepository
import dev.pranav.applock.core.utils.hasUsagePermission
import dev.pranav.applock.core.utils.isAccessibilityServiceEnabled
import dev.pranav.applock.core.utils.openAccessibilitySettings
import dev.pranav.applock.data.repository.BackendImplementation
import dev.pranav.applock.ui.components.AccessibilityServiceGuideDialog
import dev.pranav.applock.ui.components.AntiUninstallAccessibilityPermissionDialog
import dev.pranav.applock.ui.components.ShizukuPermissionDialog
import dev.pranav.applock.ui.components.UsageStatsPermission
import rikka.shizuku.Shizuku

@OptIn(
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun MainScreen(
    navController: NavController,
    mainViewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val searchQuery by mainViewModel.searchQuery.collectAsState()
    val isLoading by mainViewModel.isLoading.collectAsState()
    val filteredApps by mainViewModel.filteredApps.collectAsState()

    var showAccessibilityDialog by remember { mutableStateOf(false) }
    var showShizukuDialog by remember { mutableStateOf(false) }
    var showUsageStatsDialog by remember { mutableStateOf(false) }
    var showAntiUninstallAccessibilityDialog by remember { mutableStateOf(false) }
    var showAntiUninstallDeviceAdminDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val appLockRepository = context.appLockRepository()
        val selectedBackend = appLockRepository.getBackendImplementation()
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val component = ComponentName(context, DeviceAdmin::class.java)

        if (appLockRepository.isAntiUninstallEnabled()) {
            Log.d("MainScreen", "Anti-uninstall is enabled")
            if (!context.isAccessibilityServiceEnabled()) {
                showAntiUninstallAccessibilityDialog = true
            } else if (!dpm.isAdminActive(component)) {
                showAntiUninstallDeviceAdminDialog = true
            }
        }

        when (selectedBackend) {
            BackendImplementation.ACCESSIBILITY -> {
                if (!context.isAccessibilityServiceEnabled()) {
                    showAccessibilityDialog = true
                }
            }

            BackendImplementation.USAGE_STATS -> {
                if (!context.hasUsagePermission()) {
                    showUsageStatsDialog = true
                }
            }

            BackendImplementation.SHIZUKU -> {
                try {
                    if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() == PackageManager.PERMISSION_DENIED) {
                        showShizukuDialog = true
                    }
                } catch (_: Exception) {
                    Toast.makeText(
                        context,
                        "Shizuku not available. Please install and configure Shizuku.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // Show accessibility service guide dialog if needed
    if (showAccessibilityDialog && !showAntiUninstallAccessibilityDialog && !showAntiUninstallDeviceAdminDialog && !context.isAccessibilityServiceEnabled()) {
        AccessibilityServiceGuideDialog(
            onOpenSettings = {
                openAccessibilitySettings(context)
                showAccessibilityDialog = false
            },
            onDismiss = {
                showAccessibilityDialog = false
            }
        )
    }

    if (showShizukuDialog && !showAntiUninstallAccessibilityDialog && !showAntiUninstallDeviceAdminDialog && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_DENIED) {
        ShizukuPermissionDialog(
            onOpenSettings = {
                try {
                    if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
                        Toast.makeText(
                            context,
                            "Please grant Shizuku permission manually",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        showShizukuDialog = false
                        Shizuku.requestPermission(423)
                    }
                } catch (_: Exception) {
                    Toast.makeText(
                        context,
                        "Shizuku not available. Please install and configure Shizuku.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
            onDismiss = {
                showShizukuDialog = false
            }
        )
    }

    if (showUsageStatsDialog && !showAntiUninstallAccessibilityDialog && !showAntiUninstallDeviceAdminDialog && !context.hasUsagePermission()) {
        UsageStatsPermission(
            onOpenSettings = {
                context.startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                showUsageStatsDialog = false
            },
            onDismiss = {
                showUsageStatsDialog = false
            }
        )
    }

    if (showAntiUninstallAccessibilityDialog) {
        AntiUninstallAccessibilityPermissionDialog(
            onOpenSettings = {
                openAccessibilitySettings(context)
                showAntiUninstallAccessibilityDialog = false
            },
            onDismiss = {
                showAntiUninstallAccessibilityDialog = false
            }
        )
    }

    if (showAntiUninstallDeviceAdminDialog) {
        AntiUninstallAccessibilityPermissionDialog(
            onOpenSettings = {
                val component = ComponentName(context, DeviceAdmin::class.java)
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
                    putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "App Lock requires Device Admin permission to prevent uninstallation. It will not affect your device's functionality."
                    )
                }
                context.startActivity(intent)
                showAntiUninstallDeviceAdminDialog = false
            },
            onDismiss = {
                showAntiUninstallDeviceAdminDialog = false
            }
        )
    }

    val appLockRepository = context.appLockRepository()

    var showCommunityLink by remember { mutableStateOf(appLockRepository.isShowCommunityLink()) }

    if (showCommunityLink && !showAccessibilityDialog && !showShizukuDialog && !showUsageStatsDialog && !showAntiUninstallAccessibilityDialog && !showAntiUninstallDeviceAdminDialog) {
        AlertDialog(
            onDismissRequest = { appLockRepository.setCommunityLinkShown(true) },
            title = { Text("Join the Community") },
            text = { Text("Join our discord community for updates and support.") },
            confirmButton = {
                TextButton(onClick = {
                    appLockRepository.setCommunityLinkShown(true)
                    showCommunityLink = false
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            "https://discord.gg/46wCMRVAre".toUri()
                        )
                    )
                }) {
                    Text("Join Discord")
                }
            },
            dismissButton = {
                TextButton(onClick = { appLockRepository.setCommunityLinkShown(true) }) {
                    Text("Dismiss")
                }
            }
        )
    }

    var showDonateDialog by remember { mutableStateOf(appLockRepository.isShowDonateLink()) }
    if (showDonateDialog && !showAccessibilityDialog && !showShizukuDialog && !showUsageStatsDialog && !showAntiUninstallAccessibilityDialog && !showAntiUninstallDeviceAdminDialog && !showCommunityLink) {
        AlertDialog(
            onDismissRequest = { showDonateDialog = false },
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
                        showDonateDialog = false
                    }
                ) { Text("Donate") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDonateDialog = false
                }) { Text("Cancel") }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = {
                    Text(
                        "App Lock",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Medium
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            navController.navigate(Screen.Settings.route) {
                                launchSingleTop = true
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        if (isLoading) {
            LoadingContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            MainContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                searchQuery = searchQuery,
                filteredApps = filteredApps,
                onSearchQueryChanged = { mainViewModel.onSearchQueryChanged(it) },
                onAppToggle = { appInfo, isChecked ->
                    mainViewModel.toggleAppLock(appInfo, isChecked)
                },
                viewModel = mainViewModel
            )
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 4.dp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Loading applications...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainContent(
    modifier: Modifier = Modifier,
    searchQuery: String,
    filteredApps: Set<ApplicationInfo>,
    onSearchQueryChanged: (String) -> Unit,
    onAppToggle: (ApplicationInfo, Boolean) -> Unit,
    viewModel: MainViewModel
) {
    val focusManager = LocalFocusManager.current

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            SearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = searchQuery,
                        onQueryChange = onSearchQueryChanged,
                        onSearch = { focusManager.clearFocus() },
                        expanded = false,
                        onExpandedChange = {},
                        placeholder = {
                            Text(
                                "Search apps",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                    )
                },
                expanded = false,
                onExpandedChange = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(28.dp),
                colors = SearchBarDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                content = {},
            )
        }

        if (filteredApps.isEmpty() && searchQuery.isNotEmpty()) {
            item {
                EmptySearchState(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                )
            }
        } else {
            items(filteredApps.toList(), key = { it.packageName }) { appInfo ->
                AppItem(
                    appInfo = appInfo,
                    viewModel = viewModel,
                    onClick = { isChecked ->
                        onAppToggle(appInfo, isChecked)
                    }
                )
            }
        }
    }
}

@Composable
private fun EmptySearchState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Security,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No apps found",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Try adjusting your search",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppItem(
    appInfo: ApplicationInfo,
    viewModel: MainViewModel,
    onClick: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    val appName = remember(appInfo) { appInfo.loadLabel(packageManager).toString() }
    val icon = remember(appInfo) { appInfo.loadIcon(packageManager)?.toBitmap()?.asImageBitmap() }

    val isChecked = remember(appInfo) {
        mutableStateOf(viewModel.isAppLocked(appInfo.packageName))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                isChecked.value = !isChecked.value
                onClick(isChecked.value)
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = RoundedCornerShape(30),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = icon ?: ImageBitmap.imageResource(R.drawable.ic_notification),
                    contentDescription = appName,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = appName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = appInfo.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Switch(
            checked = isChecked.value,
            onCheckedChange = { isCheckedValue ->
                isChecked.value = isCheckedValue
                onClick(isCheckedValue)
            }
        )
    }
}
