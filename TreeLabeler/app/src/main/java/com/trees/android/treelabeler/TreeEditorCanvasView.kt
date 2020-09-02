package com.trees.android.treelabeler

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.view.GestureDetectorCompat
import com.google.android.material.snackbar.Snackbar
import kotlin.math.min


data class LabelRectangle(val left: Float, val top: Float, val right: Float, val bottom: Float)

class TreeEditorCanvasView(context: Context, attributeSet: AttributeSet) : View(context, attributeSet) {
    val paint : Paint = {
        -> val newPaint = Paint()
        newPaint.setStyle(Paint.Style.STROKE)
        newPaint
    }()

    var treeImage : Bitmap? = null
        set(value) {
            field = value
            invalidate()
        }

    private var imageScaleMultiplier : Float = 1f
    private var imageTranslateX : Float = 0f
    private var imageTranslateY : Float = 0f

    private var scaleListener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            imageScaleMultiplier *= detector.scaleFactor

            // Don't let the object get too small or too large
            imageScaleMultiplier = Math.max(1.0f, Math.min(imageScaleMultiplier, 5.0f))

            invalidate()
            return true
        }
    }

    private var panListener = object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            return true
        }

        override fun onScroll(
            e1: MotionEvent,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            // Scrolling uses math based on the viewport (as opposed to math using pixels).
            imageTranslateX -= distanceX
            imageTranslateY -= distanceY
            invalidate()
            return true
        }
    }

    private val scaleDetector = ScaleGestureDetector(context, scaleListener)
    private val panDetector = GestureDetectorCompat(context, panListener)

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        // Let the ScaleGestureDetector inspect all events.
        val scale_event = scaleDetector.onTouchEvent(ev)
        val pan_event = panDetector.onTouchEvent(ev)
        return scale_event || pan_event || super.onTouchEvent(ev)
    }

    var labelRectangles : ArrayList<LabelRectangle> = ArrayList()

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        val image = treeImage
        if (image != null && canvas != null) {
            canvas.scale(imageScaleMultiplier, imageScaleMultiplier)
            canvas.translate(imageTranslateX, imageTranslateY)
            val imageWidth = image.width
            val imageHeight = image.height
            val canvasViewHeight = height
            val canvasViewWidth = width
            val xRatio : Float = canvasViewWidth / imageWidth.toFloat()
            val yRatio : Float = canvasViewHeight / imageHeight.toFloat()
            val imageScaleRatio = min(xRatio, yRatio)
            canvas.scale(imageScaleRatio, imageScaleRatio)
            canvas.drawBitmap(image, 0f, 0f, null)
            for (labelRectangle in labelRectangles) {
                canvas.drawRect(labelRectangle.left, labelRectangle.top, labelRectangle.right, labelRectangle.bottom, paint)
            }
        }
    }

    fun onClickAddFab(view: View) {
        Snackbar.make(view, "Here's a Kitkat", Snackbar.LENGTH_LONG)
            .setAction("Action", null)
            .show()
    }
}