package com.example.customedittext

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.DrawableContainer
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.HandlerThread
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.doAfterTextChanged
import kotlinx.android.synthetic.main.layout_custom_edittext.view.*

class SSCustomEdittextOutlinedBorder @JvmOverloads constructor(context: Context, attrs: AttributeSet?, defStyle: Int = 0, defStyleRes: Int = 0): LinearLayout(context, attrs, defStyle, defStyleRes) {
    private var titleColor = ContextCompat.getColor(context, R.color.color_brownish_grey_two)
    private var titleErrorColor = ContextCompat.getColor(context, R.color.color_error)
    private var borderColor = ContextCompat.getColor(context, R.color.color_warm_grey)
    private var borderErrorColor = ContextCompat.getColor(context, R.color.color_error)
    private var borderWidth = 1
    private var maxValue = 0
    private var minValue = 0
    private var isEditable = true

    private val changeNumberThread = HandlerThread("readCommandThread").apply { start() }
    private val changeNumberHandler = Handler(changeNumberThread.looper)

    val getTextValue: String
        get() {
            return editText.text.toString()
        }

    init {
        LayoutInflater.from(context).inflate(R.layout.layout_custom_edittext, this, true)
        orientation = VERTICAL

        attrs?.let {
            val typedArray = context.obtainStyledAttributes(it, R.styleable.custom_component_attributes, 0, 0)
            val editTextHint = resources.getText(typedArray.getResourceId(R.styleable.custom_component_attributes_custom_component_editText_hint, R.string.app_name))
            val isErrorEnable = typedArray.getBoolean(R.styleable.custom_component_attributes_isErrorEnable, false)
            val inputType = typedArray.getInt(R.styleable.custom_component_attributes_android_inputType, EditorInfo.TYPE_TEXT_VARIATION_NORMAL)
            val maxLine = typedArray.getInt(R.styleable.custom_component_attributes_custom_component_maxline, 1)
            val minLine = typedArray.getInt(R.styleable.custom_component_attributes_custom_component_minline, 1)
            val maxLength = typedArray.getInt(R.styleable.custom_component_attributes_custom_component_maxLength, 99)
            val editTextBgColor = typedArray.getColor(R.styleable.custom_component_attributes_custom_component_editText_bg_color, ContextCompat.getColor(context, R.color.colorPrimary))
            val errorTextBgColor = typedArray.getColor(R.styleable.custom_component_attributes_custom_component_error_text_bg_color, ContextCompat.getColor(context, R.color.colorPrimary))
            titleColor = typedArray.getColor(R.styleable.custom_component_attributes_custom_component_title_color, ContextCompat.getColor(context, R.color.color_brownish_grey_two))
            titleErrorColor = typedArray.getColor(R.styleable.custom_component_attributes_custom_component_title_error_color, ContextCompat.getColor(context, R.color.color_error))
            borderColor = typedArray.getColor(R.styleable.custom_component_attributes_custom_component_border_color, ContextCompat.getColor(context, R.color.color_warm_grey))
            borderErrorColor = typedArray.getColor(R.styleable.custom_component_attributes_custom_component_border_error_color, ContextCompat.getColor(context, R.color.color_error))
            borderWidth = typedArray.getInt(R.styleable.custom_component_attributes_custom_component_border_width, 1)
            maxValue = typedArray.getInt(R.styleable.custom_component_attributes_custom_component_maxValue, 256)
            minValue = typedArray.getInt(R.styleable.custom_component_attributes_custom_component_minValue, 0)
            setEditTextHint(editTextHint as String)
            setTextStyle(ResourcesCompat.getFont(context, R.font.graphik_regular))
            setIsErrorEnable(isErrorEnable)
            setStyle(inputType, maxLine, minLine, maxLength)
            setEditTextBackGroundColor(editTextBgColor)
            setErrorTextBackGroundColor(errorTextBgColor);
            setTextValue(maxValue.toString())
            typedArray.recycle()
        }

        btnDown.setOnClickListener {
            if (isEditable) {                      //20200824 Craig for disable custom edit text
                var number = editText.text.toString().toInt()
                if (number > minValue) {
                    number -= 1
                    editText.setText(number.toString())
                    editText.setSelection(number.toString().count())
                }
            }
        }

        btnUp.setOnClickListener {
            if (isEditable) {                      //20200824 Craig for disable custom edit text
                var number = editText.text.toString().toInt()
                if (number < maxValue) {
                    number += 1
                    editText.setText(number.toString())
                    editText.setSelection(number.toString().count())
                }
            }
        }

        editText.filters = arrayOf<InputFilter>(InputFilterMinMax(minValue, maxValue))

        var beforeText = "0"
        editText.doAfterTextChanged {
            if (isEditable) {                    //20200824 Craig for disable custom edit text
                if (it.isNullOrBlank()) {
                    modifyText("0")
                    return@doAfterTextChanged
                }
                val originalText = it.toString()
                try {
                    val numberText = originalText.toInt().toString()
                    if (originalText != numberText) {
                        modifyText(numberText)
                    }
                    if (numberText.toInt() > maxValue) {
                        modifyText(maxValue.toString())
                    }
//                if (numberText.toInt() < minValue) {
//                    modifyText(minValue.toString())
//                }
                } catch (e: Exception) {
                    modifyText("0")
                }
            }
        }

        editText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                Log.i("1 afterTextChanged", s.toString())
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                beforeText = s.toString()
                Log.i("2 beforeTextChanged", s.toString())
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
//                if (beforeText.length > 1 && beforeText.contains("0")) {
//                    editText.setText(editText.text.toString().replace("0", " "))
//                    editText.setSelection(1)
//                }
//
//                if (s.toString().toInt0() < minValue || s.toString() == "")
//                    editText.setText(minValue.toString())
//                else if (s.toString().toInt0() > maxValue || s.toString().count() > maxValue.toString().count()){
//                    editText.setText(maxValue.toString())
//                    editText.setSelection(maxValue.toString().count())
//                }

                Log.i("3 onTextChanged", "${s.toString()}, start: $start, before: $before, count: $count")
            }
        })

        editText.setOnFocusChangeListener { _, hasFocus ->
            if (isEditable) {           //20200824 Craig for disable custom edit text
                if (!hasFocus) {
                    Log.i("onFocusChange", "lost focus")

                    val inputManager = context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
                    inputManager.hideSoftInputFromWindow(windowToken, 0)
                    if (editText.text.toString().toInt() < minValue)
                        modifyText(minValue.toString())

                    editText.clearFocus()
                }
            }
        }


        editText.setOnEditorActionListener(object: TextView.OnEditorActionListener {
            override fun onEditorAction(v: TextView?, actionId: Int, event: KeyEvent?): Boolean {
                if (isEditable) {            //20200824 Craig for disable custom edit text
                    when (actionId) {
                        EditorInfo.IME_ACTION_DONE, EditorInfo.IME_ACTION_NEXT, EditorInfo.IME_ACTION_PREVIOUS -> {
                            if (editText.text.toString().toInt() < minValue)
                                modifyText(minValue.toString())
                            Log.i("SSCustomEditText", "onEditorAction")
                            return false
                        }
                    }
                }
                return false
            }
        })
    }

    private fun modifyText(numberText: String) {
        editText.setText(numberText)
        editText.setSelection(numberText.length)
    }

    fun String.toInt0() = try {
        toInt()
    } catch (e: NumberFormatException) {
        0
    }

    fun setTextValue(value: String?) {
        value?.let {
            editText.setText(value)
            editText.setSelection(value.length)
        }
    }

    fun setIsErrorEnable(isShown: Boolean) {
        if (isShown) {
            setBackgroundBorderErrorColor(borderErrorColor)
        } else {
            setBackgroundBorderErrorColor(borderColor)
        }
    }

    fun setIsEditable(editable: Boolean) {
        isEditable = editable

        editText.isEnabled = editable
    }

    fun setErrorTextBackGroundColor(@ColorInt colorID: Int) {
    }

    fun setEditTextBackGroundColor(@ColorInt colorID: Int) {
        val drawable = editText.background as StateListDrawable
        val dcs = drawable.constantState as DrawableContainer.DrawableContainerState?
        val drawableItems = dcs!!.children
        val gradientDrawableChecked = drawableItems[0] as GradientDrawable
        gradientDrawableChecked.setColor(colorID)
    }

    fun setEditTextHint(hint: String) {
        editText.hint = hint
    }

    fun setStyle(inputType: Int, maxLine: Int, minLine: Int, maxLength: Int) {
        editText.inputType = inputType
        editText.apply {
            maxLines = maxLine
            minLines = minLine
            gravity = Gravity.TOP or Gravity.START
            filters = arrayOf(InputFilter.LengthFilter(maxLength))
        }
    }

    fun setBackgroundBorderErrorColor(@ColorInt colorID: Int) {
        val drawable = editText.background as StateListDrawable
        val dcs = drawable.constantState as DrawableContainer.DrawableContainerState?
        val drawableItems = dcs!!.children
        val gradientDrawableChecked = drawableItems[0] as GradientDrawable
        gradientDrawableChecked.setStroke(borderWidth, colorID)
    }

    fun setTextStyle(textStyle: Typeface?) {
        editText.typeface = textStyle
    }

    fun getTextValue(): String? {
        return editText.text.toString()
    }

    override fun clearFocus() {
        editText.clearFocus()
    }
}