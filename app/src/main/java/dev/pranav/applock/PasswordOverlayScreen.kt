package dev.pranav.applock

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.LocalActivity
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
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.pranav.applock.ui.Backspace
import dev.pranav.applock.ui.theme.AppLockTheme

class PasswordOverlayScreen : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Register back press callback
        onBackPressedDispatcher.addCallback(this) {
            AppLockService.isOverlayActive = false
            finish()

            val homeIntent = Intent(Intent.ACTION_MAIN)
            homeIntent.addCategory(Intent.CATEGORY_HOME)
            homeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(homeIntent)
        }

        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
        window.setType(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
        )

        setContent {
            AppLockTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PasswordScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
    }

    override fun onPause() {
        super.onPause()
        window.clearFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        AppLockService.isOverlayActive = false
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        AppLockService.isOverlayActive = false

        finish()

        val homeIntent = Intent(Intent.ACTION_MAIN)
        homeIntent.addCategory(Intent.CATEGORY_HOME)
        homeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(homeIntent)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val shapes = listOf(
    MaterialShapes.Triangle,
    MaterialShapes.Pentagon,
    MaterialShapes.Circle,
    MaterialShapes.Arrow,
    MaterialShapes.Pill,
    MaterialShapes.Cookie4Sided,
    MaterialShapes.ClamShell,
    MaterialShapes.Square,
    MaterialShapes.Heart,
    MaterialShapes.PixelTriangle
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalAnimationApi::class)
@Composable
fun PasswordScreen(modifier: Modifier = Modifier) {
    val passwordState = remember { mutableStateOf("") }
    val maxLength = 6
    val appLockService =
        (LocalContext.current.applicationContext as AppLockApplication).appLockServiceInstance
    val activity = LocalActivity.current as PasswordOverlayScreen

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "Enter password to continue",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(vertical = 16.dp)
        ) {
            repeat(maxLength) { index ->
                val filled = index < passwordState.value.length
                val isNext = index == passwordState.value.length && index < maxLength

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
                            animationSpec = tween(
                                150
                            )
                        )
                    }, label = "indicatorAnimation"
                ) { state ->
                    val shape = when (state) {
                        "filled" -> shapes.random().toShape()
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
                            ))
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 48.dp)
        ) {
            KeypadRow(
                keys = listOf("1", "2", "3"), onKeyClick = { key ->
                    if (passwordState.value.length < maxLength) {
                        passwordState.value += key
                    }
                })

            KeypadRow(
                keys = listOf("4", "5", "6"), onKeyClick = { key ->
                    if (passwordState.value.length < maxLength) {
                        passwordState.value += key
                    }
                })

            KeypadRow(
                keys = listOf("7", "8", "9"), onKeyClick = { key ->
                    if (passwordState.value.length < maxLength) {
                        passwordState.value += key
                    }
                })

            KeypadRow(
                keys = listOf("backspace", "0", "proceed"),
                icons = listOf(
                    Backspace,
                    null,
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight
                ),
                onKeyClick = { key ->
                    when (key) {
                        "0" -> {
                            if (passwordState.value.length < maxLength) {
                                passwordState.value += key
                            }
                        }

                        "backspace" -> {
                            if (passwordState.value.isNotEmpty()) {
                                passwordState.value =
                                    passwordState.value.dropLast(1)
                            }
                        }

                        else -> {
                            if (passwordState.value.length == maxLength) {
                                if (appLockService != null) {
                                    if (appLockService.validatePassword(passwordState.value)) {
                                        appLockService.unlockApp(
                                            AppLockService.currentLockedPackage ?: ""
                                        )
                                        activity.finish()
                                    } else {
                                        passwordState.value = ""
                                    }
                                } else {
                                    passwordState.value = ""
                                }
                            }
                        }
                    }
                })
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun KeypadRow(
    keys: List<String>,
    icons: List<ImageVector?> = emptyList(),
    onKeyClick: (String) -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        keys.forEach { key ->
            val interactionSource =
                remember { MutableInteractionSource() }

            ElevatedButton(
                onClick = {
                    vibrate(context, 50)
                    onKeyClick(key)
                },
                modifier = Modifier
                    .padding(8.dp)
                    .weight(1f),
                shape = CircleShape,
                interactionSource = interactionSource,
                content = {
                    if (icons.isNotEmpty() && icons[keys.indexOf(key)] != null) {
                        Icon(
                            imageVector = icons[keys.indexOf(key)]!!,
                            contentDescription = key,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        Text(
                            text = key,
                            style = MaterialTheme.typography.displaySmallEmphasized,
                        )
                    }
                })
        }
    }
}
