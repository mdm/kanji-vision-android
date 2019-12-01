package io.migge.kanjijoshu

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import io.migge.kanjijoshu.R

/**
 * A custom image view where words can be marked by touch
 */
class TouchSelectView(context: Context, attrs: AttributeSet) : View(context, attrs) {
    private val bitmap: Bitmap
    private val dstRect = Rect()

    init {
        val inputStream = context.assets.open("20181212_191644_smallest.jpg")
        bitmap = BitmapFactory.decodeStream(inputStream)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.getClipBounds(dstRect)
        canvas.drawBitmap(bitmap, null, dstRect, null)
    }
}
