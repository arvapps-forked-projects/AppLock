package dev.pranav.applock

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import dev.pranav.applock.ui.theme.AppLockTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check if usage access permission is granted
        if (!hasUsagePermission()) {
            // If not, redirect to settings
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            Log.d("AppLock", "Redirecting to usage access settings")
            startActivity(intent)
        }
        //check if app has permission to draw over other apps
        if (!Settings.canDrawOverlays(this)) {
            // If not, redirect to settings
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            Log.d("AppLock", "Redirecting to overlay permission settings")
            startActivity(intent)
        }

        // android 14 notification permission
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                Log.d("AppLock", "Redirecting to notification permission settings")
                startActivity(intent)
            }
        }

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
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Main(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        finish() // Close MainActivity when user navigates away
    }

    fun checkPermissions(): Boolean {
        // checks if all required permissions are granted
        val permissions = arrayOf(
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.SYSTEM_ALERT_WINDOW
        )
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalAnimationApi::class)
@Composable
fun Main(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val appLockService = (context.applicationContext as AppLockApplication).appLockServiceInstance

    val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

    val apps = launcherApps.getActivityList(null, android.os.Process.myUserHandle())
        .mapNotNull { it.applicationInfo }
        .filter { it.enabled && it.packageName != context.packageName }
        .sortedBy { it.loadLabel(context.packageManager).toString() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        // Header with Settings and Set PIN buttons
        item {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 24.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "App Lock",
                    style = MaterialTheme.typography.headlineSmall
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Settings button
                    androidx.compose.material3.IconButton(
                        onClick = {
                            context.startActivity(Intent(context, SettingsActivity::class.java))
                        }
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }

                    // Set PIN button
                    androidx.compose.material3.Button(
                        onClick = {
                            context.startActivity(Intent(context, SetPasswordActivity::class.java))
                        }
                    ) {
                        Text("Set PIN")
                    }
                }
            }
        }

        // List of apps
        items(apps.size) { index ->
            val appInfo = apps[index]
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
    val isChecked = androidx.compose.runtime.remember(isLocked) {
        androidx.compose.runtime.mutableStateOf(isLocked)
    }

    // Force recomposition on each pass to ensure state is fresh
    androidx.compose.runtime.LaunchedEffect(Unit) {
        isChecked.value = isLocked
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
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
