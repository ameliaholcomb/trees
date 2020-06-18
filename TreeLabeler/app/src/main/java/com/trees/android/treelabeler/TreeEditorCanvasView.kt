package com.trees.android.treelabeler

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View


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

    var labelRectangles : ArrayList<LabelRectangle> = ArrayList()

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        val image = treeImage
        if (image != null && canvas != null) {
            val imageWidth = image.width
            val imageHeight = image.height
            val widthOffset = (width - imageWidth).toFloat() / 2
            val heightOffset = (height - imageHeight).toFloat() / 2
            canvas.drawBitmap(image, widthOffset, heightOffset, null)
            for (labelRectangle in labelRectangles) {
                canvas.drawRect(labelRectangle.left, labelRectangle.top, labelRectangle.right, labelRectangle.bottom, paint)
            }
        }
    }
}