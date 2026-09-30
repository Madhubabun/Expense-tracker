package app.expensetracker.ui

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import app.expensetracker.Notifier
import app.expensetracker.Reminders
import app.expensetracker.TodayWidget

class MainActivity : ComponentActivity() {
    /** Set when the user taps a transaction notification; the app opens that transaction's editor. */
    private val openTxnId = mutableLongStateOf(-1L)
    private val locked = mutableStateOf(false)
    private var stoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifier.ensureChannel(this)
        Reminders.schedule(this)
        locked.value = Lock.shouldLock(this)
        openTxnId.longValue = intent.getLongExtra(Notifier.EXTRA_TXN_ID, -1L)
        setContent {
            AppTheme {
                if (locked.value) {
                    LockScreen(onUnlock = { Lock.authenticate(this, onSuccess = { locked.value = false }) })
                } else {
                    AppRoot(
                        openTxnId = openTxnId.longValue,
                        onOpenTxnHandled = { openTxnId.longValue = -1L },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Lock again after a minute away. Short trips (photo picker, file picker) don't ask again.
        if (stoppedAt > 0 && SystemClock.elapsedRealtime() - stoppedAt > 60_000 && Lock.shouldLock(this)) locked.value = true
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
        runCatching { TodayWidget.refresh(this) }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openTxnId.longValue = intent.getLongExtra(Notifier.EXTRA_TXN_ID, -1L)
    }
}
