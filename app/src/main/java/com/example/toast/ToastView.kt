package com.example.toast

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.TextView

class ToastView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private val DEFAULT_TEXT_SIZE = 18;
    private val DEFAULT_TEXT_COLOR = Color.argb(0xAA, 0xFF, 0xFF, 0xFF)
    private val DEFAULT_PADDING = 30;
    private val DEFAULT_BACKGROUND_COLOR = Color.argb(0xAA, 0xFF, 0x00, 0x00)

    private val DEFAULT_RADIUS = 20

    private var text: TextView? = null
    private val background: GradientDrawable = GradientDrawable()

    init {
        text  = TextView(context)
        val flp: FrameLayout.LayoutParams = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT
        )

        text?.layoutParams = flp
        text?.isSingleLine = false
        text?.id = android.R.id.message

        text?.textSize = DEFAULT_TEXT_SIZE.toFloat()
        text?.setTextColor(DEFAULT_TEXT_COLOR)

        addView(text)
        setPadding(DEFAULT_PADDING, DEFAULT_PADDING, DEFAULT_PADDING, DEFAULT_PADDING)

        background.setColor(DEFAULT_BACKGROUND_COLOR)
        background.cornerRadius = DEFAULT_RADIUS.toFloat()

        setBackgroundDrawable(background)
    }

    fun setTextSize(size: Float) {
        text?.textSize = size
    }

    fun setTextSize(unit: Int, size: Float) {
        text?.setTextSize(unit, size)
    }

    fun setTextColor(color: Int) {
        text?.setTextColor(color)
    }

    override fun setBackgroundColor(color: Int) {
        background.setColor(color)
    }

    fun setRadius(radius: Float) {
        background.cornerRadius = radius
    }

    fun setPadding(padding: Int) {
        setPadding(padding, padding, padding, padding)
    }

    fun setText(textString: String) {
        if (textString != null)
            text?.text = textString
        else
            text?.text = ""
    }
}