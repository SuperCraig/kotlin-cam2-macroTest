package com.example.craigCam2

import android.app.AlertDialog
import android.content.DialogInterface
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ToggleButton
import androidx.core.view.get
import androidx.fragment.app.Fragment
import com.example.easy_toggle.EasyToggle
import kotlinx.android.synthetic.main.fragment_setting.*
import java.lang.NumberFormatException

class SettingFragment : Fragment(){
    private lateinit var settings: SharedPreferences
    private var isGridEnable: Boolean = false
    private var isSoundEnable: Boolean = false
    private var isPatternEnable: Boolean = false
    private var isCloudSyncEnable: Boolean = false

    private var isDemoEnable: Boolean = false
    private var parameter1: Int = 0
    private var parameter2: Int = 0
    private var parameter3: Int = 0

    private var whitePeakValue: Int = 0
    private var blackNadirValue: Int = 0
    private var darkNoiseValue: Int = 0
    private var repeatTimesValue: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.fragment_setting, container, false)
        return v
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        readData()
        with(toggle_btn_grid){
            currentState = if(isGridEnable) 1 else 0
            reset()
            toggleImmediately()
        }

        with(toggle_btn_sound){
            currentState = if(isSoundEnable) 1 else 0
            reset()
            toggleImmediately()
        }

        with(toggle_btn_pattern) {
            currentState = if (isPatternEnable) 1 else 0
            reset()
            toggleImmediately()

            if (isPatternEnable) {
                edtWhitePeak.setIsEditable(true)
                edtBlackNadir.setIsEditable(true)
            } else {
                edtWhitePeak.setIsEditable(false)
                edtBlackNadir.setIsEditable(false)
            }
        }

        with(toggle_btn_cloud){
            currentState = if(isCloudSyncEnable) 1 else 0
            reset()
            toggleImmediately()
        }

        with(toggle_btn_demo) {
            currentState = if (isDemoEnable) 1 else 0
            reset()
            toggleImmediately()

            layout_parameter3.visibility = if (isDemoEnable) View.VISIBLE
            else View.GONE
        }

        toggle_btn_grid.setOnToggledListener {
            isGridEnable = it
            saveData()
            clearEdtFocus()
        }

        toggle_btn_pattern.setOnToggledListener {
            isPatternEnable = it
            saveData()
            clearEdtFocus()

            if (isPatternEnable) {
                edtWhitePeak.setIsEditable(true)
                edtBlackNadir.setIsEditable(true)
            } else {
                edtWhitePeak.setIsEditable(false)
                edtBlackNadir.setIsEditable(false)
                Log.i(TAG, "toggle_btn_pattern clicked")
            }
        }

        toggle_btn_cloud.setOnToggledListener {
            isCloudSyncEnable = it
            saveData()
            clearEdtFocus()
        }

        toggle_btn_sound.setOnToggledListener {
            isSoundEnable = it
            saveData()
            clearEdtFocus()
        }

        toggle_btn_demo.setOnToggledListener {
            isDemoEnable = it
            saveData()
            clearEdtFocus()

            layout_parameter3.visibility = if (isDemoEnable) View.VISIBLE
            else View.GONE
        }

        layout_grid.setOnClickListener {
            clearEdtFocus()
        }

        layout_sound.setOnClickListener {
            clearEdtFocus()
        }

        layout_pattern.setOnClickListener {
            clearEdtFocus()
        }

        layout_cloud.setOnClickListener {
            clearEdtFocus()
        }

        layout_demo.setOnClickListener {
            clearEdtFocus()
        }

        layout_format.setOnClickListener{
            Log.i(TAG, "layout_format.setOnClickListener")
            clearEdtFocus()
        }

        layout_language.setOnClickListener{
            Log.i(TAG, "layout_language.setOnClickListener")
            clearEdtFocus()
        }

        layout_tell_a_friend.setOnClickListener{
            Log.i(TAG, "layout_tell_a_friend.setOnClickListener")
            clearEdtFocus()
        }

        layout_feedback.setOnClickListener{
            Log.i(TAG, "layout_feedback.setOnClickListener")
            clearEdtFocus()
        }

        layout_about.setOnClickListener{
            Log.i(TAG, "layout_about.setOnClickListener")
            clearEdtFocus()
            showAbout()
        }

        layout_reset.setOnClickListener{
            Log.i(TAG, "layout_reset.setOnClickListener")
            showDialog()
            clearEdtFocus()
        }

        total_container.setOnClickListener {

        }

        layout_scrollView.setOnClickListener {
            clearEdtFocus()
        }

        edtWhitePeak.setTextValue(whitePeakValue.toString())
        edtBlackNadir.setTextValue(blackNadirValue.toString())
        edtDarkNoise.setTextValue(darkNoiseValue.toString())
        edtRepeatTimes.setTextValue(repeatTimesValue.toString())

        edtParam1.setTextValue(parameter1.toString())
        edtParam2.setTextValue(parameter2.toString())
        edtParam3.setTextValue(parameter3.toString())

        view.viewTreeObserver.addOnGlobalLayoutListener {
            val r = Rect()
            view.getWindowVisibleDisplayFrame(r)
            if (view.rootView.height - (r.bottom - r.top) > 500) {  // if more than 100 pixels, its probably a keyboard...
                Log.i(TAG, "keyboard show")
            }   else {
                Log.i(TAG, "keyboard hide")
                if (edtWhitePeak != null) {
                    if (edtWhitePeak.getTextValue()!!.toInt() < 128)
                        edtWhitePeak.setTextValue("128")
                }
            }
        }
    }

    private fun showDialog() {
        lateinit var dialog: AlertDialog

        var  builder = AlertDialog.Builder(this.context, AlertDialog.THEME_HOLO_DARK)

        builder.setTitle("Reset All Settings")

        builder.setMessage("Are you sure?")

        val dialogClickListener = DialogInterface.OnClickListener { _, which ->
            when (which) {
                DialogInterface.BUTTON_POSITIVE -> {
                    setToDefault()
                    saveData()
                }
                DialogInterface.BUTTON_NEGATIVE -> {

                }
            }
        }

        builder.setPositiveButton("Yes", dialogClickListener)
        builder.setNegativeButton("No", dialogClickListener)
        dialog = builder.create()
        dialog.show()
    }

    private fun showAbout() {
        lateinit var dialog: AlertDialog

        var builder = AlertDialog.Builder(this.context, AlertDialog.THEME_HOLO_DARK)

        builder.setTitle("Macroblock Inc.")

        val alert1: String = APP_VERSION
        val alert2: String = APP_SERIAL_NO
        builder.setMessage(alert1 + "\n" + alert2)

        val dialogClickListener = DialogInterface.OnClickListener {_, which ->
            when(which) {
                DialogInterface.BUTTON_NEUTRAL -> {

                }
            }
        }

        builder.setNeutralButton("OK", dialogClickListener)
        dialog = builder.create()
        dialog.show()
    }

    override fun onPause() {
        super.onPause()
        Log.i(TAG, "onPause")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy")
    }

    override fun onDestroyView() {
        saveData()
        super.onDestroyView()
        Log.i(TAG, "onDestroyView")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        isGridEnable.let { outState.putBoolean("IsGridEnable", it) }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        if(savedInstanceState != null){
            isGridEnable = savedInstanceState.getBoolean("IsGridEnable")
        }
    }


    private fun setToDefault() {
        whitePeakValue = 255
        blackNadirValue = 0
        darkNoiseValue = 0
        repeatTimesValue = 2
        edtWhitePeak.setTextValue(whitePeakValue.toString())
        edtDarkNoise.setTextValue(darkNoiseValue.toString())
        edtBlackNadir.setTextValue(blackNadirValue.toString())
        edtRepeatTimes.setTextValue(repeatTimesValue.toString())

        isGridEnable = false
        isSoundEnable = false
        isCloudSyncEnable = false
        isPatternEnable = false

        isDemoEnable = false
        parameter1 = 10000
        parameter2 = 1000
        parameter3 = 2000
        edtParam1.setTextValue(parameter1.toString())
        edtParam2.setTextValue(parameter2.toString())
        edtParam3.setTextValue(parameter3.toString())

        with(toggle_btn_grid){
            currentState = if(isGridEnable) 1 else 0
            reset()
            toggleImmediately()
            invalidate()
        }
        with(toggle_btn_sound){
            currentState = if(isSoundEnable) 1 else 0
            reset()
            toggleImmediately()
            invalidate()
        }
        with(toggle_btn_cloud){
            currentState = if(isCloudSyncEnable) 1 else 0
            reset()
            toggleImmediately()
            invalidate()
        }
        with(toggle_btn_pattern) {
            currentState = if (isPatternEnable) 1 else 0
            reset()
            toggleImmediately()
            invalidate()
        }

        with(toggle_btn_demo) {
            currentState = if (isDemoEnable) 1 else 0
            reset()
            toggleImmediately()
            invalidate()
        }
    }

    private fun readData(){
        settings = this.context!!.getSharedPreferences(PREF_NAME, PRIVATE_MODE)
        isGridEnable = settings.getBoolean(GRID, false)
        isSoundEnable = settings.getBoolean(SOUND, false)
        isCloudSyncEnable = settings.getBoolean(CLOUD_SYNC, false)
        isPatternEnable = settings.getBoolean(PATTERN_SYNC, false)
        whitePeakValue = settings.getInt(WHITE_PEAK, 255)
        blackNadirValue = settings.getInt(BLACK_NADIR, 0)
        darkNoiseValue = settings.getInt(DARK_NOISE, 0)
        repeatTimesValue = settings.getInt(REPEAT_TIMES, 2)

        isDemoEnable = settings.getBoolean(DEMO, false)
        parameter1 = settings.getInt(PARAMETER1, 10000)
        parameter2 = settings.getInt(PARAMETER2, 1000)
        parameter3 = settings.getInt(PARAMETER3, 2000)
    }

    private fun saveData(){
        getTextValue()

        settings = this.context!!.getSharedPreferences(PREF_NAME, PRIVATE_MODE)
        val editor = settings.edit()
        editor.putBoolean(GRID, isGridEnable)
        editor.putBoolean(SOUND, isSoundEnable)
        editor.putBoolean(CLOUD_SYNC, isCloudSyncEnable)
        editor.putBoolean(PATTERN_SYNC, isPatternEnable)
        editor.putInt(WHITE_PEAK, whitePeakValue)
        editor.putInt(BLACK_NADIR, blackNadirValue)
        editor.putInt(DARK_NOISE, darkNoiseValue)
        editor.putInt(REPEAT_TIMES, repeatTimesValue)

        editor.putBoolean(DEMO, isDemoEnable)
        editor.putInt(PARAMETER1, parameter1)
        editor.putInt(PARAMETER2, parameter2)
        editor.putInt(PARAMETER3, parameter3)
        editor.apply()
    }

    private fun getTextValue() {
        whitePeakValue = edtWhitePeak.getTextValue()!!.toInt0()
        blackNadirValue = edtBlackNadir.getTextValue()!!.toInt0()
        darkNoiseValue = edtDarkNoise.getTextValue()!!.toInt0()
        repeatTimesValue = edtRepeatTimes.getTextValue()!!.toInt0()

        parameter1 = edtParam1.getTextValue()!!.toInt0()
        parameter2 = edtParam2.getTextValue()!!.toInt0()
        parameter3 = edtParam3.getTextValue()!!.toInt0()
    }

    private fun String.toInt0() = try {
        toInt()
    } catch (e: NumberFormatException) {
        0
    }

    private fun clearEdtFocus() {
        edtWhitePeak.clearFocus()
        edtBlackNadir.clearFocus()
        edtDarkNoise.clearFocus()
        edtRepeatTimes.clearFocus()

        edtParam1.clearFocus()
        edtParam2.clearFocus()
        edtParam3.clearFocus()
    }

    companion object{
        private const val TAG = "SettingFragment"
        const val PRIVATE_MODE = 0
        const val PREF_NAME = "SettingFragment"
        const val GRID = "GRID"
        const val SOUND = "SOUND"
        const val CLOUD_SYNC = "CLOUD_SYNC"
        const val PATTERN_SYNC = "PATTERN_SYNC"
        const val WHITE_PEAK = "WHITE_PEAK"
        const val BLACK_NADIR = "BLACK_NADIR"
        const val DARK_NOISE = "DARK_NOISE"
        const val REPEAT_TIMES = "REPEAT_TIMES"

        const val DEMO = "DEMO"
        const val PARAMETER1 = "PARAMETER1"
        const val PARAMETER2 = "PARAMETER2"
        const val PARAMETER3 = "PARAMETER3"

        const val APP_VERSION = "Ver1.1"
        const val APP_SERIAL_NO = "S/N 20200904001"
    }
}