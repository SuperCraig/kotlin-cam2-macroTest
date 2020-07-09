package com.example.craigCam2.extensions

import android.hardware.camera2.CameraCharacteristics
import android.util.Log
import kotlin.math.abs

class RawConverter(pixelsBuffer: IntArray, width: Int, height: Int) {
    private val PixelsBuffer: IntArray = pixelsBuffer
    private val Width = width
    private val Height: Int = height

    enum class XGGX {
        RGGB, BGGR
    }

    enum class GXXG {
        GBRG, GRBG
    }

    fun debay(colorFilter: Int, x: Int, y: Int): IntArray {
        var pixels: IntArray = intArrayOf(0)
        when(colorFilter) {
            CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_BGGR -> pixels = debayXGGX(PixelsBuffer, x, y, XGGX.BGGR)
            CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGGB -> pixels = debayXGGX(PixelsBuffer, x, y, XGGX.RGGB)
            CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GBRG -> pixels = debayGXXG(PixelsBuffer, x, y, GXXG.GBRG)
            CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GRBG -> pixels = debayGXXG(PixelsBuffer, x, y, GXXG.GRBG)
        }
        return pixels
    }

    fun debayXGGX(pixels: IntArray, x: Int, y: Int, order: XGGX): IntArray {
        var case = 0
        case = if (x % 2 == 0 && y % 2 == 0)
            1
        else if (x % 2 == 1 && y % 2 == 0)
            2
        else if (x % 2 == 0 && y % 2 == 1)
            3
        else
            4

        when (case) {
            1 -> {     // PIXEL R
                var R = 0
                var G = 0
                var B = 0

                // get G
                val G1 = pixels[getPixel(x, y - 1)]
                val G2 = pixels[getPixel(x + 1, y)]
                val G3 = pixels[getPixel(x, y + 1)]
                val G4 = pixels[getPixel(x - 1, y)]

                // get R
                val R1 = pixels[getPixel(x, y - 2)]
                val R2 = pixels[getPixel(x + 2, y)]
                val R3 = pixels[getPixel(x, y + 2)]
                val R4 = pixels[getPixel(x - 2, y)]

                // get B
                val B1 = pixels[getPixel(x - 1, y - 1)]
                val B2 = pixels[getPixel(x + 1, y - 1)]
                val B3 = pixels[getPixel(x + 1, y + 1)]
                val B4 = pixels[getPixel(x - 1, y + 1)]

                // assign R
                R = pixels[getPixel(x, y)]

                // assign G
                if (abs(R1 - R3) < abs(R2 - R4))
                    G = (G1 + G3) / 2
                else if (abs(R1 - R3) > abs(R2 - R4))
                    G = (G2 + G4) / 2
                else
                    G = (G1 + G2 + G3 + G4) / 4

                // assign B
                B = (B1 + B2 + B3 + B4) / 4

                return when (order) {
                    XGGX.BGGR -> intArrayOf(B, G, R)
                    XGGX.RGGB -> intArrayOf(R, G, B)
                }
            }
            2 -> {        // PIXEL G1
                var R = 0
                var G = 0
                var B = 0

                // get R
                val R1 = pixels[getPixel(x - 1, y)]
                val R2 = pixels[getPixel(x + 1, y)]

                // get B
                val B1 = pixels[getPixel(x, y - 1)]
                val B2 = pixels[getPixel(x, y + 1)]

                // assign R
                R = (R1 + R2) / 2

                // assign G
                G = pixels[getPixel(x, y)]

                // assign B
                B = (B1 + B2) / 2

                return when (order) {
                    XGGX.BGGR -> intArrayOf(B, G, R)
                    XGGX.RGGB -> intArrayOf(R, G, B)
                }
            }
            3 -> {        // PIXEL G2
                var R = 0
                var G = 0
                var B = 0

                // get R
                val R1 = pixels[getPixel(x, y - 1)]
                val R2 = pixels[getPixel(x, y + 1)]

                // get B
                val B1 = pixels[getPixel(x - 1, y)]
                val B2 = pixels[getPixel(x + 1, y)]

                // assign R
                R = (R1 + R2) / 2

                // assign G
                G = pixels[getPixel(x, y)]

                // assign B
                B = (B1 + B2) / 2

                return when (order) {
                    XGGX.BGGR -> intArrayOf(B, G, R)
                    XGGX.RGGB -> intArrayOf(R, G, B)
                }
            }
            else -> {       // PIXEL B
                var R = 0
                var G = 0
                var B = 0

                // get G
                val G1 = pixels[getPixel(x, y - 1)]
                val G2 = pixels[getPixel(x + 1, y)]
                val G3 = pixels[getPixel(x, y + 1)]
                val G4 = pixels[getPixel(x - 1, y)]

                // get B
                val B1 = pixels[getPixel(x, y - 2)]
                val B2 = pixels[getPixel(x + 2, y)]
                val B3 = pixels[getPixel(x, y + 2)]
                val B4 = pixels[getPixel(x - 2, y)]

                // get R
                val R1 = pixels[getPixel(x - 1, y - 1)]
                val R2 = pixels[getPixel(x + 1, y - 1)]
                val R3 = pixels[getPixel(x + 1, y + 1)]
                val R4 = pixels[getPixel(x - 1, y + 1)]

                // assign R
                R = (R1 + R2 + R3 + R4) / 4

                // assign G
                if (abs(B1 - B3) < abs(B2 - B4))
                    G = (G1 + G3) / 2
                else if (abs(B1 - B3) > abs(B2 - B4))
                    G = (G2 + G4) / 2
                else
                    G = (G1 + G2 + G3 + G4) / 4

                // assign B
                B = pixels[getPixel(x, y)]

                return when (order) {
                    XGGX.BGGR -> intArrayOf(B, G, R)
                    XGGX.RGGB -> intArrayOf(R, G, B)
                }
            }
        }
    }

