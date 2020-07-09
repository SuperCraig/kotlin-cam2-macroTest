package com.example.craigCam2.extensions

import android.graphics.Color
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red

class MBITSP2020 {
    private val header = byteArrayOf(
        'M'.toByte(), 'B'.toByte(),
        'I'.toByte(), 'T'.toByte(), 'S'.toByte(), 'P'.toByte(), 0x32, 0x30, 0x32, 0x30
    )

    enum class Pattern {
        CONTRAST_2, CONTRAST_4, REFRESH_RATE, COLOR_TEMPERATURE
    }

    enum class GrayScaleSets {
        GRAY_SCALE_1, GRAY_SCALE_2, GRAY_SCALE_3, GRAY_SCALE_4
    }

    /**
                    Mode0          Mode1          Mode2          Mode3
     GrayScale1-R   v              v              v              v
     GrayScale1-G   v              v              v              v
     GrayScale1-B   v              v              v              v
     GrayScale2-R   v              v              x              v
     GrayScale2-G   v              v              x              v
     GrayScale2-B   v              v              x              v
     GrayScale3-R   x              v              x              v
     GrayScale3-G   x              v              x              v
     GrayScale3-B   x              v              x              v
     GrayScale4-R   x              v              x              v
     GrayScale4-G   x              v              x              v
     GrayScale4-B   x              v              x              v
     */
    enum class Mode {
        MODE0, MODE1, MODE2, MODE3
    }

    private lateinit var pattern: Pattern
    private lateinit var mode: Mode
    private lateinit var displayWidth: ByteArray
    private lateinit var displayHeight: ByteArray
    private lateinit var displayStartX: ByteArray
    private lateinit var displayStartY: ByteArray
    private lateinit var moduleWidth: ByteArray
    private lateinit var moduleHeight: ByteArray
    private lateinit var grayScale1: ByteArray
    private lateinit var grayScale2: ByteArray
    private lateinit var grayScale3: ByteArray
    private lateinit var grayScale4: ByteArray

    init {
        pattern = Pattern.CONTRAST_2
        setDisplayWidth(1920)
        setDisplayHeight(1080)
        setDisplayStartX(0)
        setDisplayStartY(0)
        setModuleWidth(64)
        setModuleHeight(40)
        setGrayScale(GrayScaleSets.GRAY_SCALE_1, 0, 0, 0)
        setGrayScale(GrayScaleSets.GRAY_SCALE_2, 0, 0, 0)
        setGrayScale(GrayScaleSets.GRAY_SCALE_3, 0, 0, 0)
        setGrayScale(GrayScaleSets.GRAY_SCALE_4, 0, 0, 0)
    }

    private fun getPattern(): ByteArray {
        return when (pattern) {
            Pattern.CONTRAST_2 -> byteArrayOf(0)
            Pattern.CONTRAST_4 -> byteArrayOf(1)
            Pattern.REFRESH_RATE -> byteArrayOf(2)
            Pattern.COLOR_TEMPERATURE -> byteArrayOf(3)
            else -> byteArrayOf(4)
        }
    }

    fun setMode(mode: Mode) {
       this.mode = mode
    }

    private fun getMode(): ByteArray {
        return when (mode) {
            Mode.MODE0 -> byteArrayOf(0)
            Mode.MODE1 -> byteArrayOf(1)
            Mode.MODE2 -> byteArrayOf(2)
            Mode.MODE3 -> byteArrayOf(3)
            else -> byteArrayOf(4)
        }
    }

    fun setPattern(pattern: Pattern) {
        this.pattern = pattern
    }

    private fun getDisplayWidth(): ByteArray {
        return displayWidth
    }

    fun setDisplayWidth(width: Int) {
        if (width <= 1920) {
            displayWidth = ByteArray(2)
            displayWidth[0] = ((width ushr 8) and 0x00FF).toByte()
            displayWidth[1] = (width and 0x00FF).toByte()
        } else {
            displayWidth = byteArrayOf(0, 0)
        }
    }

    private fun getDisplayHeight(): ByteArray {
        return displayHeight
    }

    fun setDisplayHeight(height: Int) {
        if (height <= 1080) {
            displayHeight = ByteArray(2)
            displayHeight[0] = ((height ushr 8) and 0x00FF).toByte()
            displayHeight[1] = (height and 0x00FF).toByte()
        } else {
            displayHeight = byteArrayOf(0, 0)
        }
    }

    private fun getDisplayStartX(): ByteArray {
        return displayStartX
    }

    fun setDisplayStartX(startX: Int) {
        if (startX <= 1920) {
            displayStartX = ByteArray(2)
            displayStartX[0] = ((startX ushr 8) and 0x00FF).toByte()
            displayStartX[1] = (startX and 0x00FF).toByte()
        } else {
            displayStartX = byteArrayOf(0, 0)
        }
    }

    private fun getDisplayStartY(): ByteArray {
        return displayStartY
    }

    fun setDisplayStartY(startY: Int) {
        if (startY <= 1080) {
            displayStartY = ByteArray(2)
            displayStartY[0] = ((startY ushr 8) and 0x00FF).toByte()
            displayStartY[1] = (startY and 0x00FF).toByte()
        } else {
            displayStartY = byteArrayOf(0, 0)
        }
    }

    private fun getModuleWidth(): ByteArray {
        return moduleWidth
    }

    fun setModuleWidth(width: Int) {
        if (width <= 1920) {
            moduleWidth = ByteArray(2)
            moduleWidth[0] = ((width ushr 8) and 0x00FF).toByte()
            moduleWidth[1] = (width and 0x00FF).toByte()
        } else {
            moduleWidth = byteArrayOf(0, 0)
        }
    }

