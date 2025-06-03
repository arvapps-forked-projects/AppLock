package dev.pranav.applock.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

// Enhanced Typography definitions matching the appintro module
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val Typography = Typography(
    headlineMediumEmphasized = TextStyle(
        fontWeight = FontWeight.Bold,
    ),
    displaySmallEmphasized = TextStyle(
        fontWeight = FontWeight.Bold,
    )
)
