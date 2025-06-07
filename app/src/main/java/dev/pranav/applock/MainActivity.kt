package dev.pranav.applock

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.ExperimentalAnimationApi
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.pranav.applock.services.AppLockService
import dev.pranav.applock.ui.theme.AppLockTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check if we should show the app intro
        if (AppIntroActivity.shouldShowIntro(this)) {
            startActivity(Intent(this, AppIntroActivity::class.java))
            finish()
            return
        }

        // Start the app lock service
        startForegroundService(Intent(this, AppLockService::class.java))

        // Check if password is set, if not, redirect to SetPasswordActivity
        val sharedPrefs = getSharedPreferences("app_lock_prefs", MODE_PRIVATE)
        val isPasswordSet = sharedPrefs.contains("password")

        if (!isPasswordSet) {
            // First time app opening - direct to password setup
            val setupIntent = Intent(this, SetPasswordActivity::class.java)
            setupIntent.putExtra("FIRST_TIME_SETUP", true)
            startActivity(setupIntent)
            finish()
        } else {
            if (!intent.hasExtra("FIRST_TIME_SETUP")) {
                startActivity(Intent(this, PasswordOverlayScreen::class.java).apply {
                    putExtra("FROM_MAIN_ACTIVITY", true)
                })
            }
        }

        setContent {
            AppLockTheme {
                Main()
            }
        }
    }
}

@OptIn(
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalAnimationApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun Main(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val appLockService = (context.applicationContext as? AppLockApplication)?.appLockServiceInstance

    // Create search manager
    val searchManager = remember { AppSearchManager(context) }

    // Basic state tracking
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var apps by remember { mutableStateOf<List<ApplicationInfo>>(emptyList()) }

    // Apply debounce to search for better performance
    var debouncedQuery by remember { mutableStateOf("") }

    LaunchedEffect(searchQuery) {
        delay(200)
        debouncedQuery = searchQuery
    }

    // Load all apps on startup
    LaunchedEffect(Unit) {
        apps = searchManager.loadApps()
        isLoading = false
    }

    // Filter apps based on search query
    val filteredApps = remember(debouncedQuery, apps) {
        searchManager.searchApps(debouncedQuery)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "App Lock",
                        style = MaterialTheme.typography.titleLargeEmphasized,
                    )
                },
                actions = {
                    // Settings button
                    IconButton(
                        onClick = {
                            context.startActivity(Intent(context, SettingsActivity::class.java))
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
            // Search field with simplified focus management
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
                    onValueChange = { searchQuery = it },
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
                            // Clear focus when Done is pressed on keyboard
                            focusManager.clearFocus()
                        }
                    )
                )

                AppList(
                    apps = filteredApps,
                    context = context,
                    onAppClick = { appInfo, isChecked ->
                        // Toggle app lock state
                        if (isChecked) {
                            appLockService?.addLockedApp(appInfo.packageName)
                        } else {
                            appLockService?.unlockApp(appInfo.packageName) // This should be removeLockedApp
                        }
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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
    ) {
        items(apps.size) { index ->
            val appInfo = apps[index]
            AppItem(
                appInfo = appInfo,
                context = context,
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
    onClick: (Boolean) -> Unit
) {
    // Cache app name and icon using remember to avoid repeated calls during scrolling
    val (appName, icon) = remember(appInfo.packageName) {
        appInfo.loadLabel(context.packageManager).toString() to
                appInfo.loadIcon(context.packageManager)?.toBitmap()?.asImageBitmap()
    }

    val appLockService = (context.applicationContext as? AppLockApplication)?.appLockServiceInstance

    val isLocked = appLockService?.isAppLocked(appInfo.packageName) ?: false

    val isChecked = remember(isLocked) { // Use rememberSaveable for state persistence
        mutableStateOf(isLocked)
    }

    LaunchedEffect(isLocked) { // Observe isLocked directly
        isChecked.value = isLocked
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* No-op, just to consume click events */ }
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
            onCheckedChange = { check ->
                isChecked.value = check
                onClick(check)
            }
        )
    }
}

@Preview
@Composable
fun AppItemPreview() {
    AppLockTheme(false) {
        // Mock ApplicationInfo for preview
        val mockAppInfo = ApplicationInfo().apply {
            packageName = "com.example.app"
            icon = R.drawable.ic_notification
            labelRes = R.string.app_name
        }

        AppItem(
            appInfo = mockAppInfo,
            context = LocalContext.current,
            onClick = {}
        )
    }
}
