package com.example.craigCam2

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat


class SplashScreenActivity : AppCompatActivity(),
    ActivityCompat.OnRequestPermissionsResultCallback {
    private var mDelayHandler: Handler? = null
    private val SPLASH_TIME_OUT: Long = 3000

    lateinit var mainHandler: Handler
    private var isSplashDone: Boolean = false

    private var m_bluetoothAdapter: BluetoothAdapter? = null
    private lateinit var m_paireDevices: Set<BluetoothDevice>
    private val REQUEST_ENABLE_BLUETOOTH = 1
    private var deviceList:ArrayList<BluetoothDevice> = ArrayList()

    private val mRunnable: Runnable = Runnable {
        if (!isFinishing) {
            isSplashDone = true
            if (checkRequiredPermissions()) {
                val intent = Intent(applicationContext, MainActivity::class.java)

                if(deviceList.size > 0){
                    var device: BluetoothDevice? = null
                    var address = ""
                    deviceList.forEach {
                        if (it.name.contains("DESKTOP")) {
                            device = it
                            address = it.address
                        }
                    }
                    intent.putExtra(EXTRA_ADDRESS, address)
                }else{
                    intent.putExtra(EXTRA_ADDRESS, "")
                }

                startActivity(intent)
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_page)

        mDelayHandler = Handler()
        mDelayHandler!!.postDelayed(mRunnable, SPLASH_TIME_OUT)

        mainHandler = Handler(Looper.getMainLooper())

        m_bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (m_bluetoothAdapter == null) {
            return
        }
        if (!m_bluetoothAdapter!!.isEnabled) {
            val enableBluetoothIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBluetoothIntent, REQUEST_ENABLE_BLUETOOTH)
        }

        pairedDeviceList()
    }

    public override fun onDestroy() {

        if (mDelayHandler != null) {
            mDelayHandler!!.removeCallbacks(mRunnable)
        }

        super.onDestroy()
    }

    override fun onPause(){
        super.onPause()
        mainHandler.removeCallbacks(checkPermission)
    }

    override fun onResume() {
        super.onResume()
        mainHandler.post(checkPermission)
    }

    private val checkPermission = Runnable {
        if(checkRequiredPermissions() and isSplashDone){
            val intent = Intent(applicationContext, MainActivity::class.java)

            if(deviceList.size > 0){
                val position = deviceList.size ?: 0
                val device: BluetoothDevice = deviceList[position]
                val address = device.address
                intent.putExtra(EXTRA_ADDRESS, address)
            }else{
                intent.putExtra(EXTRA_ADDRESS, "")
            }
            startActivity(intent)
            finish()
        }
    }

    private fun checkRequiredPermissions(): Boolean {
        val deniedPermissions = mutableListOf<String>()
        for (permission in PERMISSIONS_REQUIRED) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) == PackageManager.PERMISSION_DENIED
            ) {
                deniedPermissions.add(permission)
            }
        }
        if (deniedPermissions.isEmpty().not()) {
            requestPermissions(deniedPermissions.toTypedArray(), Companion.REQUEST_PERMISSION_CODE)
        }
        return deniedPermissions.isEmpty()
    }


    private fun pairedDeviceList() {
        m_paireDevices = m_bluetoothAdapter!!.bondedDevices
        val list: ArrayList<BluetoothDevice> = ArrayList()

        if (!m_paireDevices.isEmpty()) {
            for (device: BluetoothDevice in m_paireDevices) {
                list.add(device)
                Log.i("device", "uuid: ${device.uuids}, device name: ${device.name}, device address: ${device.address}, device type: ${device.type}")
            }
        }

        deviceList = list
    }

    private val PERMISSIONS_REQUEST_CODE = 10
    private val PERMISSIONS_REQUIRED = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN
    )

    companion object {
        private const val TAG = "SplashScreenActivity"
        private const val REQUEST_PERMISSION_CODE: Int = 1

        val EXTRA_ADDRESS: String = "Device_address"
    }
}