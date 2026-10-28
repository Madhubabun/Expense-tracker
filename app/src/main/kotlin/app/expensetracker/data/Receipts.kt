package app.expensetracker.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.util.UUID

/** Receipt photos attached to a spend. Shrunk to at most 1280 px so they stay small, kept on the phone only. */
object Receipts {
    private const val MAX_SIDE = 1280

    fun dir(context: Context) = File(context.filesDir, "receipts").apply { mkdirs() }

    /** Returns the saved file name, or null if the picture could not be read. */
    fun save(context: Context, uri: Uri): String? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val source = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val scale = MAX_SIDE.toFloat() / maxOf(source.width, source.height)
        val out = if (scale < 1f) Bitmap.createScaledBitmap(source, (source.width * scale).toInt(), (source.height * scale).toInt(), true) else source
        val name = "rcpt-" + UUID.randomUUID().toString().take(8) + ".jpg"
        File(dir(context), name).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 82, it) }
        name
    }.getOrNull()

    fun load(context: Context, name: String): Bitmap? =
        runCatching { BitmapFactory.decodeFile(File(dir(context), name).path) }.getOrNull()

    fun delete(context: Context, name: String?) {
        if (!name.isNullOrEmpty()) File(dir(context), name).delete()
    }
}
