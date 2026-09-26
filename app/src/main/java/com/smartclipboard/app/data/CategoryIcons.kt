package com.smartclipboard.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.smartclipboard.app.R
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Icons are copied into private storage; gallery URI access is never needed after import. */
object CategoryIcons {
    // Seeded IDs stay stable even when a built-in category is renamed.
    fun defaultResource(category: LibraryCategory?): Int = when (category?.id) {
        2L -> R.drawable.ic_sidebar_text
        3L -> R.drawable.ic_sidebar_link
        4L -> R.drawable.ic_sidebar_mail
        5L -> R.drawable.ic_sidebar_phone
        else -> R.drawable.ic_sidebar_library
    }

    suspend fun loadCategory(context: Context, category: LibraryCategory): Bitmap? =
        load(context, category.iconFile) ?: ContextCompat.getDrawable(context, defaultResource(category))
            ?.toBitmap(72, 72)

    private val cache = LruCache<String, Bitmap>(40)
    private fun directory(context: Context) = File(context.filesDir, "category-icons").apply { mkdirs() }
    private fun file(context: Context, name: String): File {
        require(name.matches(Regex("[a-f0-9-]+\\.png")))
        return File(directory(context), name)
    }
    suspend fun load(context: Context, name: String?): Bitmap? = withContext(Dispatchers.IO) {
        if (name == null) return@withContext null
        cache.get(name) ?: runCatching { BitmapFactory.decodeFile(file(context, name).path) }
            .getOrNull()?.also { cache.put(name, it) }
    }
    suspend fun save(context: Context, bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val name = "${UUID.randomUUID()}.png"
        val output = file(context, name)
        try {
            output.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            name
        } catch (e: Exception) { output.delete(); throw e }
    }
    suspend fun remove(context: Context, name: String?) = withContext(Dispatchers.IO) {
        if (name != null) { cache.remove(name); runCatching { file(context, name).delete() } }
    }
    suspend fun decode(context: Context, uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val factor = minOf(1f, 1200f / maxOf(info.size.width, info.size.height))
                decoder.setTargetSize(maxOf(1, (info.size.width * factor).toInt()), maxOf(1, (info.size.height * factor).toInt()))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
            require(options.outWidth > 0 && options.outHeight > 0) { "无法读取图片" }
            options.inJustDecodeBounds = false
            options.inSampleSize = 1
            while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 1200) options.inSampleSize *= 2
            val bitmap = requireNotNull(context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) })
            // ImageDecoder applies EXIF orientation automatically; do the same on Android 8.
            val orientation = runCatching {
                context.contentResolver.openInputStream(uri).use { stream ->
                    android.media.ExifInterface(requireNotNull(stream)).getAttributeInt(
                        android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)
                }
            }.getOrDefault(android.media.ExifInterface.ORIENTATION_NORMAL)
            val matrix = android.graphics.Matrix().apply {
                when (orientation) {
                    2 -> setScale(-1f, 1f)
                    3 -> setRotate(180f)
                    4 -> setScale(1f, -1f)
                    5 -> { setRotate(90f); postScale(-1f, 1f) }
                    6 -> setRotate(90f)
                    7 -> { setRotate(-90f); postScale(-1f, 1f) }
                    8 -> setRotate(-90f)
                }
            }
            if (matrix.isIdentity) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
    }
}
