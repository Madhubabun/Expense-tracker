package app.expensetracker.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}

val DebitColor = Color(0xFFC62828)
val CreditColor = Color(0xFF2E7D32)

val ChartColors = listOf(
    Color(0xFF1E88E5), Color(0xFFF4511E), Color(0xFF43A047), Color(0xFF8E24AA),
    Color(0xFFFFB300), Color(0xFF00ACC1), Color(0xFFD81B60), Color(0xFF6D4C41),
)
