package app.expensetracker.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.util.UUID

/** Saves a picture chosen for a category as a small square file in the app's private folder. */
object CategoryImages {
    private const val SIZE = 128

    private fun dir(context: Context) = File(context.filesDir, "category-images").apply { mkdirs() }

    /** Returns the saved file name, or null if the picture could not be read. */
    fun save(context: Context, uri: Uri): String? = runCatching {
        val source = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
        val side = minOf(source.width, source.height)
        val square = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
        val small = Bitmap.createScaledBitmap(square, SIZE, SIZE, true)
        val name = "cat-" + UUID.randomUUID().toString().take(8) + ".jpg"
        File(dir(context), name).outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        name
    }.getOrNull()

    fun load(context: Context, name: String): Bitmap? =
        runCatching { BitmapFactory.decodeFile(File(dir(context), name).path) }.getOrNull()

    fun delete(context: Context, name: String?) {
        if (name != null) File(dir(context), name).delete()
    }
}
