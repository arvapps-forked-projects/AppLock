package dev.pranav.applock.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Backspace = ImageVector.Builder(
    name = "Backspace",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 960f,
    viewportHeight = 960f
).apply {
    path(
        fill = SolidColor(Color.Black),
        fillAlpha = 1.0f,
        stroke = null,
        strokeAlpha = 1.0f,
        strokeLineWidth = 1.0f,
        strokeLineCap = StrokeCap.Butt,
        strokeLineJoin = StrokeJoin.Miter,
        strokeLineMiter = 1.0f,
        pathFillType = PathFillType.NonZero
    ) {
        moveTo(360f, 760f)
        quadToRelative(-20f, 0f, -37.5f, -9f)
        reflectiveQuadTo(294f, 726f)
        lineTo(120f, 480f)
        lineToRelative(174f, -246f)
        quadToRelative(11f, -16f, 28.5f, -25f)
        reflectiveQuadToRelative(37.5f, -9f)
        horizontalLineToRelative(400f)
        quadToRelative(33f, 0f, 56.5f, 23.5f)
        reflectiveQuadTo(840f, 280f)
        verticalLineToRelative(400f)
        quadToRelative(0f, 33f, -23.5f, 56.5f)
        reflectiveQuadTo(760f, 760f)
        close()
        moveToRelative(400f, -80f)
        verticalLineToRelative(-400f)
        close()
        moveToRelative(-400f, 0f)
        horizontalLineToRelative(400f)
        verticalLineToRelative(-400f)
        horizontalLineTo(360f)
        lineTo(218f, 480f)
        close()
        moveToRelative(96f, -40f)
        lineToRelative(104f, -104f)
        lineToRelative(104f, 104f)
        lineToRelative(56f, -56f)
        lineToRelative(-104f, -104f)
        lineToRelative(104f, -104f)
        lineToRelative(-56f, -56f)
        lineToRelative(-104f, 104f)
        lineToRelative(-104f, -104f)
        lineToRelative(-56f, 56f)
        lineToRelative(104f, 104f)
        lineToRelative(-104f, 104f)
        close()
    }
}.build()
