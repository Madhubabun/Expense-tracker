package app.expensetracker.data

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArrayList

/** Lets the open screen know the data changed behind its back (a notification button, a new SMS). */
object DataEvents {
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    fun listen(onChange: () -> Unit): () -> Unit {
        listeners += onChange
        return { listeners -= onChange }
    }

    fun changed() {
        main.post { listeners.forEach { it() } }
    }
}