    fun debayGXXG(pixels: IntArray, x: Int, y: Int, order: GXXG): IntArray {
        var case = 0
        case = if (x % 2 == 0 && y % 2 == 0)
            1
        else if (x % 2 == 1 && y % 2 == 0)
            2
        else if (x % 2 == 0 && y % 2 == 1)
            3
        else
            4

        when (case) {
            1 -> {     // PIXEL G
                var R = 0
                var G = 0
                var B = 0

                // get R
                val R1 = pixels[getPixel(x - 1, y)]
                val R2 = pixels[getPixel(x + 1, y)]

                // get B
                val B1 = pixels[getPixel(x, y - 1)]
                val B2 = pixels[getPixel(x, y + 1)]

                // assign G
                G = pixels[getPixel(x, y)]

                // assign R
                R = (R1 + R2) / 2
                // assign B
                B = (B1 + B2) / 2

                return when(order) {
                    GXXG.GRBG -> intArrayOf(R, G, B)
                    GXXG.GBRG -> intArrayOf(B, G, R)
                }
            }
            2 -> {       // PIXEL R
                var R = 0
                var G = 0
                var B = 0

                // get R
                val R1 = pixels[getPixel(x, y - 2)]
                val R2 = pixels[getPixel(x + 2, y)]
                val R3 = pixels[getPixel(x, y - 2)]
                val R4 = pixels[getPixel(x - 2, y)]

                // get G
                val G1 = pixels[getPixel(x, y -1)]
                val G2 = pixels[getPixel(x + 1, y)]
                val G3 = pixels[getPixel(x, y + 1)]
                val G4 = pixels[getPixel(x - 1, y)]

                // get B
                val B1 = pixels[getPixel(x - 1, y - 1)]
                val B2 = pixels[getPixel(x + 1, y - 1)]
                val B3 = pixels[getPixel(x - 1, y + 1)]
                val B4 = pixels[getPixel(x + 1, y + 1)]

                // assign R
                R = pixels[getPixel(x, y)]

                // assign G
                if (abs(R1 - R3) < abs(R2 - R4))
                    G = (G1 + G3) / 2
                else if (abs(R1 - R3) > abs(R2 - R4))
                    G = (G2 + G4) / 2
                else
                    G = (G1 + G2 + G3 + G4) / 4

                // assign B
                B = (B1 + B2 + B3 + B4) / 4

                return when(order) {
                    GXXG.GRBG -> intArrayOf(R, G, B)
                    GXXG.GBRG -> intArrayOf(B, G, R)
                }
            }
            3 -> {       // PIXEL B
                var R = 0
                var G = 0
                var B = 0

                // get R
                val R1 = pixels[getPixel(x - 1, y -1)]
                val R2 = pixels[getPixel(x + 1, y - 1)]
                val R3 = pixels[getPixel(x + 1, y + 1)]
                val R4 = pixels[getPixel(x - 1, y + 1)]

                // get G
                val G1 = pixels[getPixel(x, y - 1)]
                val G2 = pixels[getPixel(x + 1, y)]
                val G3 = pixels[getPixel(x, y + 1)]
                val G4 = pixels[getPixel(x - 1, y)]

                // get B
                val B1 = pixels[getPixel(x, y - 2)]
                val B2 = pixels[getPixel(x + 2, y)]
                val B3 = pixels[getPixel(x, y + 2)]
                val B4 = pixels[getPixel(x - 2, y)]

                // assign R
                R = (R1 + R2 + R3 + R4) / 4

                // assign G
                if (abs(B1 - B3) < abs(B2 - B4))
                    G = (G1 + G3) / 2
                else if (abs(B1 - B3) > abs(B2 - B4))
                    G = (G2 + G4) / 2
                else
                    G = (G1 + G2 + G3 + G4) / 4

                // assign B
                B = pixels[getPixel(x, y)]

                return when(order) {
                    GXXG.GRBG -> intArrayOf(R, G, B)
                    GXXG.GBRG -> intArrayOf(B, G, R)
                }
            }
            else -> {       // PIXEL G2
                var R = 0
                var G = 0
                var B = 0

                // get R
                val R1 = pixels[getPixel(x, y - 1)]
                val R2 = pixels[getPixel(x, y + 1)]

                // get B
                val B1 = pixels[getPixel(x - 1, y)]
                val B2 = pixels[getPixel(x + 1, y)]

                // assign R
                R = (R1 + R2) / 2

                // assign G
                G = pixels[getPixel(x, y)]

                // assign B
                B = (B1 + B2) / 2

                return when(order) {
                    GXXG.GRBG -> intArrayOf(R, G, B)
                    GXXG.GBRG -> intArrayOf(B, G, R)
                }
            }
        }
    }