    private fun getModuleHeight(): ByteArray {
        return moduleHeight
    }

    fun setModuleHeight(height: Int) {
        if (height <= 1080) {
            moduleHeight = ByteArray(2)
            moduleHeight[0] = ((height ushr 8) and 0x00FF).toByte()
            moduleHeight[1] = (height and 0x00FF).toByte()
        } else {
            moduleHeight = byteArrayOf(0, 0)
        }
    }

    private fun getGrayScale(pattern: Pattern): ByteArray {
//        when (pattern) {
//            Pattern.CONTRAST_2 -> {
//                setGrayScale(GrayScaleSets.GRAY_SCALE_1, grayScale1[0], grayScale1[1], grayScale1[2])
//                setGrayScale(GrayScaleSets.GRAY_SCALE_2, grayScale2[0], grayScale2[1], grayScale2[2])
//                setGrayScale(GrayScaleSets.GRAY_SCALE_3, 0, 0, 0)
//                setGrayScale(GrayScaleSets.GRAY_SCALE_4, 0, 0, 0)
//            }
//            Pattern.CONTRAST_4 -> {
//                setGrayScale(GrayScaleSets.GRAY_SCALE_1, grayScale1[0], grayScale1[1], grayScale1[2])
//                setGrayScale(GrayScaleSets.GRAY_SCALE_2, grayScale2[0], grayScale2[1], grayScale2[2])
//                setGrayScale(GrayScaleSets.GRAY_SCALE_3, grayScale3[0], grayScale3[1], grayScale3[2])
//                setGrayScale(GrayScaleSets.GRAY_SCALE_4, grayScale4[0], grayScale4[1], grayScale4[2])
//            }
//            Pattern.REFRESH_RATE -> {
//                setGrayScale(GrayScaleSets.GRAY_SCALE_1, grayScale1[0], grayScale1[1], grayScale1[2])
//                setGrayScale(GrayScaleSets.GRAY_SCALE_2, 0, 0, 0)
//                setGrayScale(GrayScaleSets.GRAY_SCALE_3, 0, 0, 0)
//                setGrayScale(GrayScaleSets.GRAY_SCALE_4, 0, 0, 0)
//            }
//            Pattern.COLOR_TEMPERATURE -> {
//                setGrayScale(GrayScaleSets.GRAY_SCALE_1, grayScale1[0], grayScale1[1], grayScale1[2])
//                setGrayScale(GrayScaleSets.GRAY_SCALE_2, grayScale2[0], grayScale2[1], grayScale2[2])
//                setGrayScale(GrayScaleSets.GRAY_SCALE_3, grayScale3[0], grayScale3[1], grayScale3[2])
//                setGrayScale(GrayScaleSets.GRAY_SCALE_4, grayScale4[0], grayScale4[1], grayScale4[2])
//            }
//        }
        return grayScale1 + grayScale2 + grayScale3 + grayScale4
    }

    fun setGrayScale(set: GrayScaleSets, r: Byte, g: Byte, b: Byte) {
        when (set) {
            GrayScaleSets.GRAY_SCALE_1 -> grayScale1 = byteArrayOf(r, g, b)
            GrayScaleSets.GRAY_SCALE_2 -> grayScale2 = byteArrayOf(r, g, b)
            GrayScaleSets.GRAY_SCALE_3 -> grayScale3 = byteArrayOf(r, g, b)
            GrayScaleSets.GRAY_SCALE_4 -> grayScale4 = byteArrayOf(r, g, b)
        }
    }

    fun composeCommand(): ByteArray {
//        return header + getPattern() + getDisplayWidth() + getDisplayHeight() + getDisplayStartX() + getDisplayStartY() + getModuleWidth() + getModuleHeight() + getGrayScale(pattern)
        return header + getMode() + getDisplayWidth() + getDisplayHeight() + getDisplayStartX() + getDisplayStartY() + getModuleWidth() + getModuleHeight() + getGrayScale(pattern)
    }

    fun decomposeCommand(bytes: ByteArray): Boolean {
        var index = 0
        for (i in 0..header.size) {
            if (header[i] != bytes[i]) return false
            index += 1
        }
        return bytes[index + 1] == 0x06.toByte()
    }

    fun produceCommand(mode: Mode, displayW: Int, displayH: Int, displayStartX: Int, displayStartY: Int, moduleW: Int, moduleH: Int,
                       color1: Int, color2: Int, color3: Int, color4: Int): ByteArray {
        return let {
            it.setMode(mode)
            it.setDisplayWidth(displayW)
            it.setDisplayHeight(displayH)
            it.setDisplayStartX(displayStartX)
            it.setDisplayStartY(displayStartY)
            it.setModuleWidth(moduleW)
            it.setModuleHeight(moduleH)
            it.setGrayScale(MBITSP2020.GrayScaleSets.GRAY_SCALE_1, color1.red.toByte(), color1.green.toByte(), color1.blue.toByte())
            it.setGrayScale(MBITSP2020.GrayScaleSets.GRAY_SCALE_2, color2.red.toByte(), color2.green.toByte(), color2.blue.toByte())
            it.setGrayScale(MBITSP2020.GrayScaleSets.GRAY_SCALE_3, color3.red.toByte(), color3.green.toByte(), color3.blue.toByte())
            it.setGrayScale(MBITSP2020.GrayScaleSets.GRAY_SCALE_4, color4.red.toByte(), color4.green.toByte(), color4.blue.toByte())
            it.composeCommand()
        }
    }
}
