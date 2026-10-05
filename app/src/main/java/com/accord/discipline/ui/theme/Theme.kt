package com.accord.discipline.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val PurpleDarkColorScheme = darkColorScheme(
    primary = Purple80,
    primaryContainer = PurpleContainerDark,
    onPrimaryContainer = OnPurpleContainerDark,
    secondary = PurpleGrey80,
    tertiary = Pink80
)
private val PurpleLightColorScheme = lightColorScheme(
    primary = Purple40,
    primaryContainer = PurpleContainerLight,
    onPrimaryContainer = OnPurpleContainerLight,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

private val RedDarkColorScheme = darkColorScheme(
    primary = Red80,
    primaryContainer = RedContainerDark,
    onPrimaryContainer = OnRedContainerDark,
    secondary = PurpleGrey80,
    tertiary = Pink80
)
private val RedLightColorScheme = lightColorScheme(
    primary = Red40,
    primaryContainer = RedContainerLight,
    onPrimaryContainer = OnRedContainerLight,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

private val GreenDarkColorScheme = darkColorScheme(
    primary = Green80,
    primaryContainer = GreenContainerDark,
    onPrimaryContainer = OnGreenContainerDark,
    secondary = PurpleGrey80,
    tertiary = Pink80
)
private val GreenLightColorScheme = lightColorScheme(
    primary = Green40,
    primaryContainer = GreenContainerLight,
    onPrimaryContainer = OnGreenContainerLight,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

private val BlueDarkColorScheme = darkColorScheme(
    primary = Blue80,
    primaryContainer = BlueContainerDark,
    onPrimaryContainer = OnBlueContainerDark,
    secondary = PurpleGrey80,
    tertiary = Pink80
)
private val BlueLightColorScheme = lightColorScheme(
    primary = Blue40,
    primaryContainer = BlueContainerLight,
    onPrimaryContainer = OnBlueContainerLight,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

private val OrangeDarkColorScheme = darkColorScheme(
    primary = Orange80,
    primaryContainer = OrangeContainerDark,
    onPrimaryContainer = OnOrangeContainerDark,
    secondary = PurpleGrey80,
    tertiary = Pink80
)
private val OrangeLightColorScheme = lightColorScheme(
    primary = Orange40,
    primaryContainer = OrangeContainerLight,
    onPrimaryContainer = OnOrangeContainerLight,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

private val TealDarkColorScheme = darkColorScheme(
    primary = Teal80,
    primaryContainer = TealContainerDark,
    onPrimaryContainer = OnTealContainerDark,
    secondary = PurpleGrey80,
    tertiary = Pink80
)
private val TealLightColorScheme = lightColorScheme(
    primary = Teal40,
    primaryContainer = TealContainerLight,
    onPrimaryContainer = OnTealContainerLight,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun DisciplineTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    accentColor: String = "purple",
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> {
            if (darkTheme) {
                when (accentColor) {
                    "red" -> RedDarkColorScheme
                    "green" -> GreenDarkColorScheme
                    "blue" -> BlueDarkColorScheme
                    "orange" -> OrangeDarkColorScheme
                    "teal" -> TealDarkColorScheme
                    else -> PurpleDarkColorScheme
                }
            } else {
                when (accentColor) {
                    "red" -> RedLightColorScheme
                    "green" -> GreenLightColorScheme
                    "blue" -> BlueLightColorScheme
                    "orange" -> OrangeLightColorScheme
                    "teal" -> TealLightColorScheme
                    else -> PurpleLightColorScheme
                }
            }
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
