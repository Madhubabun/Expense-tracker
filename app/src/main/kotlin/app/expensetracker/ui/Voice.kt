package app.expensetracker.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState

/**
 * Returns a function that opens the phone's own speech recognizer and hands back what you said.
 * [onFailed] runs when the phone has no speech service or nothing was heard.
 */
@Composable
fun rememberSpeech(onFailed: () -> Unit, onText: (String) -> Unit): () -> Unit {
    val failed = rememberUpdatedState(onFailed)
    val got = rememberUpdatedState(onText)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        } else null
        if (text.isNullOrBlank()) failed.value() else got.value(text)
    }
    return {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say it, like “250 rupees chai”")
        try { launcher.launch(intent) } catch (e: ActivityNotFoundException) { failed.value() }
    }
}
