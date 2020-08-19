package com.trees.android.treelabeler

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
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

    private var scaleListener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            imageScaleMultiplier *= detector.scaleFactor

            // Don't let the object get too small or too large
            imageScaleMultiplier = Math.max(0.1f, Math.min(imageScaleMultiplier, 5.0f))

            invalidate()
            return true
        }
    }

    private val scaleDetector = ScaleGestureDetector(context, scaleListener)

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        // Let the ScaleGestureDetector inspect all events.
        scaleDetector.onTouchEvent(ev)
        return true
    }

    var labelRectangles : ArrayList<LabelRectangle> = ArrayList()

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        val image = treeImage
        if (image != null && canvas != null) {
            canvas.scale(imageScaleMultiplier, imageScaleMultiplier)
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
}