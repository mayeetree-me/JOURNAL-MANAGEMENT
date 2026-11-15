package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class StatusColorPalette(
  val todoBg: Color,
  val todoFg: Color,
  val inProgressBg: Color,
  val inProgressFg: Color,
  val doneBg: Color,
  val doneFg: Color,
  val overdueBg: Color,
  val overdueFg: Color,
)

private val LightStatusColors = StatusColorPalette(
  todoBg = TodoBgLight,
  todoFg = TodoFgLight,
  inProgressBg = InProgressBgLight,
  inProgressFg = InProgressFgLight,
  doneBg = DoneBgLight,
  doneFg = DoneFgLight,
  overdueBg = OverdueBgLight,
  overdueFg = OverdueFgLight,
)

private val DarkStatusColors = StatusColorPalette(
  todoBg = TodoBgDark,
  todoFg = TodoFgDark,
  inProgressBg = InProgressBgDark,
  inProgressFg = InProgressFgDark,
  doneBg = DoneBgDark,
  doneFg = DoneFgDark,
  overdueBg = OverdueBgDark,
  overdueFg = OverdueFgDark,
)

val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }

private val JournalLightColorScheme = lightColorScheme(
  primary = BurgundyPrimary,
  onPrimary = BurgundyOnPrimary,
  primaryContainer = BurgundyPrimaryContainer,
  onPrimaryContainer = BurgundyOnPrimaryContainer,
  secondary = AcademicSecondary,
  onSecondary = Color.White,
  secondaryContainer = AcademicSecondaryContainer,
  onSecondaryContainer = AcademicOnSecondaryContainer,
  tertiary = BrassTertiary,
  onTertiary = Color.White,
  tertiaryContainer = BrassTertiaryContainer,
  onTertiaryContainer = BrassOnTertiaryContainer,
  background = IvoryBackground,
  onBackground = InkOnBackground,
  surface = IvorySurface,
  onSurface = InkOnBackground,
  surfaceVariant = IvorySurfaceVariant,
  onSurfaceVariant = InkOnSurfaceVariant,
  surfaceContainerLow = IvorySurfaceContainerLow,
  surfaceContainer = IvorySurfaceContainer,
  surfaceContainerHigh = IvorySurfaceContainerHigh,
  outline = LightOutline,
  outlineVariant = LightOutlineVariant,
  error = OverdueFgLight,
  onError = Color.White,
  errorContainer = OverdueBgLight,
  onErrorContainer = OverdueFgLight,
)

private val JournalDarkColorScheme = darkColorScheme(
  primary = RosePrimary,
  onPrimary = RoseOnPrimary,
  primaryContainer = RosePrimaryContainer,
  onPrimaryContainer = RoseOnPrimaryContainer,
  secondary = DarkSecondary,
  onSecondary = Color(0xFF281D1F),
  secondaryContainer = DarkSecondaryContainer,
  onSecondaryContainer = DarkOnSecondaryContainer,
  tertiary = DarkBrassTertiary,
  onTertiary = Color(0xFF2E1E06),
  tertiaryContainer = DarkBrassTertiaryContainer,
  onTertiaryContainer = DarkBrassOnTertiaryContainer,
  background = StudyBackground,
  onBackground = DarkOnBackground,
  surface = StudySurface,
  onSurface = DarkOnBackground,
  surfaceVariant = StudySurfaceVariant,
  onSurfaceVariant = DarkOnSurfaceVariant,
  surfaceContainerLow = StudySurfaceContainerLow,
  surfaceContainer = StudySurfaceContainer,
  surfaceContainerHigh = StudySurfaceContainerHigh,
  outline = DarkOutline,
  outlineVariant = DarkOutlineVariant,
  error = OverdueFgDark,
  onError = Color(0xFF3D0A0E),
  errorContainer = OverdueBgDark,
  onErrorContainer = OverdueFgDark,
)

private val JournalShapes = Shapes(
  small = RoundedCornerShape(8.dp),
  medium = RoundedCornerShape(14.dp),
  large = RoundedCornerShape(20.dp),
)

enum class ThemeMode(val label: String) {
  SYSTEM("System"),
  LIGHT("Ivory"),
  DARK("Study"),
}

@Composable
fun JournalWorkflowTheme(
  themeMode: ThemeMode = ThemeMode.SYSTEM,
  content: @Composable () -> Unit,
) {
  val darkTheme = when (themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
  }
  val colorScheme = if (darkTheme) JournalDarkColorScheme else JournalLightColorScheme
  val statusColors = if (darkTheme) DarkStatusColors else LightStatusColors

  CompositionLocalProvider(LocalStatusColors provides statusColors) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = JournalTypography,
      shapes = JournalShapes,
      content = content,
    )
  }
}
