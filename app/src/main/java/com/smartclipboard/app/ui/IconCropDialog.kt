package com.smartclipboard.app.ui

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/** Bounded decode happens before this view; export is a 256px square in private storage. */
@Composable
fun IconCropDialog(bitmap: Bitmap, onDismiss: () -> Unit, onConfirm: (Bitmap) -> Unit) {
    val context = LocalContext.current
    val crop = remember(bitmap) { CropView(context, bitmap) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("裁剪分类图标") },
        text = { Column {
            Text("拖动调整位置，双指缩放；方框内为保留范围")
            AndroidView(factory = { crop }, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
            Row { TextButton(onClick = { crop.zoom(0.8f) }) { Text("缩小") }
                TextButton(onClick = { crop.zoom(1.25f) }) { Text("放大") } }
        } },
        confirmButton = { TextButton(onClick = { onConfirm(crop.export()) }) { Text("使用图片") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

private class CropView(context: Context, private val bitmap: Bitmap) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private var scale = 1f
    private var base = 1f
    private var x = 0f
    private var y = 0f
    private var lastX = 0f
    private var lastY = 0f
    private val detector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean { zoom(detector.scaleFactor); return true }
    })
    init { contentDescription = "分类图标裁剪预览，可拖动及缩放" }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        base = maxOf(w.toFloat() / bitmap.width, h.toFloat() / bitmap.height)
        scale = base
        x = (w - bitmap.width * scale) / 2
        y = (h - bitmap.height * scale) / 2
    }
    private fun constrain() {
        x = x.coerceIn(minOf(0f, width - bitmap.width * scale), 0f)
        y = y.coerceIn(minOf(0f, height - bitmap.height * scale), 0f)
        invalidate()
    }
    fun zoom(factor: Float) {
        val next = (scale * factor).coerceIn(base, base * 5)
        val ratio = next / scale
        x = width / 2f - (width / 2f - x) * ratio
        y = height / 2f - (height / 2f - y) * ratio
        scale = next
        constrain()
    }
    override fun onDraw(canvas: Canvas) {
        drawImage(canvas)
        // A dark outer stroke plus a white inner stroke stays visible on light and dark images.
        val density = resources.displayMetrics.density
        val inset = 2f * density
        borderPaint.strokeWidth = 4f * density
        borderPaint.color = Color.BLACK
        canvas.drawRect(inset, inset, width - inset, height - inset, borderPaint)
        borderPaint.strokeWidth = 2f * density
        borderPaint.color = Color.WHITE
        canvas.drawRect(inset, inset, width - inset, height - inset, borderPaint)
    }
    private fun drawImage(canvas: Canvas) {
        canvas.drawColor(Color.WHITE)
        canvas.save(); canvas.translate(x, y); canvas.scale(scale, scale)
        canvas.drawBitmap(bitmap, 0f, 0f, paint); canvas.restore()
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        detector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_MOVE && !detector.isInProgress) {
            x += event.x - lastX; y += event.y - lastY; constrain()
        }
        lastX = event.x; lastY = event.y
        if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    fun export(): Bitmap {
        check(width > 0 && height > 0)
        return Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).also {
            val canvas = Canvas(it)
            canvas.scale(256f / width, 256f / height)
            // Export only image pixels; the crop guide is a preview overlay.
            drawImage(canvas)
        }
    }
}
