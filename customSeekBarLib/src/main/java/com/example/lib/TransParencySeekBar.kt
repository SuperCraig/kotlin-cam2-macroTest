package com.example.lib

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class TransParencySeekBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), ProgressListener {

    private val a = attrs.let{ context.obtainStyledAttributes(attrs, R.styleable.TransParencySeekBar, defStyleAttr, defStyleAttr) }

    private var thumbX: Float = 0F
    private var touchX: Float = 0F

    val thumb: Drawable = a.getDrawable(R.styleable.TransParencySeekBar_tb_thumb)
        ?: resources.getDrawable(R.drawable.thumb)

    val right_triangle: Drawable = resources.getDrawable(R.drawable.right_triangle)

    val left_triangle: Drawable = resources.getDrawable(R.drawable.left_triangle)

    var progressBackgroundColor: Int = a.getColor(R.styleable.TransParencySeekBar_tb_backgroundColor, context.resources.getColor(R.color.backgroundColor))

    var progressColor: Int = a.getColor(R.styleable.TransParencySeekBar_tb_progressColor, context.resources.getColor(R.color.progressColor))

    var maxProgress: Int = a.getInteger(R.styleable.TransParencySeekBar_tb_maxProgress, 100)

    var progress: Int = a.getInteger(R.styleable.TransParencySeekBar_tb_progress, 0)

    var mEnable: Boolean = a.getBoolean(R.styleable.TransParencySeekBar_tb_enable, true)

    var thumbClickable: Boolean = a.getBoolean(R.styleable.TransParencySeekBar_tb_thumbClickable, false)

    var textSize: Float = a.getFloat(R.styleable.TransParencySeekBar_tb_textSize, 40F)

    var textColor: Int = a.getColor(R.styleable.TransParencySeekBar_tb_textColor, context.resources.getColor(R.color.textColor))

    var textBackgroundColor: Int = a.getColor(R.styleable.TransParencySeekBar_tb_textBackgroundColor, context.resources.getColor(R.color.textBackgroundColor))

    var text: String = a.getString(R.styleable.TransParencySeekBar_tb_text).toString()

    private var prevTouchX : Float = 0F

    init{
        a.recycle()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean{
        if(mEnable){
            when(event.action){
                MotionEvent.ACTION_DOWN ->{
                    isPressed = true
                }
                MotionEvent.ACTION_MOVE -> if(isPressed) changeProgress(event.x)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isPressed = false
                    touchX = 0F
                }
            }
        }
        return mEnable
    }

    @SuppressLint("DrawAllocation")
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = getDefaultSize(suggestedMinimumHeight, heightMeasureSpec)
        val width = getDefaultSize(suggestedMinimumWidth, widthMeasureSpec)

        thumbX = getThumbPosition()
        setMeasuredDimension(width, height)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        drawBackground(canvas)
        //drawProgress(canvas)
        putText(canvas)
//        if(mEnable){
//            if(!thumbClickable) drawThumb(canvas)
//            else if(thumbClickable && isPressed) drawThumb(canvas)
//        }
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
    }

    override fun invoke(progress: Int) {
        this.progress = progress
        thumbX = getThumbPosition()
        invalidate()
    }

    private fun drawBackground(canvas: Canvas){
        val paint: Paint = Paint()
        //paint.color = progressBackgroundColor

        val bitmap = BitmapFactory.decodeResource(resources, R.drawable.scale)

        canvas.drawBitmap(bitmap, null, Rect(0, 0, canvas.width.toInt(), canvas.height.toInt()),paint)
        //canvas.drawRect(0F, 0F, canvas.width.toFloat(), canvas.height.toFloat(), paint)
    }

    private fun drawProgress(canvas: Canvas){
        val paint: Paint = Paint()
        paint.color = progressColor

        canvas.drawRect(0.toFloat(), 0.toFloat(), thumbX, canvas.height.toFloat(), paint)
    }

    private fun putText(canvas: Canvas){
        val paint: Paint = Paint()
        paint.color = textColor
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = textSize * context.resources.displayMetrics.density

        //for adding text background
        val paintBack = Paint()
        val fm: Paint.FontMetrics = Paint.FontMetrics()
        paintBack.color = textBackgroundColor
        paintBack.getFontMetrics(fm)

        val xPos: Float = (canvas.width / 2).toFloat()
        val yPos: Float = ((canvas.height / 2) - ((paint.descent() + paint.ascent()) / 2))
        val yPosT: Float = ((canvas.height / 2) + ((paint.descent() + paint.ascent()) / 2))

        canvas.drawRect(xPos - paint.measureText(text)/2, yPosT,
            xPos + paint.measureText(text)/2, yPos, paintBack);

        canvas.drawText(text, xPos, yPos, paint)
    }

    private fun drawThumb(canvas: Canvas){
        thumb.setBounds(thumbX.toInt(), 0, thumbX.toInt() + 10, canvas.height)
        thumb.draw(canvas)
    }

    private fun drawRightTriangle(canvas: Canvas){
        var width = right_triangle.intrinsicWidth
        var height = right_triangle.intrinsicHeight
        right_triangle.setBounds(canvas.width - width, 0, canvas.width, height)
        right_triangle.draw(canvas)
    }

    private fun drawLeftTriangle(canvas: Canvas){
        left_triangle.setBounds(20, 0, 40, canvas.height)
        left_triangle.draw(canvas)
    }

    private fun getThumbPosition(): Float{
        val percent = (progress.toFloat() / maxProgress.toFloat()) * 100
        return (measuredWidth.toFloat() / 100) * percent
    }

    private fun changeProgress(x: Float){
        if(touchX == 0F) touchX = x
        else if(thumbX < 0) {
            thumbX = 0F
            progress = 0
        } else if(thumbX > measuredWidth) {
            thumbX = measuredWidth.toFloat()
            progress = maxProgress
        }else {
            progress = ((maxProgress.toFloat() / 100) * ((thumbX / measuredWidth) * 100)).toInt()
            thumbX += x - touchX

            if(x >= touchX) touchX += x - touchX
            else touchX -= touchX - x
        }

        invalidate()
    }
}