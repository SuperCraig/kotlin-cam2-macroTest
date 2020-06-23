package com.example.toast

import android.R.attr
import android.content.Context
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.widget.Toast


class Toast(context: Context, toastView: ToastView) {

    // -----類別常數-----
    /**
     * 短時間訊息
     */
    val SHORT: Int = android.widget.Toast.LENGTH_SHORT
    /**
     * 長時間訊息
     */
    val LONG: Int = android.widget.Toast.LENGTH_LONG

    /**
     * 類別位置
     *
     * @author magiclen
     *
     */
    enum class Position {
        CENTER, CENTER_BOTTOM, CENTER_TOP, CENTER_LEFT, CENTER_RIGHT, LEFT_TOP, RIGHT_TOP, LEFT_BOTTOM, RIGHT_BOTTOM
    }

    /**
     * 調整Toast與螢幕邊界的距離，數值愈大距離愈遠
     */
    val offsetFactor: Int = 12

    // -----類別變數-----
    /**
     * 儲存Context的參考
     */
    var context: Context = context

    var toast: android.widget.Toast = Toast(context)

    var toastPosition: Position = Position.CENTER_BOTTOM

    var offsetX: Int = 0

    var offsetY: Int = 0

    var toastView: ToastView? = null

    init {
        this.toastView = toastView
    }

    fun setPosition(position: Position) {
        if (position != null) {
            toastPosition = position;
        } else {
            toastPosition = Position.CENTER_BOTTOM;
        }
        useDefaultOffset();
    }

    fun setPosition(position: Position, offsetX: Int, offsetY: Int) {
        if (position != null) {
            toastPosition = position;
        } else {
            toastPosition = Position.CENTER_BOTTOM;
        }
        setOffset(offsetX, offsetY);
    }

    fun useDefaultOffset() {
        val dm: DisplayMetrics = context.getResources().getDisplayMetrics();
        setOffset(dm.widthPixels / offsetFactor, dm.heightPixels / offsetFactor);
    }

    fun setOffset(offsetX: Int, offsetY: Int) {
        this.offsetX = offsetX;
        this.offsetY = offsetY;
    }

    fun showToast(text: String) {
        showToast(text, android.widget.Toast.LENGTH_SHORT)
    }

    fun makeText(text: String, duration: Int): android.widget.Toast {
        return if (toastView != null) {
            val toast = android.widget.Toast(context)
            toastView!!.setText(text)
            toast.duration = duration
            toast.view = toastView
            toast
        } else {
            android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT)
        }
    }

    fun showToast(text: String, duration: Int) {
        if (toast == null) {
            // 如果Toast物件不存在，就用makeText方法建立一個新的Toast
            toast = makeText(text, duration)
        } else {
            // 如果Toast物件存在，就重設它的持續時間和訊息文字
            toast.setDuration(duration)
            if (toastView == null) {
                toast.setText(text)
            } else {
                toastView!!.setText(text)
            }
        }

        // 設定氣泡訊息的位置

        // 設定氣泡訊息的位置
        var gravity = 0
        var offsetX = 0
        var offsetY = 0
        when (toastPosition) {
            Position.CENTER -> gravity = Gravity.CENTER
            Position.CENTER_BOTTOM -> {
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
                offsetY = this.offsetY
            }
            Position.CENTER_TOP -> {
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
                offsetY = this.offsetY
            }
            Position.CENTER_LEFT -> {
                gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
                offsetX = this.offsetX
            }
            Position.CENTER_RIGHT -> {
                gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
                offsetX = this.offsetX
            }
            Position.LEFT_TOP -> {
                gravity = Gravity.LEFT or Gravity.TOP
                offsetX = this.offsetX
                offsetY = this.offsetY
            }
            Position.LEFT_BOTTOM -> {
                gravity = Gravity.LEFT or Gravity.BOTTOM
                offsetX = this.offsetX
                offsetY = this.offsetY
            }
            Position.RIGHT_TOP -> {
                gravity = Gravity.RIGHT or Gravity.TOP
                offsetX = this.offsetX
                offsetY = this.offsetY
            }
            Position.RIGHT_BOTTOM -> {
                gravity = Gravity.RIGHT or Gravity.BOTTOM
                offsetX = this.offsetX
                offsetY = this.offsetY
            }
        }
        toast.setGravity(gravity, offsetX, offsetY)
        // 顯示出氣泡訊息
        // 顯示出氣泡訊息
        toast.view = this.toastView
        toast.show()
    }
}