    fun getPixel(x: Int, y: Int): Int {
        var X = 0
        X = when {
            x >= Width -> Width - 1
            x < 0 -> 0
            else -> x
        }

        var Y = 0
        Y = when {
            y >= Height -> Height - 1
            y < 0 -> 0
            else -> y
        }

        val index = X + Y * Width
        return if (index < Width * Height)
            index
        else
            0
    }

    fun rotate90Pixel(pixels: IntArray, x: Int, y: Int): Int {
        val index = Width * (Height - (y + 1)) + x
        return if (index < Width  * Height) pixels[index]
        else pixels[(Width * Height) - 1]
    }

    fun DebayGXXG(pixels: IntArray, x: Int, y: Int, order: GXXG): IntArray {
        val TAG = "RawConverter"
        Log.i(TAG, "Pixels max: ${pixels.max()}, Pixels min: ${pixels.min()}")

        var r: Int
        var g = 0
        var b: Int

        if (abs(x - y) % 2 == 0) {
            g =  pixels[getPixel(x, y)]
        } else {
            var count = 0
            if ((x - 1) >= 0) {
                g += pixels[getPixel(x - 1, y)]
                count ++
            }
            if ((x + 1) < Width) {
                g += pixels[getPixel(x + 1, y)]
                count ++
            }
            if ((y - 1) >= 0) {
                g += pixels[getPixel(x, y - 1)]
                count ++
            }
            if ((y + 1) < Height) {
                g += pixels[getPixel(x, y + 1)]
                count ++
            }
            g /= count
        }

        if (x % 2 == 0 && y % 2 == 1) {
            b = pixels[getPixel(x, y)]
        } else {
            if (y % 2 == 1) {
                if ((x + 1) < Width)
                    b = (pixels[getPixel(x - 1, y)] + pixels[getPixel(x + 1, y)]) /2
                else
                    b = pixels[getPixel(x - 1, y)]
            } else {
                if (y == 0) {
                    if (x % 2 == 0)
                        b = pixels[getPixel(x, y + 1)]
                    else
                        if ((x + 1) < Width)
                            b = (pixels[getPixel(x - 1, y + 1)] + pixels[getPixel(x + 1, y + 1)]) / 2
                        else
                            b = pixels[getPixel(x - 1, y + 1)]
                } else {
                    if (x % 2 == 0)
                        b = (pixels[getPixel(x, y - 1)] + pixels[getPixel(x, y + 1)]) / 2
                    else
                        if ((x + 1) < Width)
                            b = (pixels[getPixel(x - 1, y - 1)] + pixels[getPixel(x + 1, y - 1)] + pixels[getPixel(x - 1, y + 1)] + pixels[getPixel(x + 1, y + 1)]) / 4
                        else
                            b = (pixels[getPixel(x - 1, y - 1)] + pixels[getPixel(x - 1, y + 1)]) / 2
                }
            }
        }

        if (x % 2 == 1 && y % 2 == 0) {
            r = pixels[getPixel(x, y)]
        } else {
            if (x % 2 == 1) {
                if ((y + 1) < Height)
                    r = (pixels[getPixel(x, y - 1)] + pixels[getPixel(x, y + 1)]) / 2
                else
                    r = pixels[getPixel(x, y - 1)]
            } else {
                if (x == 0) {
                    if (y % 2 == 0)
                        r = pixels[getPixel(x + 1, y)]
                    else
                        if ((y + 1) < Height)
                            r = (pixels[getPixel(x + 1, y - 1)] + pixels[getPixel(x + 1, y + 1)]) / 2
                        else
                            r = pixels[getPixel(x + 1, y -1)]
                } else {
                    if (y % 2 == 0)
                        r = (pixels[getPixel(x - 1, y)] + pixels[getPixel(x + 1, y)]) / 2
                    else
                        if ((y + 1) < Height)
                            r = (pixels[getPixel(x - 1, y - 1)] + pixels[getPixel(x + 1, y - 1)] + pixels[getPixel(x - 1, y + 1)] + pixels[getPixel(x + 1, y + 1)]) / 4
                        else
                            r = (pixels[getPixel(x - 1, y - 1)] + pixels[getPixel(x + 1, y - 1)]) / 2
                }
            }
        }

        return when (order) {
            GXXG.GRBG -> intArrayOf(r, g, b)
            GXXG.GBRG -> intArrayOf(b, g, r)
        }
    }
}