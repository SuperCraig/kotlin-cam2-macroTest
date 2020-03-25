package com.example.cargicamera2

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.PersistableBundle
import androidx.appcompat.app.AppCompatActivity

class SplashScreenActivity :AppCompatActivity(){
    private var mDelayHandler: Handler? = null
    private val SPLASH_TIME_OUT:Long = 3000

    internal val mRunnable: Runnable = Runnable {
        if (!isFinishing) {
            val intent = Intent(applicationContext, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_page)

        mDelayHandler = Handler()
        mDelayHandler!!.postDelayed(mRunnable, SPLASH_TIME_OUT)
    }

    public override fun onDestroy() {

        if (mDelayHandler != null) {
            mDelayHandler!!.removeCallbacks(mRunnable)
        }

        super.onDestroy()
    }
}