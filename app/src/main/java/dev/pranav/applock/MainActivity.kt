package dev.pranav.applock

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.pranav.applock.ui.theme.AppLockTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check if we should show the app intro
        if (AppIntroActivity.shouldShowIntro(this)) {
            // Launch the intro activity
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
            // Don't finish MainActivity so it's in the back stack when user returns
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
    val appLockService = (context.applicationContext as AppLockApplication).appLockServiceInstance

    val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

    // Get all apps and sort them for efficient searching
    val allApps = remember {
        launcherApps.getActivityList(null, android.os.Process.myUserHandle())
            .mapNotNull { it.applicationInfo }
            .filter { it.enabled && it.packageName != context.packageName }
            .sortedBy { it.loadLabel(context.packageManager).toString().lowercase() }
    }

    // Search state
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    // Filter apps based on search query with improved search algorithm
    val filteredApps = remember(searchQuery) {
        if (searchQuery.isEmpty()) {
            allApps
        } else {
            val query = searchQuery.lowercase()
            // Use binary search-like approach for better performance
            // First, find apps that start with the query (higher priority)
            val startsWithMatches = allApps.filter {
                it.loadLabel(context.packageManager).toString().lowercase().startsWith(query)
            }

            // Then find apps that contain the query but don't start with it
            val containsMatches = if (query.length > 1) {
                allApps.filter { app ->
                    val appName = app.loadLabel(context.packageManager).toString().lowercase()
                    !appName.startsWith(query) && appName.contains(query)
                }
            } else {
                emptyList()
            }

            // Combine both results, prioritizing exact matches
            startsWithMatches + containsMatches
        }
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
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search field with simplified focus management
            val focusManager = LocalFocusManager.current

            OutlinedTextField(
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
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
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

            // App list in LazyColumn
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // List of filtered apps
                items(filteredApps.size) { index ->
                    val appInfo = filteredApps[index]
                    AppItem(
                        appInfo = appInfo,
                        context = context,
                        onClick = { isChecked ->
                            if (isChecked) {
                                // Lock the app
                                Log.d("AppLock", "Locking app: ${appInfo.packageName}")
                                appLockService?.addLockedApp(appInfo.packageName)
                            } else {
                                // Unlock the app
                                Log.d("AppLock", "Unlocking app: ${appInfo.packageName}")
                                appLockService?.removeLockedApp(appInfo.packageName)
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppItem(
    appInfo: android.content.pm.ApplicationInfo,
    context: Context,
    onClick: (Boolean) -> Unit
) {
    val icon = appInfo.loadIcon(context.packageManager).toBitmap().asImageBitmap()
    val appLockService = (context.applicationContext as AppLockApplication).appLockServiceInstance

    // Use collectAsState to observe changes from a flow or use remember + key to force recomposition
    val isLocked = appLockService?.isAppLocked(appInfo.packageName) ?: false

    // Using a key parameter with remember forces it to recompose when the key changes
    val isChecked = remember(isLocked) {
        mutableStateOf(isLocked)
    }

    // Force recomposition on each pass to ensure state is fresh
    LaunchedEffect(Unit) {
        isChecked.value = isLocked
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
            .clickable { /* No-op, just to consume click events */ },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Image(
            bitmap = icon,
            contentDescription = appInfo.loadLabel(context.packageManager).toString(),
            modifier = Modifier
                .size(48.dp)
                .padding(4.dp)
        )

        Text(
            text = appInfo.loadLabel(context.packageManager).toString(),
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
