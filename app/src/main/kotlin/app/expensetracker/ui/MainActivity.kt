package app.expensetracker.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableLongStateOf
import app.expensetracker.Notifier

class MainActivity : ComponentActivity() {
    /** Set when the user taps a transaction notification; the app opens that transaction's editor. */
    private val openTxnId = mutableLongStateOf(-1L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.ensureChannel(this)
        openTxnId.longValue = intent.getLongExtra(Notifier.EXTRA_TXN_ID, -1L)
        setContent {
            AppTheme {
                AppRoot(
                    openTxnId = openTxnId.longValue,
                    onOpenTxnHandled = { openTxnId.longValue = -1L },
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openTxnId.longValue = intent.getLongExtra(Notifier.EXTRA_TXN_ID, -1L)
    }
}
