package com.example.cargicamera2

import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.extensions.canny
import kotlinx.android.synthetic.main.activity_opencv_main.*
import org.opencv.core.Mat

class MainActivity : AppCompatActivity() {
    private lateinit var m_address: String

    private val imageBitmap by lazy {
        (ContextCompat.getDrawable(
            this,
            R.drawable.lena
        ) as BitmapDrawable).bitmap
    }

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

//        val camera2Intent = Camera2Intent()
//        savedInstanceState ?: supportFragmentManager.beginTransaction()
//               .replace(R.id.container, camera2Intent, "Camera2BasicFragment").commit()


//        setContentView(R.layout.activity_opencv_main)
//        applyCannyEdge()

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

    private fun applyCannyEdge () {
        val mat = Mat()
        mat.canny(imageBitmap) { image.setImageBitmap(it) }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
