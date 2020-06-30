package com.example.cargicamera2.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import android.util.AttributeSet
import android.view.View
import androidx.annotation.RequiresApi

class CornerOfRectangleView  @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        val paint: Paint = Paint(Paint.DITHER_FLAG)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 50f
        paint.color = 0x6260f542.toInt()

        paint.strokeJoin = Paint.Join.MITER
        canvas!!.drawPath(createCornersPath(0, 0, width, height, 100), paint)
    }

    private fun createCornersPath(left: Int, top: Int, right: Int, bottom: Int, cornerWidth: Int): Path {
        val path: Path = Path()

        path.moveTo(left.toFloat(), (top + cornerWidth).toFloat())
        path.lineTo(left.toFloat(), top.toFloat())
        path.lineTo((left + cornerWidth).toFloat(), top.toFloat())

        path.moveTo((right - cornerWidth).toFloat(), top.toFloat())
        path.lineTo(right.toFloat(), top.toFloat())
        path.lineTo(right.toFloat(), (top + cornerWidth).toFloat())

        path.moveTo(left.toFloat(), (bottom - cornerWidth).toFloat())
        path.lineTo(left.toFloat(), bottom.toFloat())
        path.lineTo((left + cornerWidth).toFloat(), bottom.toFloat())

        path.moveTo((right - cornerWidth).toFloat(), bottom.toFloat())
        path.lineTo(right.toFloat(), bottom.toFloat())
        path.lineTo(right.toFloat(), (bottom - cornerWidth).toFloat())
        return path
    }
}