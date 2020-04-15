package com.example.cargicamera2.ui

import android.R
import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.view.View
import android.widget.RelativeLayout


class GridLineView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    override fun onDraw(canvas: Canvas) {
        val metrics: DisplayMetrics = Resources.getSystem().displayMetrics
        var screenWidth = metrics.widthPixels
        var screenHeight = metrics.heightPixels * 0.9

        val paint: Paint = Paint()
        paint.isAntiAlias = true
        paint.strokeWidth = 3F
        paint.style = Paint.Style.STROKE
        paint.color = Color.parseColor("#32a852")

        canvas.drawLine(((screenWidth/3)*2).toFloat(), 0F,
            ((screenWidth/3)*2).toFloat(), screenHeight.toFloat(),paint)
        canvas.drawLine(((screenWidth/3).toFloat()),0F,((screenWidth/3).toFloat()),
            screenHeight.toFloat(),paint)
        canvas.drawLine(0F, ((screenHeight/3)*2).toFloat(),
            screenWidth.toFloat(), ((screenHeight/3)*2).toFloat(),paint)
        canvas.drawLine(0F,((screenHeight/3).toFloat()),
            screenWidth.toFloat(),((screenHeight/3).toFloat()),paint)
    }
}