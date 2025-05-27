package dev.pranav.applock

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.pranav.applock.ui.theme.AppLockTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val intent = Intent(
            Settings.ACTION_USAGE_ACCESS_SETTINGS
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        //startActivity(intent)

        startForegroundService(Intent(this, AppLockService::class.java))

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

    // Check if the app is already locked by asking the service
    val initialLockState = appLockService?.isAppLocked(appInfo.packageName) ?: false
    val isChecked = remember { mutableStateOf(initialLockState) }

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
