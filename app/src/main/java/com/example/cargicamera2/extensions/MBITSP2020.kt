package com.example.cargicamera2.extensions

class MBITSP2020 {
    val header = byteArrayOf(
        'M'.toByte(), 'B'.toByte(),
        'I'.toByte(), 'T'.toByte(), 'S'.toByte(), 'P'.toByte(), 2, 0, 2, 0
    )

    enum class Pattern {
        CONTRAST_2, CONTRAST_4, REFRESH_RATE, COLOR_TEMPERATURE
    }

    enum class GrayScaleSets {
        GRAY_SCALE_1, GRAY_SCALE_2, GRAY_SCALE_3, GRAY_SCALE_4
    }

    private lateinit var pattern: Pattern
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

    fun getPattern(): ByteArray {
        return when (pattern) {
            Pattern.CONTRAST_2 -> byteArrayOf(0)
            Pattern.CONTRAST_4 -> byteArrayOf(1)
            Pattern.REFRESH_RATE -> byteArrayOf(2)
            Pattern.COLOR_TEMPERATURE -> byteArrayOf(3)
            else -> byteArrayOf(4)
        }
    }

    fun setPattern(pattern: Pattern) {
        this.pattern = pattern
    }

    fun getDisplayWidth(): ByteArray {
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

    fun getDisplayHeight(): ByteArray {
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

    fun getDisplayStartX(): ByteArray {
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

    fun getDisplayStartY(): ByteArray {
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

    fun getModuleWidth(): ByteArray {
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

    fun getModuleHeight(): ByteArray {
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

    fun getGraySacle(pattern: Pattern): ByteArray {
        when (pattern) {
            Pattern.CONTRAST_2 -> {
                setGrayScale(GrayScaleSets.GRAY_SCALE_1, 0, 0, 0)
                setGrayScale(GrayScaleSets.GRAY_SCALE_2, 0, 0, 0)
                setGrayScale(GrayScaleSets.GRAY_SCALE_3, 0, 0, 0)
                setGrayScale(GrayScaleSets.GRAY_SCALE_4, 0, 0, 0)
            }
            Pattern.CONTRAST_4 -> {
                setGrayScale(GrayScaleSets.GRAY_SCALE_1, 0, 0, 0)
                setGrayScale(GrayScaleSets.GRAY_SCALE_2, 0, 0, 0)
                setGrayScale(GrayScaleSets.GRAY_SCALE_3, 0, 0, 0)
                setGrayScale(GrayScaleSets.GRAY_SCALE_4, 0, 0, 0)
            }
            Pattern.REFRESH_RATE -> {
                setGrayScale(GrayScaleSets.GRAY_SCALE_1, 0, 0, 0)
                setGrayScale(GrayScaleSets.GRAY_SCALE_2, 0, 0, 0)
                setGrayScale(GrayScaleSets.GRAY_SCALE_3, 0, 0, 0)
                setGrayScale(GrayScaleSets.GRAY_SCALE_4, 0, 0, 0)
            }
            Pattern.COLOR_TEMPERATURE -> {
                setGrayScale(GrayScaleSets.GRAY_SCALE_1, 0, 0, 0)
                setGrayScale(GrayScaleSets.GRAY_SCALE_2, 0, 0, 0)
                setGrayScale(GrayScaleSets.GRAY_SCALE_3, 0, 0, 0)
                setGrayScale(GrayScaleSets.GRAY_SCALE_4, 0, 0, 0)
            }
        }
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
        setPattern(Pattern.CONTRAST_2)
        setDisplayWidth(1920)
        setDisplayHeight(1080)
        setDisplayStartX(19)
        setDisplayStartY(20)
        setModuleWidth(64)
        setModuleHeight(40)

        return header + getPattern() + getDisplayWidth() + getDisplayHeight() + getDisplayStartX() + getDisplayStartY() + getModuleWidth() + getModuleHeight() + getGraySacle(pattern)
    }
}
