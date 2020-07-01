package com.example.cargicamera2

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
    private var isCloudSyncEnable: Boolean = false
    private var whitePeakValue: Int = 0
    private var blackNadirValue: Int = 0
    private var darkNoiseValue: Int = 0

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

        with(toggle_btn_cloud){
            currentState = if(isCloudSyncEnable) 1 else 0
            reset()
            toggleImmediately()
        }

        toggle_btn_grid.setOnToggledListener {
            isGridEnable = it
            saveData()
            clearEdtFocus()
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

        layout_grid.setOnClickListener {
            clearEdtFocus()
        }

        layout_sound.setOnClickListener {
            clearEdtFocus()
        }

        layout_cloud.setOnClickListener {
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
        edtWhitePeak.setTextValue("255")
        edtDarkNoise.setTextValue("0")
        edtBlackNadir.setTextValue("0")

        isGridEnable = false
        isSoundEnable = false
        isCloudSyncEnable = false

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
    }

    private fun readData(){
        settings = this.context!!.getSharedPreferences(PREF_NAME, PRIVATE_MODE)
        isGridEnable = settings.getBoolean(GRID, false)
        isSoundEnable = settings.getBoolean(SOUND, false)
        isCloudSyncEnable = settings.getBoolean(CLOUD_SYNC, false)
        whitePeakValue = settings.getInt(WHITE_PEAK, 255)
        blackNadirValue = settings.getInt(BLACK_NADIR, 0)
        darkNoiseValue = settings.getInt(DARK_NOISE, 70)
    }

    private fun saveData(){
        getTextValue()

        settings = this.context!!.getSharedPreferences(PREF_NAME, PRIVATE_MODE)
        val editor = settings.edit()
        editor.putBoolean(GRID, isGridEnable)
        editor.putBoolean(SOUND, isSoundEnable)
        editor.putBoolean(CLOUD_SYNC, isCloudSyncEnable)
        editor.putInt(WHITE_PEAK, whitePeakValue)
        editor.putInt(BLACK_NADIR, blackNadirValue)
        editor.putInt(DARK_NOISE, darkNoiseValue)
        editor.apply()
    }

    private fun getTextValue() {
        whitePeakValue = edtWhitePeak.getTextValue()!!.toInt0()
        blackNadirValue = edtBlackNadir.getTextValue()!!.toInt0()
        darkNoiseValue = edtDarkNoise.getTextValue()!!.toInt0()
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
    }

    companion object{
        private const val TAG = "SettingFragment"
        const val PRIVATE_MODE = 0
        const val PREF_NAME = "SettingFragment"
        const val GRID = "GRID"
        const val SOUND = "SOUND"
        const val CLOUD_SYNC = "CLOUD_SYNC"
        const val WHITE_PEAK = "WHITE_PEAK"
        const val BLACK_NADIR = "BLACK_NADIR"
        const val DARK_NOISE = "DARK_NOISE"
    }
}