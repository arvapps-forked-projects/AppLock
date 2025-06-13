package dev.pranav.applock.features.applist.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import dev.pranav.applock.R
import dev.pranav.applock.core.navigation.Screen
import dev.pranav.applock.core.utils.isAccessibilityServiceEnabled
import dev.pranav.applock.core.utils.openAccessibilitySettings
import dev.pranav.applock.ui.components.AccessibilityServiceGuideDialog

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

    val searchQuery by mainViewModel.searchQuery.collectAsState()
    val isLoading by mainViewModel.isLoading.collectAsState()
    val filteredApps by mainViewModel.filteredApps.collectAsState()


    // Check if accessibility service is enabled
    var showAccessibilityDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val isAccessibilityEnabled =
            context.isAccessibilityServiceEnabled()

        if (!isAccessibilityEnabled) {
            showAccessibilityDialog = true
        }
    }

    // Show accessibility service guide dialog if needed
    if (showAccessibilityDialog) {
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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "App Lock",
                        style = MaterialTheme.typography.titleLarge,
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
                            contentDescription = "Settings"
                        )
                    }
                },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            val focusManager = LocalFocusManager.current

            if (isLoading) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    LoadingIndicator(
                        modifier = Modifier.size(80.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading applications...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                TextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    value = searchQuery,
                    onValueChange = { mainViewModel.onSearchQueryChanged(it) },
                    placeholder = { Text("Search apps") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                        }
                    )
                )

                AppList(
                    apps = filteredApps,
                    context = context,
                    onAppClick = { appInfo, isChecked ->
                        mainViewModel.toggleAppLock(appInfo, isChecked)
                    }
                )
            }
        }
    }
}

@Composable
fun AppList(
    apps: List<ApplicationInfo>,
    context: Context,
    onAppClick: (ApplicationInfo, Boolean) -> Unit
) {
    val viewModel = viewModel<MainViewModel>()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
    ) {
        items(
            apps.size,
        ) { index ->
            val appInfo = apps[index]
            AppItem(
                appInfo = appInfo,
                context = context,
                viewModel = viewModel,
                onClick = { isChecked ->
                    onAppClick(appInfo, isChecked)
                }
            )
            if (index < apps.size - 1) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppItem(
    appInfo: ApplicationInfo,
    context: Context,
    viewModel: MainViewModel,
    onClick: (Boolean) -> Unit
) {
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
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Image(
            bitmap = icon ?: ImageBitmap.imageResource(R.drawable.ic_notification),
            contentDescription = appName,
            modifier = Modifier
                .size(48.dp)
                .padding(4.dp)
        )

        Text(
            text = appName,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge
        )

        Switch(
            checked = isChecked.value,
            onCheckedChange = { isCheckedValue ->
                isChecked.value = isCheckedValue
                onClick(isCheckedValue)
            },
        )
    }
}
