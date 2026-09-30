package app.expensetracker.ui

import android.app.Activity
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import app.expensetracker.data.Prefs
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Fingerprint, face or screen-lock unlock. Needs Android 11 or newer and a screen lock set up on the phone. */
object Lock {
    private const val AUTH = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun available(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 30 &&
            context.getSystemService(BiometricManager::class.java)?.canAuthenticate(AUTH) == BiometricManager.BIOMETRIC_SUCCESS

    /** True when the lock is switched on and the phone can actually ask for it, so nobody gets locked out. */
    fun shouldLock(context: Context): Boolean = Prefs.flag(context, Prefs.APP_LOCK, false) && available(context)

    fun authenticate(activity: Activity, onSuccess: () -> Unit, onFail: () -> Unit = {}) {
        if (Build.VERSION.SDK_INT < 30) return onFail()
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle("Unlock Expense Tracker")
            .setAllowedAuthenticators(AUTH)
            .build()
        prompt.authenticate(
            CancellationSignal(), activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) = onFail()
            },
        )
    }
}

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    LaunchedEffect(Unit) { onUnlock() }
    Column(
        Modifier.fillMaxSize().background(Pal.bg).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🔒", fontSize = 56.sp)
        Text("Expense Tracker is locked", color = Pal.fg, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
        Text(
            "Unlock",
            color = Color.White, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 24.dp).clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(Pal.accent, Pal.pink))).clickable(onClick = onUnlock)
                .padding(horizontal = 32.dp, vertical = 14.dp),
        )
    }
}
