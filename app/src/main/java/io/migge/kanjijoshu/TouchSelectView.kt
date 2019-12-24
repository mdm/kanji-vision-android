package io.migge.kanjijoshu

import android.content.Context
import android.gesture.OrientedBoundingBox
import android.graphics.*
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import io.migge.kanjijoshu.R
import org.json.JSONException
import org.json.JSONObject

data class OCRArtifact(
    val text: String,
    val path: Path,
    val boundingBox: RectF,
    val children: ArrayList<OCRArtifact>
)

fun safeGetInt(jsonObject: JSONObject, name: String): Int {
    return try {
        jsonObject.getInt(name)
    } catch (e: JSONException) {
        0
    }
}

fun artifactFromJSONObject(jsonObject: JSONObject, children: ArrayList<OCRArtifact>): OCRArtifact {
    val text: String
    if (children.isEmpty()) {
        text = jsonObject.getString("text")
    } else {
        text = children.map { it.text }.joinToString()
    }


    val verticesArray = jsonObject
        .getJSONObject("boundingBox")
        .getJSONArray("vertices")


    val path = Path()

    val firstVertexObject = verticesArray.getJSONObject(0)
    path.moveTo(
        1.0f * safeGetInt(firstVertexObject, "x"),
        1.0f * safeGetInt(firstVertexObject, "y")
    )
    for (vertex in 1 until verticesArray.length()) {
        val vertexObject = verticesArray.getJSONObject(vertex)
        path.lineTo(
            1.0f * safeGetInt(vertexObject, "x"),
            1.0f * safeGetInt(vertexObject, "y")
        )
    }

    path.lineTo(
        1.0f * safeGetInt(firstVertexObject, "x"),
        1.0f * safeGetInt(firstVertexObject, "y")
    )

    var minX = Int.MAX_VALUE
    var minY = Int.MAX_VALUE
    var maxX = 0
    var maxY = 0
    for (vertex in 0 until verticesArray.length()) {
        val vertexObject = verticesArray.getJSONObject(vertex)
        val x = safeGetInt(vertexObject, "x")
        val y = safeGetInt(vertexObject, "y")

        if (x < minX) {
            minX = x
        }

        if (y < minY) {
            minY = y
        }

        if (x > maxX) {
            maxX = x
        }

        if (y > maxY) {
            maxY = y
        }
    }

    val boundingBox = RectF(1.0f * minX, 1.0f * minY, 1.0f * maxX, 1.0f * maxY)

    return OCRArtifact(text, path, boundingBox, children)
}


/**
 * A custom image view where words can be marked by touch
 */
class TouchSelectView(context: Context, attrs: AttributeSet) : View(context, attrs) {
    private val image: Bitmap
    private val artifacts = ArrayList<OCRArtifact>()
    private val artifactPaint = Paint()
    private val highlightPaint = Paint()
    private val highlightStrokeWidth = 50.0f

    private lateinit var imageWithBoundingBoxes: Bitmap
    private var toSourceSpace = Matrix()
    private var highlight: Path? = null

    init {
        val bitmapInputStream = context.assets.open("20181212_191644_smallest.jpg")
        image = BitmapFactory.decodeStream(bitmapInputStream)

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
                            Log.d(
                                "WORD",
                                block.toString() + " " + paragraph.toString() + " " + word.toString()
                            )
                            val wordObject = wordsArray.getJSONObject(word)

                            val children = ArrayList<OCRArtifact>()

                            val symbolsArray = wordObject
                                .getJSONArray("symbols")

                            for (symbol in 0 until symbolsArray.length()) {
                                Log.d("SYMBOL", symbol.toString())
                                val symbolObject = symbolsArray.getJSONObject(symbol)
                                children.add(artifactFromJSONObject(symbolObject, ArrayList()))
                            }

                            artifacts.add(artifactFromJSONObject(wordObject, children))
                        }
                    }
                }
            }
        }

        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.color = Color.WHITE
        highlightPaint.strokeWidth = highlightStrokeWidth
        highlightPaint.strokeJoin = Paint.Join.ROUND
        highlightPaint.strokeCap = Paint.Cap.ROUND

        artifactPaint.style = Paint.Style.STROKE
        artifactPaint.color = Color.RED
        artifactPaint.strokeWidth = 5.0f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawBitmap(imageWithBoundingBoxes, 0.0f, 0.0f, null)

        if (highlight != null) {
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

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        imageWithBoundingBoxes = Bitmap.createScaledBitmap(image, w, h, true)

        toSourceSpace.setScale(1.0f * image.width / w, 1.0f * image.height / h)

        val toDestinationSpace = Matrix()
        toDestinationSpace.setScale(1.0f * w / image.width, 1.0f * h / image.height)

        val canvas = Canvas(imageWithBoundingBoxes)

        for (artifact in artifacts) {
            val transformed = Path(artifact.path)
            transformed.transform(toDestinationSpace)
            canvas.drawPath(transformed, artifactPaint)
        }

        super.onSizeChanged(w, h, oldw, oldh)
    }
}
