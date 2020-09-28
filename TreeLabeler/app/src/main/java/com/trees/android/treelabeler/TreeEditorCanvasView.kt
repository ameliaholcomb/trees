package com.trees.android.treelabeler

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.view.GestureDetectorCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlin.math.max
import kotlin.math.min


data class LabelRectangle(var left: Float, var top: Float, var right: Float, var bottom: Float)

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

    var csvFileName: String? = null

    private var imageScaleMultiplier : Float = 1f
    private var imageScaleRatio : Float = 1f
    private var imageTranslateX : Float = 0f
    private var imageTranslateY : Float = 0f

    private var isEditMode : Boolean = false

    private var add_rect_x1 : Float = 0f
    private var add_rect_y1 : Float = 0f
    private var add_rect_x2 : Float = 0f
    private var add_rect_y2 : Float = 0f

    private var edit_rect : LabelRectangle = LabelRectangle(0f, 0f, 0f, 0f)


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
        var scale_event = false
        var drag_event = false
        if (!isEditMode) {
            scale_event = scaleDetector.onTouchEvent(ev)
            drag_event = panDetector.onTouchEvent(ev)
        } else {
            val action: Int = ev.getAction()
            val x = ev.x
            val y = ev.y
            when (action) {
                MotionEvent.ACTION_DOWN -> {
                    add_touch_start(x,y)
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    add_touch_move(x,y)
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    add_touch_up(x,y)
                    invalidate()
                    return true
                }
            }
        }
        return scale_event || drag_event || super.onTouchEvent(ev)
    }

    fun add_touch_start(x: Float, y: Float) {
        add_rect_x1 = x
        add_rect_y1 = y
        add_rect_x2 = x
        add_rect_y2 = y
        edit_rect = LabelRectangle(x, y, x, y)
    }

    fun add_touch_move(x: Float, y: Float) {
        add_rect_x2 = x
        add_rect_y2 = y
        val rect_left = min(add_rect_x1, add_rect_x2)
        val rect_right = max(add_rect_x1, add_rect_x2)
        val rect_top = min(add_rect_y1, add_rect_y2)
        val rect_bot = max(add_rect_y1, add_rect_y2)
        edit_rect.top = rect_top
        edit_rect.bottom = rect_bot
        edit_rect.left = rect_left
        edit_rect.right = rect_right
    }

    fun add_touch_up(x: Float, y: Float) {
        add_rect_x2 = x
        add_rect_y2 = y
        val rect_left = min(add_rect_x1, add_rect_x2)
        val rect_right = max(add_rect_x1, add_rect_x2)
        val rect_top = min(add_rect_y1, add_rect_y2)
        val rect_bot = max(add_rect_y1, add_rect_y2)
        edit_rect.top = (rect_top / imageScaleMultiplier - imageTranslateY) / imageScaleRatio
        edit_rect.bottom = (rect_bot / imageScaleMultiplier- imageTranslateY) / imageScaleRatio
        edit_rect.left = (rect_left / imageScaleMultiplier - imageTranslateX) / imageScaleRatio
        edit_rect.right = (rect_right / imageScaleMultiplier - imageTranslateX) / imageScaleRatio
        labelRectangles.add(edit_rect)
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
            imageScaleRatio = min(xRatio, yRatio)
            canvas.scale(imageScaleRatio, imageScaleRatio)
            canvas.drawBitmap(image, 0f, 0f, null)
            for (labelRectangle in labelRectangles) {
                canvas.drawRect(labelRectangle.left, labelRectangle.top, labelRectangle.right, labelRectangle.bottom, paint)
            }
        }
    }

    fun onClickAddFab(fab: FloatingActionButton, view: View) {
        if (isEditMode) {
            fab.setImageResource(R.drawable.ic_add_white_24dp)
        } else {
            fab.setImageResource(R.drawable.ic_check_white_24dp)
        }
        isEditMode = !isEditMode
    }
}