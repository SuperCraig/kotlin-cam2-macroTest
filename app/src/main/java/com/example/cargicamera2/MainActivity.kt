package com.example.cargicamera2

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log

class MainActivity : AppCompatActivity() {
    private lateinit var m_address: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bundle = Bundle()
        val camera2BasicFragment = Camera2BasicFragment()

        m_address = intent.getStringExtra(SplashScreenActivity.EXTRA_ADDRESS) ?: ""
        bundle.putString(SplashScreenActivity.EXTRA_ADDRESS, m_address)
        camera2BasicFragment.arguments = bundle
        savedInstanceState ?: supportFragmentManager.beginTransaction()
            .replace(R.id.container, camera2BasicFragment, "Camera2BasicFragment").commit()

        Log.i(TAG, "Paired address: $m_address")
    }

    override fun onBackPressed() {
        super.onBackPressed()
        Log.i(TAG, "onBackPressed")
        val cameraFragment: Camera2BasicFragment = supportFragmentManager.findFragmentByTag("Camera2BasicFragment") as Camera2BasicFragment
        if(cameraFragment.isVisible){
            cameraFragment.readSettingData()
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
