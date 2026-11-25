package com.abhang.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.abhang.app.R
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

/** Fill orange: switches, thumbs, decorative fills. Too light for text, so text uses [BurntSaffron]. */
val Saffron = Color(0xFFE65100)
/** Deeper orange for the app bar, headings and small orange text (white on it is 5.7:1). */
private val BurntSaffron = Color(0xFFB93C00)
private val Maroon = Color(0xFF7B1E1E)
private val DarkBar = Color(0xFF2A1C17)

private val Light = lightColorScheme(
    primary = BurntSaffron, onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE0B2), onPrimaryContainer = Maroon,
    secondary = Maroon, background = Color(0xFFFFF8F0), surface = Color(0xFFFFF8F0),
    surfaceVariant = Color(0xFFFFEBD6),
    outline = Color(0xFF8A6A55), outlineVariant = Color(0xFFE6C9AA),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFFFB74D), onPrimary = Color(0xFF3E1A00),
    primaryContainer = Color(0xFF5D2A00), onPrimaryContainer = Color(0xFFFFDDB8),
    secondary = Color(0xFFFFB4A8), background = Color(0xFF1B1210), surface = Color(0xFF1B1210),
    surfaceVariant = Color(0xFF3A2A24),
    outline = Color(0xFFA68B7C), outlineVariant = Color(0xFF5A453B),
)

/** App bar: burnt saffron in light; a dark surface with amber content in dark (a full amber slab glares at night). */
object BarColors {
    @Composable fun container(): Color = if (isSystemInDarkTheme()) DarkBar else MaterialTheme.colorScheme.primary
    @Composable fun content(): Color = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary
}

/** Google Sans (variable font, OFL). Weight axis is honoured on API 26+; older devices use the default weight. */
val GoogleSans = FontFamily(
    listOf(
        FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold,
    ).map { w ->
        Font(R.font.google_sans, w, variationSettings = FontVariation.Settings(w, FontStyle.Normal))
    },
)

private fun appTypography(): Typography {
    val t = Typography()
    fun TextStyle.g() = copy(fontFamily = GoogleSans)
    return t.copy(
        displayLarge = t.displayLarge.g(), displayMedium = t.displayMedium.g(), displaySmall = t.displaySmall.g(),
        headlineLarge = t.headlineLarge.g(), headlineMedium = t.headlineMedium.g(), headlineSmall = t.headlineSmall.g(),
        titleLarge = t.titleLarge.g(),
        // List titles and metadata sit a step above the Material defaults: readers are often older and Devanagari matras need air.
        titleMedium = t.titleMedium.g().copy(fontSize = 18.sp, lineHeight = 26.sp),
        titleSmall = t.titleSmall.g(),
        bodyLarge = t.bodyLarge.g(),
        bodyMedium = t.bodyMedium.g().copy(fontSize = 15.sp, lineHeight = 22.sp),
        bodySmall = t.bodySmall.g().copy(fontSize = 14.sp, lineHeight = 20.sp),
        labelLarge = t.labelLarge.g(),
        labelMedium = t.labelMedium.g().copy(fontSize = 14.sp, lineHeight = 20.sp),
        labelSmall = t.labelSmall.g(),
    )
}

@Composable
fun AbhangTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = remember { appTypography() },
        content = content,
    )
}
