package io.migge.kanjijoshu

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import io.migge.kanjijoshu.R
import org.json.JSONObject

/**
 * A custom image view where words can be marked by touch
 */
class TouchSelectView(context: Context, attrs: AttributeSet) : View(context, attrs) {
    private val bitmap: Bitmap
    private val dstRect = Rect()
    private val boundingBoxes = ArrayList<Path>()
    private var highlight: Path? = null

    init {
        val bitmapInputStream = context.assets.open("20181212_191644_smallest.jpg")
        bitmap = BitmapFactory.decodeStream(bitmapInputStream)

        val jsonInputStream = context.assets.open("output_pretty.json")
        val size = jsonInputStream.available()
        val buffer = ByteArray(size)
        jsonInputStream.use { it.read(buffer) }
        val jsonString = String(buffer)

        val rootObject = JSONObject(jsonString)
        val responsesArray = rootObject.getJSONArray("responses")

        // full text annotations
        for (response in 0 until responsesArray.length()) {
            val pagesArray = responsesArray.getJSONObject(response)
                .getJSONObject("fullTextAnnotation")
                .getJSONArray("pages")

            for (page in 0 until pagesArray.length()) {
                val blocksArray = pagesArray.getJSONObject(page)
                    .getJSONArray("blocks")

                for (block in 0 until blocksArray.length()) {
                    val paragraphsArray = blocksArray.getJSONObject(block)
                        .getJSONArray("paragraphs")

                    for (paragraph in 0 until paragraphsArray.length()) {
                        val wordsArray = paragraphsArray.getJSONObject(paragraph)
                            .getJSONArray("words")

                        for (word in 0 until wordsArray.length()) {
                            val verticesArray = wordsArray.getJSONObject(word)
                                .getJSONObject("boundingBox")
                                .getJSONArray("vertices")

                            val path = Path()

                            val firstVertexObject = verticesArray.getJSONObject(0)
                            path.moveTo(
                                1.0f * firstVertexObject.getInt("x"),
                                1.0f * firstVertexObject.getInt("y")
                            )
                            for (vertex in 1 until verticesArray.length()) {
                                val vertexObject = verticesArray.getJSONObject(vertex)
                                path.lineTo(
                                    1.0f * vertexObject.getInt("x"),
                                    1.0f * vertexObject.getInt("y")
                                )
                            }

                            path.lineTo(
                                1.0f * firstVertexObject.getInt("x"),
                                1.0f * firstVertexObject.getInt("y")
                            )
                            boundingBoxes.add(path)
                        }
                    }
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.getClipBounds(dstRect)
        canvas.drawBitmap(bitmap, null, dstRect, null)

        val scaleX = 1.0f * (dstRect.right - dstRect.left) / bitmap.width
        val scaleY = 1.0f * (dstRect.bottom - dstRect.top) / bitmap.height
        val matrix = Matrix()
        matrix.setScale(scaleX, scaleY)

        val boundingBoxPaint = Paint()
        boundingBoxPaint.style = Paint.Style.STROKE
        boundingBoxPaint.color = Color.RED
        boundingBoxPaint.strokeWidth = 5.0f
        for (boundingBox in boundingBoxes) {
            val transformed = Path(boundingBox)
            transformed.transform(matrix)
            canvas.drawPath(transformed, boundingBoxPaint)
        }

        if (highlight != null) {
            val highlightPaint = Paint()
            highlightPaint.style = Paint.Style.STROKE
            highlightPaint.color = Color.WHITE
            highlightPaint.strokeWidth = 50.0f
            highlightPaint.strokeJoin = Paint.Join.ROUND
            highlightPaint.strokeCap = Paint.Cap.ROUND
            canvas.drawPath(highlight!!, highlightPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        when (event?.action) {
            MotionEvent.ACTION_DOWN -> {
                highlight = Path()
                highlight!!.moveTo(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> {
                highlight?.lineTo(event.x, event.y)
            }
            MotionEvent.ACTION_UP -> {
                // extract characters
            }
            else -> {
                return super.onTouchEvent(event)
            }
        }

        invalidate()
        return true
    }
}
