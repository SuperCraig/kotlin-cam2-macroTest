package com.example.craigCam2

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.*
import android.graphics.Point
import android.graphics.Rect
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.*
import android.media.*
import android.os.*
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.*
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RelativeLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.craigCam2.*
import com.example.craigCam2.CompareSizesByArea
import com.example.craigCam2.extensions.MBITSP2020
import com.example.craigCam2.extensions.RawConverter
import com.example.craigCam2.extensions.getISOList
import com.example.craigCam2.extensions.getTvList
import com.example.craigCam2.fragments.PermissionsFragment
import com.example.craigCam2.room.History
import com.example.craigCam2.room.HistoryViewModel
import com.example.craigCam2.services.showToast
import com.example.craigCam2.ui.AutoFitTextureView
import com.example.craigCam2.ui.ErrorDialog
import com.example.craigCam2.ui.FocusView
import com.example.craigCam2.ui.GridLineView
import com.example.extensions.toBitmap
import com.example.extensions.toMat
import com.example.imagegallery.fragment.GalleryFullscreenFragment
import com.example.imagegallery.model.ImageGalleryUiModel
import com.example.imagegallery.service.MediaHelper
import com.example.mruler.RulerView
import com.example.toast.ToastView
import kotlinx.android.synthetic.main.fragment_camera2_basic.*
import kotlinx.android.synthetic.main.fragment_camera2_basic.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.collections.ArrayList
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.*

class Camera2BasicFragment : Fragment(), View.OnClickListener,
    ActivityCompat.OnRequestPermissionsResultCallback {

    /**
     * [TextureView.SurfaceTextureListener] handles several lifecycle events on a
     * [TextureView].
     */
    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {

        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
            openCamera(width, height)
        }

        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
            configureTransform(width, height)
        }

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture) : Boolean {
            return true
        }

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

    }

    private val surfaceTextureTouchListener = View.OnTouchListener { v, event ->
        try{
//                focus.showFocus(event.x.toInt(), event.y.toInt())

            val maxZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
            val rect: Rect? = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
//                rect ?: return@setOnTouchListener true

            val currentFingerSpacing: Float

            if (event.pointerCount > 1){
                currentFingerSpacing = getFingerSpacing(event)
                var delta: Float = 0.05f
                if (fingerSpacing != 0f){
                    if (currentFingerSpacing > fingerSpacing){
                        if((maxZoom!! - zoomLevel) <= delta)
                            delta = maxZoom - zoomLevel

                        zoomLevel += delta
                    }
                    else if (currentFingerSpacing < fingerSpacing){
                        if ((zoomLevel - delta) < 1f)
                            delta = zoomLevel - 1f

                        zoomLevel -= delta
                    }
                    val ratio: Float = (1 / zoomLevel).toFloat()
                    val croppedWidth = rect!!.width() - (rect.width() * ratio).roundToInt()
                    val croppedHeight = rect.height() - (rect.height() * ratio).roundToInt()
                    zoom = Rect(croppedWidth/2, croppedHeight/2, rect.width() - croppedWidth/2, rect.height() - croppedHeight/2)

                    previewRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoom)

//                        unlockFocus()
                    captureSession.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler)
                }
                fingerSpacing = currentFingerSpacing

            }else{
                return@OnTouchListener true
            }
            return@OnTouchListener true
        }catch (e: Exception){
            Log.d(TAG, e.toString())
            return@OnTouchListener true
        }
    }

    /**
     * ID of the current [CameraDevice].
     */
    private lateinit var cameraId: String

    /**
     * An [AutoFitTextureView] for camera preview.
     */
    private lateinit var textureView: AutoFitTextureView

    private lateinit var focus: FocusView

    /**
     * The [android.util.Size] of camera preview.
     */
    private lateinit var previewSize: Size

    /**
     * Orientation of the camera sensor
     */
    private var sensorOrientation = 0

    /**
     * The current state of camera state for taking pictures.
     *
     * @see .captureCallback
     */
    private var state = STATE_PREVIEW

    /**
     * A [Semaphore] to prevent the app from exiting before closing the camera.
     */
    private val cameraOpenCloseLock = Semaphore(1)


    /** [CameraCharacteristics] corresponding to the provided Camera ID */
    private lateinit var characteristics: CameraCharacteristics

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private var backgroundThread: HandlerThread? = null

    /**
     * A [Handler] for running tasks in the background.
     */
    private var backgroundHandler: Handler? = null

    /**
     * An [ImageReader] that handles still image capture.
     */
    private lateinit var mImageReader: ImageReader

    private var mRawImageReader: ImageReader? = null

    /**
     * This is the output file for our picture.
     */
    private lateinit var file: File
    private lateinit var captureResult: CaptureResult


    /**
     * [CaptureRequest.Builder] for the camera preview
     */
    private lateinit var previewRequestBuilder: CaptureRequest.Builder

    /**
     * [CaptureRequest] generated by [.previewRequestBuilder]
     */
    private lateinit var previewRequest: CaptureRequest


    /**
     * Whether the current camera device supports Flash or not.
     */
    private var flashSupported = false

    /**
     * A [CameraCaptureSession] for camera preview.
     */
    private lateinit var captureSession: CameraCaptureSession

    /**
     * A reference to the opened [CameraDevice].
     */
    private var cameraDevice: CameraDevice? = null

    /** [HandlerThread] where all camera operations run */
    private val cameraThread = HandlerThread("CameraThread").apply { start() }

    /** [Handler] corresponding to [cameraThread] */
    private val cameraHandler = Handler(cameraThread.looper)

    /** [HandlerThread] where all buffer reading operations run */
    private val imageReaderThread = HandlerThread("imageReaderThread").apply { start() }

    /** [Handler] corresponding to [imageReaderThread] */
    private val imageReaderHandler = Handler(imageReaderThread.looper)

    private val readCommandThread = HandlerThread("readCommandThread").apply { start() }
    private val readCommandHandler = Handler(readCommandThread.looper)

    private lateinit var settings: SharedPreferences
    private var aperture: Float = 0f
    private var exposureTime: Long = 0
    private var sensorSensitivity: Int = 0
    private var apertureProgress:Int = 0
    private var exposureProgress: Int = 0
    private var sensorSensitivityProgress: Int = 0

    private var focusZoomScale: Int = 0

    private var isAutoEnable: Boolean = false
    private var isManualEnable: Boolean = false
    private var isContrastEnable: Boolean = false
    private var isColorTemperatureEnable: Boolean = false
    private var isRefreshRateEnable: Boolean = false

    private var isGridEnable: Boolean = false
    private var isSoundEnable: Boolean = false
    private var isCloudSyncEnable: Boolean = false
    private var whitePeakValue: Int = 0
    private var blackNadirValue: Int = 0
    private var darkNoiseValue: Int = 0
    private var repeatTimesValue: Int = 0

    private var isJPEGSavedEnable: Boolean = true
    private var isRAWSavedEnable: Boolean = true

    private var progressbarShutter: ProgressBar ?= null

    private var fingerSpacing: Float = 0f
    private var zoomLevel: Float = 0f
    private var zoom: Rect? = null

    private lateinit var gridLineView: GridLineView

    private val mediaActionSound: MediaActionSound = MediaActionSound()

//    private lateinit var isoCustomSeekBar: CustomSeekBar
//    private lateinit var tvCustomSeekBar: CustomSeekBar
//    private lateinit var avCustomSeekBar: CustomSeekBar

    private lateinit var isoCustomSeekBar: RulerView
    private lateinit var tvCustomSeekBar: RulerView
    private lateinit var avCustomSeekBar: RulerView
    private lateinit var focusCustomSeekBar: RulerView

    private var sensorManager: SensorManager? = null
    private var lightSensor: Sensor? = null

    private val lightSensorListener: LightSensorListener = LightSensorListener()

    private var latestFileName: String? = null

    private var currentFocusIconFlag = FocusIconSize.ZOOM1_8
    private var currentFocusIconSizeWidth = 0.0
    private var currentFocusIconSizeHeight = 0.0
    private var defaultFocusAreaWidth: Int = 0
    private var defaultFocusAreaHeight: Int = 0

    var currentMeasurement: Measurement = Measurement.None
    /**
     * [CameraDevice.StateCallback] is called when [CameraDevice] changes its state.
     */
    private val stateCallback = object : CameraDevice.StateCallback() {

        override fun onOpened(cameraDevice: CameraDevice) {
            cameraOpenCloseLock.release()
            this@Camera2BasicFragment.cameraDevice = cameraDevice
            createCameraPreviewSession()
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
            cameraOpenCloseLock.release()
            cameraDevice.close()
            this@Camera2BasicFragment.cameraDevice = null
        }

        override fun onError(cameraDevice: CameraDevice, error: Int) {
            onDisconnected(cameraDevice)
            this@Camera2BasicFragment.activity?.finish()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        var view: View = inflater.inflate(R.layout.fragment_camera2_basic, container, false)
        m_address = arguments?.getString(SplashScreenActivity.EXTRA_ADDRESS).toString()

        sensorManager = context?.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager!!.getDefaultSensor(Sensor.TYPE_LIGHT)

        Log.i(TAG,"onCreateView")
        Log.i(TAG, "Paired address: $m_address")
        return view
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<View>(R.id.btnPicture).setOnClickListener(this)
//        view.findViewById<View>(R.id.btnManual).setOnClickListener(this)
//        view.findViewById<View>(R.id.btnAuto).setOnClickListener(this)
        view.findViewById<View>(R.id.btnContrast).setOnClickListener(this)
        view.findViewById<View>(R.id.btnRefreshRate).setOnClickListener(this)
        view.findViewById<View>(R.id.btnColorTemperature).setOnClickListener(this)
        view.findViewById<View>(R.id.btnPhotoBox).setOnClickListener(this)
        view.findViewById<View>(R.id.btnRecordBar).setOnClickListener(this)
        view.findViewById<View>(R.id.btnSetting).setOnClickListener(this)
        view.findViewById<View>(R.id.btnExposure).setOnClickListener(this)
        view.findViewById<View>(R.id.btnISO).setOnClickListener(this)
//        view.findViewById<View>(R.id.btnAperture).setOnClickListener(this)
        view.findViewById<View>(R.id.btnFocus).setOnClickListener(this)

        val vibrate = context?.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        val vibrationEffect = VibrationEffect.createOneShot(2, VibrationEffect.DEFAULT_AMPLITUDE)

        readData()          //read shared preferneces data & apply
        readSettingData()

        isoCustomSeekBar = view.findViewById(R.id.isoCustomSeekBar)
        isoCustomSeekBar.setValue(sensorSensitivityProgress.toFloat())
        txt_3AValue.text = "ISO $sensorSensitivity"
        isoCustomSeekBar.setValueListener {
            var progress = isoCustomSeekBar.getValue()
            val range = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
            val max1 = range!!.upper //10000
            val min1 = range.lower //100
            val isoList = getISOList(min1, max1)

            if (isoCustomSeekBar.getMaxValue() != isoList.size) {
                isoCustomSeekBar.setMaxValue(isoList.size - 1)
                isoCustomSeekBar.setNumShow(isoList.size / 2)
            }

            if (progress.toInt() >= isoList.size)
                progress = (isoList.size - 1).toFloat()

            val iso = isoList[progress.toInt()]
            if (sensorSensitivity != iso)
                vibrate.vibrate(vibrationEffect)
            setSensorSensitivity(iso)
            sensorSensitivity = iso
            sensorSensitivityProgress = progress.toInt()
            saveData()      //save shared preferences
            Log.i(TAG, "progress: $progress")
        }
//        isoCustomSeekBar = view.findViewById(R.id.isoCustomSeekBar)
//        isoCustomSeekBar.progress = sensorSensitivityProgress
//        isoCustomSeekBar.text = "ISO $sensorSensitivity"
//        isoCustomSeekBar.setOnTouchListener { _, _ ->
//            val progress = isoCustomSeekBar.progress
//            val range = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
//            val max1 = range!!.upper //10000
//            val min1 = range.lower //100
////            val iso: Int = progress * (max1 - min1) / 100 + min1
//            val isoList = getISOList(min1, max1)
//            val index = progress * (isoList.size - 1) / isoCustomSeekBar.maxProgress
//            val iso = isoList[index]
//
//            if (sensorSensitivity != iso)
//                vibrate.vibrate(vibrationEffect)
//
//            setSensorSensitivity(iso)
//            sensorSensitivity = iso
//            sensorSensitivityProgress = progress
//            saveData()      //save shared preferences
//            false
//        }

        tvCustomSeekBar = view.findViewById(R.id.tvCustomSeekBar)
        tvCustomSeekBar.setValue(exposureProgress.toFloat())
        txt_3AValue.text = "1/${"%.1f".format(10.toDouble().pow(9.toDouble()) / exposureTime)}s"
        tvCustomSeekBar.setValueListener {
            var progress = tvCustomSeekBar.getValue()

            val range = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            val max = range!!.upper
            val min = range.lower
            val tvList = getTvList(min, max)

            if (tvCustomSeekBar.getMaxValue() != tvList.size) {
                tvCustomSeekBar.setMaxValue(tvList.size - 1)
                tvCustomSeekBar.setNumShow(tvList.size / 2)
            }

            if (progress.toInt() >= tvList.size)
                progress = (tvList.size - 1).toFloat()

            var ae: Long = (10.0.pow(9) / tvList[progress.toInt()]).roundToLong()
            if (ae < min) ae = min
            if (ae > max) ae = max
            if (exposureTime != ae)
                vibrate.vibrate(vibrationEffect)
            setExposureTime(ae)
            exposureTime = ae
            exposureProgress = progress.toInt()
            saveData()
            Log.i(TAG, "progress: $progress, ae: $ae")
        }
//        tvCustomSeekBar = view.findViewById(R.id.tvCustomSeekBar)
//        tvCustomSeekBar.progress = exposureProgress
//        tvCustomSeekBar.text = "1/${"%.1f".format(10.toDouble().pow(9.toDouble()) / exposureTime)}s"
//        tvCustomSeekBar.setOnTouchListener{_, _ ->
//            val progress = tvCustomSeekBar.progress
//            val range = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
//            val max = range!!.upper
//            val min = range.lower
////            val ae: Long = progress * (max - min) / 100 + min
//            val tvList = getTvList(min, max)
//            val index = progress * (tvList.size - 1) / tvCustomSeekBar.maxProgress
//            var ae: Long = (10.0.pow(9) / tvList[index]).roundToLong()
//            if (ae < min) ae = min
//            if (ae > max) ae = max
//
//            if (exposureTime != ae)
//                vibrate.vibrate(vibrationEffect)
//
//            setExposureTime(ae)
//            exposureTime = ae
//            exposureProgress = progress
//            saveData()      //save shared preferences
//            false
//        }

//        avCustomSeekBar = view.findViewById(R.id.avCustomSeekBar)
//        avCustomSeekBar.setValueListener {
//            val apertures = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
//            val apertureIndex = avCustomSeekBar.getValue()
//            if(apertureIndex < apertures?.size ?: 1)
//                aperture = apertures?.get(apertureIndex.toInt())!!
//            if (this.aperture != aperture)
//                vibrate.vibrate(vibrationEffect)
//
//            setApertureSize(aperture)
//            this.aperture = aperture
//            apertureProgress = avCustomSeekBar.getValue().toInt()
//            saveData()
//        }

//        avCustomSeekBar = view.findViewById(R.id.avCustomSeekBar)
//        avCustomSeekBar.progress = apertureProgress
//        avCustomSeekBar.text = "F$aperture"
//        avCustomSeekBar.setOnTouchListener{_, _ ->
//            val apertures = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
//            val apertureScale = avCustomSeekBar.maxProgress / (apertures?.size ?: 1)
//            val apertureIndex = avCustomSeekBar.progress/apertureScale
//
//            if(apertureIndex < apertures?.size ?: 1)
//                aperture = apertures?.get(apertureIndex)!!
//
//            if (this.aperture != aperture)
//                vibrate.vibrate(vibrationEffect)
//
//            setApertureSize(aperture)
//            this.aperture = aperture
//            apertureProgress = avCustomSeekBar.progress
//            saveData()
//            false
//        }

        val relativeLayout = view.findViewById<RelativeLayout>(R.id.layoutCenter)             //20200706 get  default focus area layout size
        relativeLayout.viewTreeObserver.addOnGlobalLayoutListener(object: ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                defaultFocusAreaWidth = relativeLayout.width
                defaultFocusAreaHeight = relativeLayout.height
                saveData()
                focusAreaLayout.viewTreeObserver.removeOnGlobalLayoutListener(this)
                focusAreaLayout.visibility = View.GONE
            }
        })


        setZoomArea(focusZoomScale)
        focusCustomSeekBar = view.findViewById(R.id.focusCustomSeekBar)
        focusCustomSeekBar.setValue(focusZoomScale.toFloat())
        focusCustomSeekBar.setValueListener {
            focusAreaLayout.visibility = View.VISIBLE

            val progress = focusCustomSeekBar.getValue().toInt()

            var focusIconFlag: FocusIconSize = FocusIconSize.ZOOM1_8
            when (progress) {
                0 -> {
                    focusIconFlag = FocusIconSize.ZOOM1_8
                    focusZoomScale = 0
                    txt_3AValue.text = "1/8"
                }
                1 -> {
                    focusIconFlag = FocusIconSize.ZOOM2_8
                    focusZoomScale = 1
                    txt_3AValue.text = "2/8"
                }
                2 -> {
                    focusIconFlag = FocusIconSize.ZOOM3_8
                    focusZoomScale = 2
                    txt_3AValue.text = "3/8"
                }
                3 -> {
                    focusIconFlag = FocusIconSize.ZOOM4_8
                    focusZoomScale = 3
                    txt_3AValue.text = "4/8"
                }
                4 -> {
                    focusIconFlag = FocusIconSize.ZOOM5_8
                    focusZoomScale = 4
                    txt_3AValue.text = "5/8"
                }
                5 -> {
                    focusIconFlag = FocusIconSize.ZOOM6_8
                    focusZoomScale = 5
                    txt_3AValue.text = "6/8"
                }
                6 -> {
                    focusIconFlag = FocusIconSize.ZOOM7_8
                    focusZoomScale = 6
                    txt_3AValue.text = "7/8"
                }
                7 -> {
                    focusIconFlag = FocusIconSize.ZOOM8_8
                    focusZoomScale = 7
                    txt_3AValue.text = "8/8"
                }
            }

            if (currentFocusIconFlag != focusIconFlag)
                vibrate.vibrate(vibrationEffect)

            setZoomArea(focusZoomScale)

            currentFocusIconFlag = focusIconFlag

            saveData()
            Log.i(TAG, "focusCustomSeekBar: $progress")
        }

        val imageView: ImageView = view.findViewById(R.id.btnPhotoBox)
        try {
            val imageGalleryUiModelList: MutableMap<String, ArrayList<ImageGalleryUiModel>> =
                MediaHelper.getImageGallery(this.context!!)

            val imageList:ArrayList<ImageGalleryUiModel> = imageGalleryUiModelList[ALBUM_NAME]!!
            file = File(imageList[imageList.size - 1].imageUri)
            imageView.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
        } catch (e: Exception) {
            e.printStackTrace()
        }

//        if (isManualEnable) {
//            btnManual.setImageResource(R.drawable.ic_manual_selection)
//            frameLayout3A.visibility = View.VISIBLE
//        } else {
//            btnManual.setImageResource(R.drawable.ic_manual)
//            frameLayout3A.visibility = View.INVISIBLE
//        }

        isAutoEnable = true
//        btnAuto.setImageResource(R.drawable.ic_auto_selection)

        progressbarShutter = view.findViewById(R.id.progressBar_shutter)

        val stamp = view.findViewById<View>(R.id.android)
        stamp.setOnClickListener(this)

        val gradation = view.findViewById<View>(R.id.gradation)
        gradation.setOnClickListener(this)

        focus = view.findViewById(R.id.focus_view)
        textureView = view.findViewById(R.id.texture)

        textureView.setOnTouchListener(surfaceTextureTouchListener)

//        if (m_address.contains(":"))
//            ConnectToDevice(context!!).execute()        // connect to bluetooth device
//        else
//            Toast.makeText(this.context, "This device has not matched any bluetooth", Toast.LENGTH_LONG).show()

        view.keepScreenOn = true
    }

    private fun getFingerSpacing(event: MotionEvent): Float{
        var x: Float = event.getX(0) - event.getX(1)
        var y: Float = event.getY(0) - event.getY(1)
        return sqrt((x * x + y * y).toDouble()).toFloat()
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()

        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (textureView.isAvailable) {
            openCamera(textureView.width, textureView.height)
        } else {
            textureView.surfaceTextureListener = surfaceTextureListener
        }

        if (!btnPicture.isEnabled)
            btnPicture.isEnabled = true

        if (progressbarShutter!!.visibility == View.VISIBLE)
            progressbarShutter!!.visibility = View.INVISIBLE

        sensorManager!!.registerListener(lightSensorListener, sensorManager!!.getDefaultSensor(Sensor.TYPE_LIGHT), SensorManager.SENSOR_DELAY_NORMAL)
        Log.i(TAG, "onResume")
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.btnPicture -> {
                val dateFormat = SimpleDateFormat("yyyy_MM_dd HH:mm:ss", Locale.TAIWAN)
                val currentDateTime: String = dateFormat.format(Date()) // Find todays date

                var isLEDScreen: Boolean = false

                var task = 0
                if (isColorTemperatureEnable) task += 1
                if (isContrastEnable) task += 1
                if (isRefreshRateEnable) task += 1

                if (task == 0) {        //20200623 no measurement item selected
                    val fileName = "$currentDateTime"
                    var pictureObject: PictureObject? = null
//                    var pictureObject: ContrastObject? = null
                    lifecycleScope.launch(Dispatchers.IO) {
                        view.post {
                            btnPicture.isEnabled = false
                        }

//                        if (mRawImageReader != null) {
//                            takeRawPhoto(fileName).use { result ->
//                                pictureObject = procedureContrast(result)
//                            }
//                        }

                        takeJPEGPhoto(fileName).use { result ->
                            pictureObject = procedureTakePicture(result)
                        }

                        view.post {
                            btnPhotoBox.setImageBitmap(BitmapFactory.decodeFile(pictureObject!!.file!!.absolutePath))
                            latestFileName = pictureObject!!.file!!.absolutePath
                        }

                        view.post {
                            btnPicture.isEnabled = true
                        }
                    }
                    return
                }

                readSettingData()

                var count = 0
                // Disable click listener to prevent multiple requests simultaneously in flight
                progressbarShutter?.max = 100
                progressbarShutter?.progress = 0
                progressbarShutter?.visibility = ProgressBar.VISIBLE

//                view.findViewById<View>(R.id.btnPicture).isEnabled = false

                // Perform I/O heavy operations in a different scope
                lifecycleScope.launch(Dispatchers.IO) {
//                    cameraHandler.postDelayed(restoreButtonAction, 15000)

                    btnPicture.isEnabled = false

                    var colorTemperatureObject: ColorTemperatureObject? = null
                    var contrastObject: ContrastObject? = null
                    var refreshRate: Int = 0
                    val colorTemperatureCounts = ArrayList<ColorTemperature>()

                    if (isContrastEnable) {
                        var scale = 100 /  (2 * (repeatTimesValue + 1))         //white and black times

                        val commandWhite = MBITSP2020().produceCommand(MBITSP2020.Mode.MODE2, 1920, 1080, 0, 0, 0, 0,
                            Color.argb(0, whitePeakValue, whitePeakValue, whitePeakValue),
                            Color.argb(0, 0, 0, 0),
                            Color.argb(0, 0, 0, 0),
                            Color.argb(0, 0, 0, 0))

                        val commandBlack = MBITSP2020().produceCommand(MBITSP2020.Mode.MODE2, 1920, 1080, 0, 0, 0, 0,
                            Color.argb(0, blackNadirValue, blackNadirValue, blackNadirValue),
                            Color.argb(0, 0, 0, 0),
                            Color.argb(0, 0, 0, 0),
                            Color.argb(0, 0, 0, 0))

                        Log.i(TAG, "Lux: ${lightSensorListener.getLux()}")
                        if (mRawImageReader != null) {
                            val fileName = "C_$currentDateTime"
                            var contrastObjectRAW: ContrastObject? = null
                            var pictureObject: PictureObject? = null
                            var contrastObjectJPEGWhite: ContrastObject? = null
                            var contrastObjectJPEGBlack: ContrastObject? = null

                            m_bluetoothSocket = sendToDevice(commandWhite)
                            readCommandHandler.post(readCommandRunnable)
                            Thread.sleep(1000)

                            takeRawPhoto(fileName).use { result ->
//                                contrastObjectRAW = procedureContrast(result)
                                pictureObject = procedureTakePicture(result)
                            }
                            takeJPEGPhoto().use { result ->
                                contrastObjectJPEGWhite = procedureContrast(result)
                            }
                            progressbarShutter?.progress = scale

                            for (i in 0 until repeatTimesValue) {       //do repeat calculate
                                Thread.sleep(200)

                                takeJPEGPhoto().use { result ->
                                    val contrastObject  = procedureContrast(result)
                                    contrastObjectJPEGWhite!!.lum1 += contrastObject.lum1
                                }

                                progressbarShutter?.progress = progressbarShutter?.progress!!.plus(scale)
                            }
                            contrastObjectJPEGWhite!!.lum1 /= repeatTimesValue + 1


                            m_bluetoothSocket = sendToDevice(commandBlack)
                            readCommandHandler.post(readCommandRunnable)
                            Thread.sleep(1000)

                            takeRawPhoto(fileName).use { result ->
//                                contrastObjectRAW = procedureContrast(result)
                                pictureObject = procedureTakePicture(result)
                            }
                            takeJPEGPhoto().use { result ->
                                contrastObjectJPEGBlack = procedureContrast(result)
                            }
                            progressbarShutter?.progress = progressbarShutter?.progress!!.plus(scale)


                            for (i in 0 until repeatTimesValue) {       //do repeat calculate
                                Thread.sleep(200)

                                takeJPEGPhoto().use { result ->
                                    val contrastObject = procedureContrast(result)
                                    contrastObjectJPEGBlack!!.lum1 += contrastObject.lum1
                                }

                                progressbarShutter?.progress = progressbarShutter?.progress!!.plus(scale)
                            }
                            contrastObjectJPEGBlack!!.lum1 /= repeatTimesValue + 1

                            view.post {
                                showToast("Picture Done!")
                            }

//                            contrastObject = contrastObjectRAW
                            contrastObject = contrastObjectJPEGWhite
                            contrastObject!!.file = pictureObject!!.file
                            contrastObject.lum1 = contrastObjectJPEGWhite!!.lum1
                            contrastObject.lum2 = contrastObjectJPEGBlack!!.lum1
                            contrastObject.contrast = if (contrastObject.lum1 > contrastObject.lum2) (contrastObject.lum1 / contrastObject.lum2).roundToDecimalPlaces(0)
                            else (contrastObject.lum2 / contrastObject.lum1).roundToDecimalPlaces(0)


                            if (contrastObject.contrast < 20 && (contrastObject.contrast.isNaN() || contrastObject.contrast.isNaN()))
                                contrastObject = ContrastObject(0.0, 0.0, lightSensorListener.getLux().toDouble(), pictureObject?.file)

                            try {
                                view.post {
                                    btnPhotoBox.setImageBitmap(BitmapFactory.decodeFile(contrastObject!!.file!!.absolutePath))
                                    latestFileName = contrastObject!!.file!!.absolutePath
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        } else {
                            val fileName = "C_$currentDateTime"
                            var contrastObjectJPEGWhite: ContrastObject? = null
                            var contrastObjectJPEGBlack: ContrastObject? = null

                            m_bluetoothSocket = sendToDevice(commandWhite)
                            readCommandHandler.post(readCommandRunnable)
                            Thread.sleep(1000)

                            takeJPEGPhoto(fileName).use { result ->
                                contrastObjectJPEGWhite = procedureContrast(result)
                            }
                            progressbarShutter?.progress = scale

                            for (i in 0 until repeatTimesValue) {
                                Thread.sleep(200)

                                takeJPEGPhoto().use { result ->
                                    val contrastObject = procedureContrast(result)
                                    contrastObjectJPEGWhite!!.lum1 += contrastObject.lum1
                                }

                                progressbarShutter?.progress = progressbarShutter?.progress!!.plus(scale)
                            }
                            contrastObjectJPEGWhite!!.lum1 /= repeatTimesValue + 1

                            m_bluetoothSocket = sendToDevice(commandBlack)
                            readCommandHandler.post(readCommandRunnable)
                            Thread.sleep(1000)

                            takeJPEGPhoto(fileName).use { result ->
                                contrastObjectJPEGBlack = procedureContrast(result)
                            }
                            progressbarShutter?.progress = progressbarShutter?.progress!!.plus(scale)

                            for (i in 0 until repeatTimesValue) {
                                Thread.sleep(200)

                                takeJPEGPhoto().use { result ->
                                    val contrastObject = procedureContrast(result)
                                    contrastObjectJPEGBlack!!.lum1 += contrastObject.lum1
                                }
                                progressbarShutter?.progress = progressbarShutter?.progress!!.plus(scale)
                            }
                            contrastObjectJPEGBlack!!.lum1 /= repeatTimesValue + 1

                            view.post {
                                showToast("Picture Done!")
                            }

                            contrastObject = contrastObjectJPEGWhite
                            contrastObject!!.lum2 = contrastObjectJPEGBlack!!.lum1
                            contrastObject.contrast = if (contrastObject.lum1 > contrastObject.lum2) (contrastObject.lum1 / contrastObject.lum2).roundToDecimalPlaces(0)
                            else (contrastObject.lum2 / contrastObject.lum1).roundToDecimalPlaces(0)
                            contrastObject.file = contrastObjectJPEGBlack!!.file

                            contrastObject = if (contrastObject.contrast.isNaN()) ContrastObject(0.0, 0.0, lightSensorListener.getLux().toDouble(), contrastObject.file)
                            else contrastObject

                            try {
                                view.post {
                                    btnPhotoBox.setImageBitmap(BitmapFactory.decodeFile(contrastObject!!.file!!.absolutePath))
                                    latestFileName = contrastObject!!.file!!.absolutePath
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }

                    Thread.sleep(200)
                    if (isColorTemperatureEnable) {     //set fixed iso 320 & tv 250
                        var scale = 100 / (repeatTimesValue + 1)
                        val command = MBITSP2020().produceCommand(MBITSP2020.Mode.MODE2, 1920, 1080, 0, 0,
                            0, 0,
                            Color.argb(0, whitePeakValue, whitePeakValue, whitePeakValue),
                            Color.argb(0, 0, 0, 0),
                            Color.argb(0, 0, 0, 0),
                            Color.argb(0, 0, 0, 0))
                        m_bluetoothSocket = sendToDevice(command)
                        readCommandHandler.post(readCommandRunnable)

                        val fileName = "T_$currentDateTime"

                        if (mRawImageReader != null) {
                            takeRawPhoto(fileName).use { result ->
                                val pictureObject = procedureTakePicture(result)
                            }
                        }

                        takeJPEGPhoto(fileName).use { result ->
                            colorTemperatureObject = procedureColorTemperature(result)
                            colorTemperatureCounts.add(colorTemperatureObject!!.colorTemperature)
                        }
                        progressbarShutter?.progress = scale

                        for (i in 0 until repeatTimesValue) {           //do repeat calculate cct
                            Thread.sleep(200)

                            takeJPEGPhoto().use { result ->
                                val colorObject = procedureColorTemperature(result)
                                when {
                                    colorTemperatureObject != null -> {
                                        colorTemperatureObject!!.cct += colorObject.cct
                                        colorTemperatureObject!!.cxcy[0] += colorObject.cxcy[0]
                                        colorTemperatureObject!!.cxcy[1] += colorObject.cxcy[1]
                                        colorTemperatureCounts.add(colorObject.colorTemperature)
                                    }
                                    else -> {

                                    }
                                }
                            }

                            progressbarShutter?.progress = progressbarShutter?.progress!!.plus(scale)
                        }

                        when {
                            colorTemperatureObject != null -> {
                                colorTemperatureObject!!.cct /= repeatTimesValue + 1
                                colorTemperatureObject!!.cxcy[0] = (colorTemperatureObject!!.cxcy[0] / (repeatTimesValue + 1)).roundToDecimalPlaces(2)
                                colorTemperatureObject!!.cxcy[1] = (colorTemperatureObject!!.cxcy[1] / (repeatTimesValue + 1)).roundToDecimalPlaces(2)
                                val countOfWarm = colorTemperatureCounts.count { it == ColorTemperature.Warm }
                                val countOfCold = colorTemperatureCounts.count { it == ColorTemperature.Cold }
                                val countOfNormal = colorTemperatureCounts.count {it == ColorTemperature.Normal}
                                when {
                                    (countOfWarm >= countOfCold && countOfWarm >= countOfNormal) -> {
                                        colorTemperatureObject!!.colorTemperature = ColorTemperature.Warm
                                    }
                                    (countOfCold >= countOfWarm && countOfCold >= countOfNormal) -> {
                                        colorTemperatureObject!!.colorTemperature = ColorTemperature.Cold
                                    }
                                    else -> {
                                        colorTemperatureObject!!.colorTemperature = ColorTemperature.Normal
                                    }
                                }
                            }
                            else -> {

                            }
                        }

                        try {
                            view.post {
                                showToast("Picture Done!")
                            }
                            view.post {
                                btnPhotoBox.setImageBitmap(BitmapFactory.decodeFile(colorTemperatureObject!!.file!!.absolutePath))
                                latestFileName = colorTemperatureObject!!.file!!.absolutePath
                            }
                        }catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    Thread.sleep(200)
                    if (isRefreshRateEnable) {      //from tv: 1000 ~ 4000 and fixed iso 800
                        var scale = 100 / (repeatTimesValue + 1)
                        val command = MBITSP2020().produceCommand(MBITSP2020.Mode.MODE2, 1920, 1080, 0, 0,
                            0, 0,
                            Color.argb(0, whitePeakValue, whitePeakValue, whitePeakValue),
                            Color.argb(0, 0, 0, 0),
                            Color.argb(0, 0, 0, 0),
                            Color.argb(0, 0, 0, 0))
                        m_bluetoothSocket = sendToDevice(command)
                        readCommandHandler.post(readCommandRunnable)

                        val exposureTimeRange = intArrayOf(1500, 2000, 2500, 3000, 3500, 4000)
                        val fixedISO = 60
                        val circleCount = ArrayList<Int>()
                        setSensorSensitivity(fixedISO)

                        var prevRate = 0.0
                        var assigned = false

                        val files: ArrayList<File?> = ArrayList()

                        val fileName = "F_$currentDateTime"

                        setExposureTime((10.0.pow(9) / 1000).roundToLong())      //start from 1000
                        Thread.sleep(50)

                        if (mRawImageReader != null) {
                            takeRawPhoto(fileName).use { result ->
                                val pictureObject = procedureTakePicture(result)
                            }
                        }

                        takeJPEGPhoto(fileName).use { result ->
                            val countOfBlack = procedureRefreshRate(result)
                            prevRate = countOfBlack.blackOfCount / countOfBlack.totalCount

                            files.add(countOfBlack.file)

                            isLEDScreen = prevRate > 0.9

                            Log.i(TAG, "Black rate: $prevRate, Circles: ${countOfBlack.circles}")
                        }
                        progressbarShutter?.progress = scale

                        for (i in 0 until repeatTimesValue) {
                            Thread.sleep(500)

                            takeJPEGPhoto().use { result ->
                                val countOfBlack = procedureRefreshRate(result)
                                prevRate += countOfBlack.blackOfCount / countOfBlack.totalCount
                                isLEDScreen = prevRate > 0.9

                                Log.i(TAG, "Black rate: $prevRate, Circles: ${countOfBlack.circles}")
                            }

                            progressbarShutter?.progress = progressbarShutter?.progress!!.plus(scale)
                        }
                        prevRate /= refreshRate + 1

                        if (prevRate < 0.2) {
                            exposureTimeRange.forEach {
                                if (!assigned) {
                                    val ae: Long = (10.0.pow(9) / it).roundToLong()
                                    setExposureTime(ae)
                                    Thread.sleep(50)

                                    if (mRawImageReader != null) {
                                        takeRawPhoto(fileName).use { result ->
                                            val pictureObject = procedureTakePicture(result)
                                        }
                                    }

                                    takeJPEGPhoto(fileName).use { result ->
                                        val countOfBlack = procedureRefreshRate(result)

                                        val curRate = countOfBlack.blackOfCount / countOfBlack.totalCount

                                        if (curRate > 0.2) {
                                            if (it in 1001 .. 2499) refreshRate = 2000
                                            if (it in 2500 .. 3000) refreshRate = 3000
                                            if (it > 3000) refreshRate = 4000
                                            assigned = true
                                        }

                                        files.add(countOfBlack.file)

                                        Log.i(TAG, "Black rate: $curRate")
                                    }
                                    progressbarShutter?.progress = scale

                                    for (i in 0 until repeatTimesValue) {
                                        Thread.sleep(200)

                                        takeJPEGPhoto().use { result ->
                                            val countOfBlack = procedureRefreshRate(result)

                                            val curRate = countOfBlack.blackOfCount / countOfBlack.totalCount

                                            if (curRate > 0.2) {
                                                if (it in 1001 .. 2499) refreshRate = 2000
                                                if (it in 2500 .. 3000) refreshRate = 3000
                                                if (it > 3000) refreshRate = 4000
                                                assigned = true
                                            }
                                            Log.i(TAG, "Black rate: $curRate")
                                        }

                                        progressbarShutter?.progress = progressbarShutter?.progress!!.plus(scale)
                                    }
                                }
                            }
                        } else {
                            refreshRate = 1000
                            assigned = true
                        }

                        if (!assigned)
                            refreshRate = 4000

                        view.post {
                            showToast("Picture Done!")
                        }

                        try {
                            view.post {
                                btnPhotoBox.setImageBitmap(BitmapFactory.decodeFile(files[files.size - 1]!!.absolutePath))
                                latestFileName = files[files.size - 1]!!.absolutePath
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }


                    if (!isContrastEnable) contrastObject = ContrastObject(0.0, 0.0, 0.0)
                    if (!isColorTemperatureEnable) colorTemperatureObject = ColorTemperatureObject(
                        doubleArrayOf(0.0, 0.0), 0.0, ColorTemperature.None
                    )
                    if (!isRefreshRateEnable) refreshRate = 0

                    val historyViewModel = ViewModelProvider(this@Camera2BasicFragment).get(HistoryViewModel::class.java)

                    val contrastDescription = if (isContrastEnable) contrastObject!!.contrast.toInt().toString() + " (" +
                            contrastObject.lum1.toInt().toString() + " : " + contrastObject.lum2.toInt().toString() + ")"
                    else "0"

                    val colorTemperatureDescription = if (isColorTemperatureEnable) colorTemperatureObject!!.colorTemperature.name + " (" +
                            colorTemperatureObject!!.cxcy[0].toString() + ", " + colorTemperatureObject!!.cxcy[1].toString() + ")"
                    else ColorTemperature.None.name

                    val refreshDescription = if (isRefreshRateEnable && !isLEDScreen) "$refreshRate Hz"
                    else if (isRefreshRateEnable && isLEDScreen) "over 4000 Hz"
                    else "0"

                    historyViewModel.insert(History(currentDateTime, contrastDescription, refreshDescription, colorTemperatureDescription))

                    Log.i(TAG, "Contrast: ${contrastObject!!.contrast}, Refresh Rate: $refreshRate, Color Temperature: ${colorTemperatureObject!!.colorTemperature}")

                    cameraHandler.removeCallbacks(restoreButtonAction)
                    cameraHandler.post(restoreButtonAction)
                }

            }
//            R.id.btnAuto -> {
//                val btnAuto = view.findViewById<ImageButton>(R.id.btnAuto)
//                isAutoEnable = !isAutoEnable
//
//                if(isAutoEnable){
//                    btnAuto.setImageResource(R.drawable.ic_auto_selection)
//
//                    btnManual.setImageResource(R.drawable.ic_manual)
//                    frameLayout3A.visibility = View.INVISIBLE
//                    frameLayout3AValue.visibility = View.INVISIBLE
//                    //avCustomSeekBar.visibility = View.INVISIBLE
//                    tvCustomSeekBar.visibility = View.INVISIBLE
//                    isoCustomSeekBar.visibility = View.INVISIBLE
//                    focusCustomSeekBar.visibility = View.INVISIBLE
//                }else{
//                    btnAuto.setImageResource(R.drawable.ic_auto)
//                }
//
//                isManualEnable = false
//                automate3A()
//                saveData()
//            }
            R.id.btnContrast -> {
                val btnContrast = view.findViewById<ImageView>(R.id.btnContrast)
                isContrastEnable = !isContrastEnable

                readSettingData()

                if(isContrastEnable){
                    manual3A(Measurement.Contrast)

                    view.post {
                        showToast(50.0f, "Contrast", Color.argb(0xAA, 0xAA, 0x00, 0x00))
                    }

                    btnContrast.setImageResource(R.drawable.ic_contrast_selection)
//                    contrastTargetLayout.visibility = View.VISIBLE
                    focusAreaLayout.visibility = View.VISIBLE
                    frameLayout3A.visibility = View.VISIBLE
                    frameLayout3AValue.visibility = View.VISIBLE
                    tvCustomSeekBar.visibility = View.VISIBLE
                    isoCustomSeekBar.visibility = View.INVISIBLE
                    focusCustomSeekBar.visibility = View.INVISIBLE
                    txt_3AValue.text = "1/${"%.1f".format(10.toDouble().pow(9.toDouble()) / exposureTime)}s"

                    isRefreshRateEnable = false
                    btnRefreshRate.setImageResource(R.drawable.ic_refresh_rate)
                    isColorTemperatureEnable = false
                    btnColorTemperature.setImageResource(R.drawable.ic_color_temperature)

                    isAutoEnable = false
//                    btnAuto.setImageResource(R.drawable.ic_auto)

                    lifecycleScope.launch(Dispatchers.IO) {
                        val command = MBITSP2020().produceCommand(MBITSP2020.Mode.MODE2, 1920, 1080, 0, 0,
                            0, 0,
                            Color.argb(0, whitePeakValue, whitePeakValue, whitePeakValue),
                            Color.argb(0, 0, 0, 0),
                            Color.argb(0, 0, 0, 0),
                            Color.argb(0, 0, 0, 0))
                        m_bluetoothSocket = sendToDevice(command)
                        readCommandHandler.post(readCommandRunnable)
                    }

                    currentMeasurement = Measurement.Contrast
                }else{
                    automate3A()

                    btnContrast.setImageResource(R.drawable.ic_contrast)
//                    contrastTargetLayout.visibility = View.INVISIBLE
                    focusAreaLayout.visibility = View.INVISIBLE
                    frameLayout3A.visibility = View.INVISIBLE
                    frameLayout3AValue.visibility = View.INVISIBLE
                    tvCustomSeekBar.visibility = View.INVISIBLE
                    isoCustomSeekBar.visibility = View.INVISIBLE
                    focusCustomSeekBar.visibility = View.INVISIBLE

                    isAutoEnable = true
//                    btnAuto.setImageResource(R.drawable.ic_auto_selection)
                }

                saveData()
            }
            R.id.btnRefreshRate -> {
                val btnRefreshRate = view.findViewById<ImageView>(R.id.btnRefreshRate)
                isRefreshRateEnable = !isRefreshRateEnable

                readSettingData()

                if(isRefreshRateEnable){
                    manual3A(Measurement.RefreshRate)

                    view.post {
                        showToast(50.0f, "Refresh Rate", Color.argb(0xAA, 0xAA, 0x00, 0x00))
                    }

                    btnRefreshRate.setImageResource(R.drawable.ic_refresh_rate_selection)
//                    contrastTargetLayout.visibility = View.INVISIBLE
                    focusAreaLayout.visibility = View.VISIBLE
                    frameLayout3A.visibility = View.VISIBLE
                    frameLayout3AValue.visibility = View.VISIBLE
                    tvCustomSeekBar.visibility = View.VISIBLE
                    isoCustomSeekBar.visibility = View.INVISIBLE
                    focusCustomSeekBar.visibility = View.INVISIBLE
                    txt_3AValue.text = "1/${"%.1f".format(10.toDouble().pow(9.toDouble()) / exposureTime)}s"

                    isContrastEnable = false
                    btnContrast.setImageResource(R.drawable.ic_contrast)
                    isColorTemperatureEnable = false
                    btnColorTemperature.setImageResource(R.drawable.ic_color_temperature)

                    isAutoEnable = false
//                    btnAuto.setImageResource(R.drawable.ic_auto)

                    lifecycleScope.launch(Dispatchers.IO) {
                        val command = MBITSP2020().produceCommand(MBITSP2020.Mode.MODE2, 1920, 1080, 0, 0,
                            0, 0,
                            Color.argb(0, whitePeakValue, whitePeakValue, whitePeakValue),
                            Color.argb(0, 0, 0, 0),
                            Color.argb(0, 0, 0, 0),
                            Color.argb(0, 0, 0, 0))
                        m_bluetoothSocket = sendToDevice(command)
                        readCommandHandler.post(readCommandRunnable)
                    }

                    currentMeasurement = Measurement.RefreshRate
                }else{
                    automate3A()

                    btnRefreshRate.setImageResource(R.drawable.ic_refresh_rate)
                    focusAreaLayout.visibility = View.INVISIBLE
                    frameLayout3A.visibility = View.INVISIBLE
                    frameLayout3AValue.visibility = View.INVISIBLE
                    tvCustomSeekBar.visibility = View.INVISIBLE
                    isoCustomSeekBar.visibility = View.INVISIBLE
                    focusCustomSeekBar.visibility = View.INVISIBLE

                    isAutoEnable = true
//                    btnAuto.setImageResource(R.drawable.ic_auto_selection)
                }

                saveData()
            }
            R.id.btnColorTemperature -> {
                val btnColorTemperature = view.findViewById<ImageView>(R.id.btnColorTemperature)
                isColorTemperatureEnable = !isColorTemperatureEnable

                readSettingData()

                if(isColorTemperatureEnable){
                    manual3A(Measurement.ColorTemperature)

                    view.post {
                        showToast(50.0f, "Color Temp.", Color.argb(0xAA, 0xAA, 0x00, 0x00))
                    }

                    btnColorTemperature.setImageResource(R.drawable.ic_color_temperature_selection)
//                    contrastTargetLayout.visibility = View.INVISIBLE
                    focusAreaLayout.visibility = View.VISIBLE
                    frameLayout3A.visibility = View.VISIBLE
                    frameLayout3AValue.visibility = View.VISIBLE
                    tvCustomSeekBar.visibility = View.VISIBLE
                    isoCustomSeekBar.visibility = View.INVISIBLE
                    focusCustomSeekBar.visibility = View.INVISIBLE
                    txt_3AValue.text = "1/${"%.1f".format(10.toDouble().pow(9.toDouble()) / exposureTime)}s"

                    isContrastEnable = false
                    btnContrast.setImageResource(R.drawable.ic_contrast)
                    isRefreshRateEnable = false
                    btnRefreshRate.setImageResource(R.drawable.ic_refresh_rate)

                    isAutoEnable = false
//                    btnAuto.setImageResource(R.drawable.ic_auto)

                    lifecycleScope.launch(Dispatchers.IO) {
                        val command = MBITSP2020().produceCommand(MBITSP2020.Mode.MODE2, 1920, 1080, 0, 0,
                            0, 0,
                            Color.argb(0, whitePeakValue, whitePeakValue, whitePeakValue),
                            Color.argb(0, 0, 0, 0),
                            Color.argb(0, 0, 0, 0),
                            Color.argb(0, 0, 0, 0))
                        m_bluetoothSocket = sendToDevice(command)
                        readCommandHandler.post(readCommandRunnable)
                    }

                    currentMeasurement = Measurement.ColorTemperature
                }else{
                    automate3A()

                    btnColorTemperature.setImageResource(R.drawable.ic_color_temperature)
                    focusAreaLayout.visibility = View.INVISIBLE
                    frameLayout3A.visibility = View.INVISIBLE
                    frameLayout3AValue.visibility = View.INVISIBLE
                    tvCustomSeekBar.visibility = View.INVISIBLE
                    isoCustomSeekBar.visibility = View.INVISIBLE
                    focusCustomSeekBar.visibility = View.INVISIBLE

                    isAutoEnable = true
//                    btnAuto.setImageResource(R.drawable.ic_auto_selection)
                }

                saveData()
            }
//            R.id.btnManual ->{
//                val btnManual = view.findViewById<ImageButton>(R.id.btnManual)
//                frameLayout3A.visibility = View.VISIBLE
//                frameLayout3AValue.visibility = View.VISIBLE
//
//                isManualEnable = !isManualEnable
//
//                if(isManualEnable){
//                    btnManual.setImageResource(R.drawable.ic_manual_selection)
//                }else{
//                    btnManual.setImageResource(R.drawable.ic_manual)
//                }
//
//                manual3A(Measurement.None)
//
//                isAutoEnable = false
//                btnAuto.setImageResource(R.drawable.ic_auto)
//                saveData()
//            }
            R.id.btnPhotoBox -> {
//                pickPictureFromGallery()

                val fragment = PhotoboxFragment()
                val fragmentManager = activity!!.supportFragmentManager
                val fragmentTransaction = fragmentManager.beginTransaction()
                fragmentTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                fragmentTransaction.add(R.id.container, fragment, "PhotoboxFragment")
                fragmentTransaction.addToBackStack("Camera2BasicFragment")
                fragmentTransaction.commit()
                Log.i(TAG, "R.id.btnPhotoBox")

                /////////////////////////////////////////////////////////////////////////////
                Log.i(TAG, "latestFileName: $latestFileName")
                val images= ArrayList<com.example.imagegallery.adapter.Image>()
                try {
                    val imageGalleryUiModelList: MutableMap<String, ArrayList<ImageGalleryUiModel>> =
                        MediaHelper.getImageGallery(this.context!!)

                    val imageList:ArrayList<ImageGalleryUiModel> = imageGalleryUiModelList[ALBUM_NAME]!!
                    if (latestFileName != null) {
                        imageList.forEach {
                            val strings = latestFileName!!.split("/")
                            val name = strings[strings.size - 1]
                            if (it.imageUri.contains(name))
                                images.add(com.example.imagegallery.adapter.Image(it.imageUri, it.imageUri, false))
                        }
                    } else {
                        val imageGalleryUiModel = imageList[imageList.size - 1]
                        images.add(com.example.imagegallery.adapter.Image(imageGalleryUiModel.imageUri , imageGalleryUiModel.imageUri, false))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                val position = 0
                val bundle = Bundle()
                bundle.putSerializable("images", images)
                bundle.putInt("position", position)

                val galleryFullscreenManager = activity!!.supportFragmentManager
                val galleryFullscreenTransaction = galleryFullscreenManager.beginTransaction()
                val galleryFragment = GalleryFullscreenFragment()
                galleryFragment.arguments = bundle
                galleryFragment.show(galleryFullscreenTransaction, "gallery")
            }
            R.id.btnRecordBar ->{
                val fragment = HistoryFragment()
                val fragmentManager = activity!!.supportFragmentManager
                val fragmentTransaction = fragmentManager.beginTransaction()
                fragmentTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                fragmentTransaction.add(R.id.container, fragment, "HistoryFragment")
                fragmentTransaction.addToBackStack("Camera2BasicFragment")
                fragmentTransaction.commit()
                Log.i(TAG, "R.id.btnRecordBar")
            }
            R.id.btnSetting ->{
                val fragment = SettingFragment()
                val fragmentManager = activity!!.supportFragmentManager
                val fragmentTransaction = fragmentManager.beginTransaction()
                fragmentTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                fragmentTransaction.add(R.id.container, fragment, "SettingFragment")
                fragmentTransaction.addToBackStack("Camera2BasicFragment")
                fragmentTransaction.commit()
                Log.i(TAG, "R.id.btnSetting")
            }
            R.id.btnExposure -> {
                tvCustomSeekBar.visibility = View.VISIBLE

                isoCustomSeekBar.visibility = View.INVISIBLE

//                avCustomSeekBar.visibility = View.INVISIBLE
                focusCustomSeekBar.visibility = View.INVISIBLE

                txt_3AValue.text = "1/${"%.1f".format(10.toDouble().pow(9.toDouble()) / exposureTime)}s"
            }
            R.id.btnISO -> {
                tvCustomSeekBar.visibility = View.INVISIBLE

                isoCustomSeekBar.visibility = View.VISIBLE

//                avCustomSeekBar.visibility = View.INVISIBLE
                focusCustomSeekBar.visibility = View.INVISIBLE

                txt_3AValue.text = "ISO $sensorSensitivity"
            }
//            R.id.btnAperture -> {
//                tvCustomSeekBar.visibility = View.INVISIBLE
//
//                isoCustomSeekBar.visibility = View.INVISIBLE
//
//                avCustomSeekBar.visibility = if (avCustomSeekBar.visibility == View.INVISIBLE) View.VISIBLE
//                else View.INVISIBLE
//
//                focusCustomSeekBar.visibility = View.INVISIBLE
//            }
            R.id.btnFocus -> {
                tvCustomSeekBar.visibility = View.INVISIBLE

                isoCustomSeekBar.visibility = View.INVISIBLE

//                avCustomSeekBar.visibility = View.INVISIBLE

                focusCustomSeekBar.visibility = View.VISIBLE

                txt_3AValue.text = "${focusZoomScale + 1}/8"
            }
        }
    }

    private val restoreButtonAction = Runnable {
        view?.post {
            if (!btnPicture.isEnabled)
                btnPicture.isEnabled = true

            if (progressbarShutter?.visibility == View.VISIBLE)
                progressbarShutter?.visibility = View.INVISIBLE
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager!!.unregisterListener(lightSensorListener)
        Log.i(TAG, "onPause")
    }

    override fun onStop() {
        super.onStop()

        lifecycleScope.launch(Dispatchers.IO) {
            m_bluetoothSocket = sendToDevice(MinimumTestPattern)
            readCommandHandler.post(readCommandRunnable)
        }
        Log.i(TAG, "onStop")
    }

    override fun onStart() {
        super.onStart()
        Log.i(TAG, "onStart")
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraThread.quitSafely()
        imageReaderThread.quitSafely()
        disconnect()
    }

    /**
     * Opens the camera specified by [Camera2BasicFragment.cameraId].
     */
    private fun openCamera(width: Int, height: Int) {
        if (activity == null) {
            Log.e(TAG, "activity is not ready!")
            return
        }

        val permission = ContextCompat.checkSelfPermission(activity!!, Manifest.permission.CAMERA)
        if (permission != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission()
            return
        }

        setUpCameraOutputs(width, height)
        configureTransform(width, height)

        val manager = activity?.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            // Wait for camera to open - 2.5 seconds is sufficient
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }
            manager.openCamera(cameraId, stateCallback, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera opening.", e)
        }
    }

    private fun requestCameraPermission() {
        if (PermissionsFragment.hasPermissions(this.context!!)) {

        } else {
            requestPermissions(PERMISSIONS_REQUIRED, PERMISSIONS_REQUEST_CODE)
        }
    }

    /**
     * Starts a background thread and its [Handler].
     */
    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread?.looper)
    }

    /**
     * Stops the background thread and its [Handler].
     */
    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, e.toString())
        }
    }

    /**
     * Sets up member variables related to camera.
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    private fun setUpCameraOutputs(width: Int, height: Int) {
        val manager = activity?.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            for (cameraId in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(cameraId)

                this.characteristics = characteristics
                // We don't use a front facing camera in this sample.
                val cameraDirection = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (cameraDirection != null &&
                    cameraDirection == CameraCharacteristics.LENS_FACING_FRONT
                ) {
                    continue
                }

                val map = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
                ) ?: continue


                // For still image captures, we use the largest available size.

                val capabilities: IntArray? = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)

                val largestRawImageSize: Size?
                if (capabilities?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)!!){
                    Log.i(TAG, "This device does support Raw")

                    largestRawImageSize = Collections.max(
                        listOf(*map.getOutputSizes(RAW_FORMAT)),
                        CompareSizesByArea()
                    )

                    mRawImageReader = ImageReader.newInstance(
                        largestRawImageSize.width,
                        largestRawImageSize.height,
                        RAW_FORMAT,
                        IMAGE_BUFFER_SIZE
                    )
                }else{
                    Log.i(TAG, "This device doesn't support Raw")

                    largestRawImageSize = null
                    mRawImageReader = null
                }

                val largestImageSize = Collections.max(
                    listOf(*map.getOutputSizes(JPEG_FORMAT)),
                    CompareSizesByArea()
                )

                mImageReader = ImageReader.newInstance(
                    largestImageSize.width,
                    largestImageSize.height,
                    JPEG_FORMAT, IMAGE_BUFFER_SIZE
                )

                // Find out if we need to swap dimension to get the preview size relative to sensor
                // coordinate.
                val displayRotation = activity!!.windowManager.defaultDisplay.rotation

                sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!
                val swappedDimensions = areDimensionsSwapped(displayRotation)

                val displaySize = Point()
                activity!!.windowManager.defaultDisplay.getSize(displaySize)
                val rotatedPreviewWidth = if (swappedDimensions) height else width
                val rotatedPreviewHeight = if (swappedDimensions) width else height
                var maxPreviewWidth = if (swappedDimensions) displaySize.y else displaySize.x
                var maxPreviewHeight = if (swappedDimensions) displaySize.x else displaySize.y

                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) maxPreviewWidth = MAX_PREVIEW_WIDTH
                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) maxPreviewHeight = MAX_PREVIEW_HEIGHT

                // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.

                previewSize = if(largestRawImageSize != null) {
                    chooseOptimalSize(
                        map.getOutputSizes(SurfaceTexture::class.java),
                        rotatedPreviewWidth, rotatedPreviewHeight,
                        maxPreviewWidth, maxPreviewHeight,
                        largestRawImageSize
                    )
                } else {
                    chooseOptimalSize(
                        map.getOutputSizes(SurfaceTexture::class.java),
                        rotatedPreviewWidth, rotatedPreviewHeight,
                        maxPreviewWidth, maxPreviewHeight,
                        largestImageSize
                    )
                }


                // We fit the aspect ratio of TextureView to the size of preview we picked.
                if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    textureView.setAspectRatio(previewSize.width, previewSize.height)
                } else {
                    textureView.setAspectRatio(previewSize.height, previewSize.width)
                }

                // Check if the flash is supported.
                flashSupported =
                    characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true

                this.cameraId = cameraId

                // We've found a viable camera and finished setting up member variables,
                // so we don't need to iterate through other available cameras.
                return
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        } catch (e: NullPointerException) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            ErrorDialog.newInstance(getString(R.string.camera_error))
                .show(childFragmentManager, FRAGMENT_DIALOG)
        }
    }

    /**
     * Configures the necessary [android.graphics.Matrix] transformation to `textureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `textureView` is fixed.
     *
     * @param viewWidth  The width of `textureView`
     * @param viewHeight The height of `textureView`
     */
    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        activity ?: return
        val rotation = activity!!.windowManager.defaultDisplay.rotation
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0f, 0f, previewSize.height.toFloat(), previewSize.width.toFloat())

        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()

        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            val scale = Math.max(
                viewHeight.toFloat() / previewSize.height,
                viewWidth.toFloat() / previewSize.width
            )
            with(matrix) {
                setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
                postScale(scale, scale, centerX, centerY)
                postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
            }
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180f, centerX, centerY)
        }
        textureView.setTransform(matrix)
    }

    /**
     * Determines if the dimensions are swapped given the phone's current rotation.
     *
     * @param displayRotation The current rotation of the display
     *
     * @return true if the dimensions are swapped, false otherwise.
     */
    private fun areDimensionsSwapped(displayRotation: Int): Boolean {
        var swappedDimensions = false
        when (displayRotation) {
            Surface.ROTATION_0, Surface.ROTATION_180 -> {
                if (sensorOrientation == 90 || sensorOrientation == 270) {
                    swappedDimensions = true
                }
            }
            Surface.ROTATION_90, Surface.ROTATION_270 -> {
                if (sensorOrientation == 0 || sensorOrientation == 180) {
                    swappedDimensions = true
                }
            }
            else -> {
                Log.e(TAG, "Display rotation is invalid: $displayRotation")
            }
        }
        return swappedDimensions
    }

    /**
     * Creates a new [CameraCaptureSession] for camera preview.
     */
    private fun createCameraPreviewSession() {
        try {
            val texture = textureView.surfaceTexture

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(previewSize.width, previewSize.height)

            // This is the output Surface we need to start preview.
            val surface = Surface(texture)

            // We set up a CaptureRequest.Builder with the output Surface.
            previewRequestBuilder = cameraDevice!!.createCaptureRequest(
                CameraDevice.TEMPLATE_PREVIEW
            )
            previewRequestBuilder.addTarget(surface)

            //initialize3A()            //20200622 Craig

            val outputs = if(mRawImageReader == null) {
                listOf(surface, mImageReader.surface)
            } else {
                listOf(surface, mImageReader.surface, mRawImageReader!!.surface)
            }

            // Here, we create a CameraCaptureSession for camera preview.
            cameraDevice?.createCaptureSession(
                outputs,
                object : CameraCaptureSession.StateCallback() {

                    override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                        // The camera is already closed
                        if (cameraDevice == null) return

                        // When the session is ready, we start displaying the preview.
                        captureSession = cameraCaptureSession
                        try {
                            // Auto focus should be continuous for camera preview.
                            previewRequestBuilder.set(
                                CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                            )

                            // Flash is automatically enabled when necessary.
//                            setAutoFlash(previewRequestBuilder)

                            // Finally, we start displaying the camera preview.
                            previewRequest = previewRequestBuilder.build()
                            captureSession.setRepeatingRequest(
                                previewRequest,
                                null, backgroundHandler
                            )

                            if (isAutoEnable)
                                automate3A()                //20200622 Craig
                            else
                                manual3A(Measurement.None)

                            Log.i(TAG, "CameraCaptureSession.StateCallback")
                        } catch (e: CameraAccessException) {
                            Log.e(TAG, e.toString())
                        }

                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        activity?.showToast("Failed")
                    }
                }, null
            )
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        }
    }

    private fun setAutoFlash(requestBuilder: CaptureRequest.Builder) {
        if (flashSupported) {
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)

            requestBuilder.set(
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_OFF
            )
        }
    }

    /**
     * Helper function used to capture a still image using the [CameraDevice.TEMPLATE_STILL_CAPTURE]
     * template. It performs synchronization between the [CaptureResult] and the [Image] resulting
     * from the single capture, and outputs a [CombinedCaptureResult] object.
     */
    private suspend fun takeRawPhoto(fileName: String? = null):
            CombinedCaptureResult = suspendCoroutine { cont ->

        // Flush any images left in the image reader
        @Suppress("ControlFlowWithEmptyBody")
        var it = mRawImageReader!!.acquireNextImage()
        while (it != null) {
            it.close()
            it = mRawImageReader!!.acquireNextImage()
        }

        // Start a new image queue
        val imageQueue = ArrayBlockingQueue<Image>(IMAGE_BUFFER_SIZE)

        mRawImageReader!!.setOnImageAvailableListener({ reader ->
            val image = reader.acquireNextImage()
            Log.d(TAG, "Image available in queue: ${image.timestamp}")
            imageQueue.add(image)
        }, imageReaderHandler)

        val captureRequest = captureSession.device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
        captureRequest.addTarget(mRawImageReader!!.surface)

//        captureRequest.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
//        captureRequest.set(CaptureRequest.SCALER_CROP_REGION, zoom)

        val isSavedEnable = fileName != null            //20200617 Craig

        captureSession.capture(
            captureRequest.build(),
            object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureStarted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    timestamp: Long,
                    frameNumber: Long
                ) {
                    super.onCaptureStarted(session, request, timestamp, frameNumber)
                    if(isSoundEnable) {
                        cameraHandler.post{
                            mediaActionSound.play(MediaActionSound.SHUTTER_CLICK)
//                            val audioManager = context!!.getSystemService(Context.AUDIO_SERVICE) as AudioManager
//                            val volume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
//                            if (volume != 0) {
//                                val _shootMP = MediaPlayer.create(context, Uri.parse("file:///system/media/audio/ui/camera_click.ogg"))
//                                _shootMP.start()
//                            }
                        }
                        Log.i(TAG, "onCaptureStarted")
                    }
                }

                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    super.onCaptureCompleted(session, request, result)
                    val resultTimestamp = result.get(CaptureResult.SENSOR_TIMESTAMP)
                    Log.d(TAG, "Capture result received: $resultTimestamp")

                    // Set a timeout in case image captured is dropped from the pipeline
                    val exc = TimeoutException("Image dequeuing took too long")
                    val timeoutRunnable = Runnable { cont.resumeWithException(exc) }
                    imageReaderHandler.postDelayed(timeoutRunnable, IMAGE_CAPTURE_TIMEOUT_MILLIS)

                    // Loop in the coroutine's context until an image with matching timestamp comes
                    // We need to launch the coroutine context again because the callback is done in
                    //  the handler provided to the `capture` method, not in our coroutine context
                    @Suppress("BlockingMethodInNonBlockingContext")
                    lifecycleScope.launch(cont.context) {
                        // Dequeue images while timestamps don't match
                        val image = imageQueue.take()

                        // TODO(owahltinez): b/142011420
                        // if (image.timestamp != resultTimestamp) continue
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                            image.format != ImageFormat.DEPTH_JPEG &&
                            image.timestamp != resultTimestamp
                        ) {
                            imageQueue.forEach { image ->
                                image.close()
                            }
                        }
                        else {
                            // Unset the image reader listener
                            imageReaderHandler.removeCallbacks(timeoutRunnable)
                            mRawImageReader!!.setOnImageAvailableListener(null, null)

                            // Clear the queue of images, if there are left
                            imageQueue.forEach { image ->
                                image.close()
                            }

                            cont.resume(
                                CombinedCaptureResult(
                                    image, result, ExifInterface.ORIENTATION_NORMAL, mRawImageReader!!.imageFormat, isSavedEnable, fileName
                                )
                            )
                        }
                    }
                }
            },
            cameraHandler
        )
    }

    private suspend fun takeJPEGPhoto(fileName: String? = null):
            CombinedCaptureResult = suspendCoroutine { cont ->

        // Flush any images left in the image reader
        @Suppress("ControlFlowWithEmptyBody")
        var it = mImageReader.acquireNextImage()
        while (it != null) {
            it.close()
            it = mImageReader.acquireNextImage()
        }

        // Start a new image queue
        val imageQueue = ArrayBlockingQueue<Image>(IMAGE_BUFFER_SIZE)

        mImageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireNextImage()
            Log.d(TAG, "Image available in queue: ${image.timestamp}")
            imageQueue.add(image)
        }, imageReaderHandler)

        val captureRequest = captureSession.device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
        captureRequest.addTarget(mImageReader.surface)

        if (mRawImageReader != null)            //20200702 Craig for continuing capture same photo
            captureRequest.addTarget(mRawImageReader!!.surface)

//        captureRequest.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
        captureRequest.set(CaptureRequest.SCALER_CROP_REGION, zoom)

        val isSavedEnable = fileName != null            //20200617 Craig

        captureSession.capture(
            captureRequest.build(),
            object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureStarted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    timestamp: Long,
                    frameNumber: Long
                ) {
                    super.onCaptureStarted(session, request, timestamp, frameNumber)
                    if(isSoundEnable) {
                        cameraHandler.post{
                            mediaActionSound.play(MediaActionSound.SHUTTER_CLICK)
//                            val audioManager = context!!.getSystemService(Context.AUDIO_SERVICE) as AudioManager
//                            val volume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
//                            if (volume != 0) {
//                                val _shootMP = MediaPlayer.create(context, Uri.parse("file:///system/media/audio/ui/camera_click.ogg"))
//                                _shootMP.start()
//                            }
                        }
                        Log.i(TAG, "onCaptureStarted")
                    }
                }

                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    super.onCaptureCompleted(session, request, result)
                    val resultTimestamp = result.get(CaptureResult.SENSOR_TIMESTAMP)
                    Log.d(TAG, "Capture result received: $resultTimestamp")

                    // Set a timeout in case image captured is dropped from the pipeline
                    val exc = TimeoutException("Image dequeuing took too long")
                    val timeoutRunnable = Runnable { cont.resumeWithException(exc) }
                    imageReaderHandler.postDelayed(timeoutRunnable, IMAGE_CAPTURE_TIMEOUT_MILLIS)

                    // Loop in the coroutine's context until an image with matching timestamp comes
                    // We need to launch the coroutine context again because the callback is done in
                    //  the handler provided to the `capture` method, not in our coroutine context
                    @Suppress("BlockingMethodInNonBlockingContext")
                    lifecycleScope.launch(cont.context) {
                        // Dequeue images while timestamps don't match
                        val image = imageQueue.take()

                        // TODO(owahltinez): b/142011420
                        // if (image.timestamp != resultTimestamp) continue
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                            image.format != ImageFormat.DEPTH_JPEG &&
                            image.timestamp != resultTimestamp
                        ) {
                            imageQueue.forEach { image ->
                                image.close()
                            }
                        } else {
                            // Unset the image reader listener
                            imageReaderHandler.removeCallbacks(timeoutRunnable)
//                            mImageReader.setOnImageAvailableListener(null, null)

                            // Clear the queue of images, if there are left
                            imageQueue.forEach { image ->
                                image.close()
                            }

                            cont.resume(
                                CombinedCaptureResult(
                                    image, result, ExifInterface.ORIENTATION_NORMAL, mImageReader.imageFormat, isSavedEnable, fileName
                                )
                            )
                        }
                    }
                }
            },
            cameraHandler
        )
    }

    private suspend fun procedureTakePicture(result: CombinedCaptureResult): PictureObject = suspendCoroutine { cont ->
        when (result.format) {
            ImageFormat.JPEG, ImageFormat.DEPTH_JPEG -> {
                try {
                    val buffer = result.image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining()).apply { buffer.get(this) }
                    val pictureObject = PictureObject()
                    pictureObject.file = null

                    if (result.isSavedEnable) {
                        val output = createFile(result.fileName,"jpg")
                        val temp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size).rotate(90f)
                        temp.compress(Bitmap.CompressFormat.JPEG, 100, FileOutputStream(output))

                        MediaScannerConnection.scanFile(context, arrayOf(output.path),
                            arrayOf("image/jpeg")) { path, _ ->
                            Log.i(TAG, "onScanCompleted : $path")
                        }

                        temp.recycle()

                        pictureObject.file = output
                    }

                    result.image.close()
                    cont.resume(pictureObject)
                } catch (exc: IOException) {
                    Log.e(TAG, "Unable to write JPEG image to file", exc)
                    cont.resumeWithException(exc)
                }
            }

            // When the format is RAW we use the DngCreator utility library
            ImageFormat.RAW_SENSOR -> {
                val dngCreator = DngCreator(characteristics, result.metadata)
                dngCreator.setOrientation(ExifInterface.ORIENTATION_ROTATE_90)      //rotate picture
                try {
                    val byteBuffer = result.image.planes[0].buffer
                    val byteArray = ByteArray(byteBuffer.remaining())
                    byteBuffer.get(byteArray)

                    val pictureObject = PictureObject()
                    pictureObject.file = null

                    if (result.isSavedEnable){
                        val output = createFile(result.fileName,"dng")

                        FileOutputStream(output).use { dngCreator.writeImage(it, result.image) }

                        MediaScannerConnection.scanFile(
                            context, arrayOf(output.path),
                            arrayOf("image/", "image/x-adobe-dng")
                        ) { path, _ ->
                            Log.i(TAG, "onScanCompleted : $path")
                        }

                        pictureObject.file = output
                    }

                    result.image.close()
                    cont.resume(pictureObject)
                } catch (exc: IOException) {
                    Log.e(TAG, "Unable to write DNG image to file", exc)
                    exc.printStackTrace()
                    cont.resumeWithException(exc)
                }
            }
            // No other formats are supported by this sample
            else -> {
                val exc = RuntimeException("Unknown image format: ${result.image.format}")
                Log.e(TAG, exc.message, exc)
                cont.resumeWithException(exc)
            }
        }
    }

    private suspend fun procedureRefreshRate(result: CombinedCaptureResult): BlackCircleObject = suspendCoroutine { cont ->
        when (result.format) {
            ImageFormat.JPEG, ImageFormat.DEPTH_JPEG -> {
                try {
                    val buffer = result.image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining()).apply { buffer.get(this) }
                    val countOfBlack = calculateRefreshRate(bytes)
                    val circleObject = calculateRefreshRate_CircleObject(bytes)
                    countOfBlack.file = null
                    circleObject.file = null

                    val blackCircleObject = BlackCircleObject(countOfBlack.mat, countOfBlack.blackOfCount, countOfBlack.totalCount,
                    circleObject.circles)

                    if (result.isSavedEnable) {
                        val output = createFile(result.fileName, "jpg")

                        val temp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size).rotate(90f)
                        temp.compress(Bitmap.CompressFormat.JPEG, 100, FileOutputStream(output))

                        MediaScannerConnection.scanFile(context, arrayOf(output.path),
                            arrayOf("image/jpeg")) { path, _ ->
                            Log.i(TAG, "onScanCompleted : $path")
                        }

                        temp.recycle()

                        countOfBlack.file = output
                        blackCircleObject.file = output
                    }

                    result.image.close()

                    cont.resume(blackCircleObject)
                }catch (exc: IOException) {
                    Log.e(TAG, "Unable to write JPEG image to file", exc)
                    cont.resumeWithException(exc)
                }
            }
            // No other formats are supported by this sample
            else -> {
                val exc = RuntimeException("Unknown image format: ${result.image.format}")
                Log.e(TAG, exc.message, exc)
                cont.resumeWithException(exc)
            }
        }
    }

    private suspend fun procedureColorTemperature(result: CombinedCaptureResult): ColorTemperatureObject = suspendCoroutine { cont ->
        when (result.format) {
            ImageFormat.JPEG, ImageFormat.DEPTH_JPEG -> {
                try {
                    val buffer = result.image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining()).apply { buffer.get(this) }
                    val colorTemperatureObject = calculateColorTemp(bytes)
                    colorTemperatureObject.file = null

                    if (result.isSavedEnable) {
                        val output = createFile(result.fileName, "jpg")
                        val temp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size).rotate(90f)
                        temp.compress(Bitmap.CompressFormat.JPEG, 100, FileOutputStream(output))

                        MediaScannerConnection.scanFile(context, arrayOf(output.path),
                            arrayOf("image/jpeg")) { path, _ ->
                            Log.i(TAG, "onScanCompleted : $path")
                        }

                        temp.recycle()

                        colorTemperatureObject.file = output
                    }

                    result.image.close()

                    cont.resume(colorTemperatureObject)
                }catch (exc: IOException) {
                    Log.e(TAG, "Unable to write JPEG image to file", exc)
                    cont.resumeWithException(exc)
                }
            }
            // No other formats are supported by this sample
            else -> {
                val exc = RuntimeException("Unknown image format: ${result.image.format}")
                Log.e(TAG, exc.message, exc)
                cont.resumeWithException(exc)
            }
        }
    }

    private suspend fun procedureContrast(result: CombinedCaptureResult): ContrastObject = suspendCoroutine { cont ->
        when (result.format) {
            // When the format is JPEG or DEPTH JPEG we can simply save the bytes as-is
            ImageFormat.JPEG, ImageFormat.DEPTH_JPEG -> {
                try {
                    val buffer = result.image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining()).apply { buffer.get(this) }

                    val contrastObject = calculateContrast(bytes)
                    contrastObject.file = null

                    if (result.isSavedEnable) {
                        val output = createFile(result.fileName,"jpg")
                        val temp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size).rotate(90f)
                        temp.compress(Bitmap.CompressFormat.JPEG, 100, FileOutputStream(output))

                        MediaScannerConnection.scanFile(context, arrayOf(output.path),
                            arrayOf("image/jpeg")) { path, _ ->
                            Log.i(TAG, "onScanCompleted : $path")
                        }

                        temp.recycle()

                        contrastObject.file = output
                    }

                    result.image.close()

                    cont.resume(contrastObject)
                } catch (exc: IOException) {
                    Log.e(TAG, "Unable to write JPEG image to file", exc)
                    cont.resumeWithException(exc)
                }
            }

            // When the format is RAW we use the DngCreator utility library
            ImageFormat.RAW_SENSOR -> {
                val dngCreator = DngCreator(characteristics, result.metadata)
                dngCreator.setOrientation(ExifInterface.ORIENTATION_ROTATE_90)      //rotate picture
                try {
                    val byteBuffer = result.image.planes[0].buffer
                    val byteArray = ByteArray(byteBuffer.remaining())
                    byteBuffer.get(byteArray)

                    val contrastObject = calculateContrastRAW_CV(result.image.width, result.image.height, byteBuffer)
//                    val contrastObject = calculateContrastRAW(result.image.width, result.image.height, byteArray)

                    contrastObject.file = null

                    if (result.isSavedEnable){
                        val output = createFile(result.fileName,"dng")

                        FileOutputStream(output).use { dngCreator.writeImage(it, result.image) }

                        MediaScannerConnection.scanFile(
                            context, arrayOf(output.path),
                            arrayOf("image/", "image/x-adobe-dng")
                        ) { path, _ ->
                            Log.i(TAG, "onScanCompleted : $path")
                        }

                        contrastObject.file = output
                    }

                    result.image.close()
                    cont.resume(contrastObject)
                } catch (exc: IOException) {
                    Log.e(TAG, "Unable to write DNG image to file", exc)
                    exc.printStackTrace()
                    cont.resumeWithException(exc)
                }
            }

            // No other formats are supported by this sample
            else -> {
                val exc = RuntimeException("Unknown image format: ${result.image.format}")
                Log.e(TAG, exc.message, exc)
                cont.resumeWithException(exc)
            }
        }
    }


    private fun saveExif(file: File, av: String, tv: String, iso: String) {
        val exif = ExifInterface(file.absolutePath)
        exif.setAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS, iso)
        exif.setAttribute(ExifInterface.TAG_EXPOSURE_TIME, tv)
        exif.setAttribute(ExifInterface.TAG_APERTURE_VALUE, av)
        exif.saveAttributes()
    }

    private fun sensorToDeviceRotation(cameraCharacteristics: CameraCharacteristics, deviceOrientation: Int): Int {
        val sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)

        var deviceOrientation = ORIENTATIONS.get(deviceOrientation)

        if(cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT){
            deviceOrientation = -deviceOrientation
        }

        return ((sensorOrientation?.minus(deviceOrientation) ?: 0) + 360) % 360
    }

    private fun average(byteArray: ByteArray): Double {
        var sum: Double = 0.0
        var count: Int = 0
        for (element in byteArray) {
            if (count % 2 == 0) {
                sum += element
            } else {
                sum += (element * 256)
            }
            ++count
        }
        return if (count == 0) Double.NaN else sum / (count / 2)
    }

    private fun calculateRefreshRate(bytes: ByteArray): CountOfBlack {
        val temp  = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val bitmap = temp.rotate(90f)

//        val lineObject = findLines(bitmap)

//        val circleObject = findCircles(bitmap)

        val countOfBlack = findBlackRate(bitmap)

        if (isJPEGSavedEnable)
            countOfBlack.mat.toBitmap().compress(Bitmap.CompressFormat.JPEG, 80, FileOutputStream(createFile(null,"jpg")))

        temp.recycle()
        bitmap.recycle()
        return countOfBlack
    }

    private fun calculateRefreshRate_CircleObject(bytes: ByteArray): CircleObject {
        val temp  = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val bitmap = temp.rotate(90f)

        val circleObject = findCircles(bitmap)

        if (isJPEGSavedEnable)
            circleObject.mat.toBitmap().compress(Bitmap.CompressFormat.JPEG, 80, FileOutputStream(createFile(null,"jpg")))

        temp.recycle()
        bitmap.recycle()
        return circleObject
    }

    private fun calculateColorTemp(bytes: ByteArray): ColorTemperatureObject {
        val temp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val bitmap = temp.rotate(90f)
        var count = 0
        var red: Float = 0f
        var green: Float = 0f
        var blue: Float = 0f

        val width = bitmap.width
        val height = bitmap.height

        val targetSizeWidth = width * getFocusRatio(currentFocusIconFlag) * 0.9
        val targetSizeHeight = height * getFocusRatio(currentFocusIconFlag) * 0.9

        val startX = (width / 2) - (targetSizeWidth / 2)
        val startY = (height / 2) - (targetSizeHeight / 2)

        for (j in startY.toInt() .. (startY + targetSizeHeight).toInt()) {
            for (i in startX.toInt() .. (startX + targetSizeWidth).toInt()) {
                val argb = bitmap.getPixel(i, j)
                val r = Color.red(argb)
                val g = Color.green(argb)
                val b = Color.blue(argb)
                val lum = luminance(r, g, b)
                if (lum < 150) {
                    red += r
                    green += g
                    blue += b
                    count += 1
                }
            }
        }
        red /= count
        green /= count
        blue /= count

        val cxcy = calculateXY(red, green, blue)
        Log.i(TAG, "Color temperature: cx: ${cxcy[0]}, cy${cxcy[1]}")

        val cct = calculateCCT(cxcy[0], cxcy[1])

        Log.i(TAG, "cct: $cct")
        Log.i(TAG, "Red: $red, Green: $green, Blue: $blue")

        val colorTemperatureCCT = if (cct < 5000) ColorTemperature.Warm
        else if (cct >= 5000 && cct < 8000) ColorTemperature.Normal
        else ColorTemperature.Cold

        var colorTemperature: ColorTemperature = ColorTemperature.None
        if (abs(red - green) < (0.05 * 255) && abs(red - blue) < (0.05 * 255)){
            colorTemperature = ColorTemperature.Normal
            Log.i(TAG, "Normal color temperature")
        }
        else if (red > green && red > blue || (red > green && abs(red - blue) < (0.1 * 255))){
            colorTemperature = ColorTemperature.Warm
            Log.i(TAG, "Warm color temperature")
        }
        else if (blue > red && blue > green){
            colorTemperature = ColorTemperature.Cold
            Log.i(TAG, "Cold color temperature")
        }
        else{
            colorTemperature = ColorTemperature.None
            Log.i(TAG, "No color temperature")
        }

//        bitmap.compress(Bitmap.CompressFormat.PNG, 85, FileOutputStream(file))

        temp.recycle()
        bitmap.recycle()
        return if (colorTemperature == ColorTemperature.None) ColorTemperatureObject(cxcy, cct, colorTemperatureCCT)
        else ColorTemperatureObject(cxcy, cct, colorTemperature)
    }

    private fun calculateXY(r: Float, g: Float, b: Float):DoubleArray {
        val X = (412.5 * r) + (357.6 * g) + (180.4 * b)
        val Y = (212.7 * r) + (712.5 * g) + (72.2 * b)      //luminance
        val Z = (19.3 * r) + (119.2 * g) + (950.3 * b)

        val cx = X / (X + Y + Z)
        val cy = Y / (X + Y + Z)
        return doubleArrayOf(cx.roundToDecimalPlaces(2), cy.roundToDecimalPlaces(2))
    }

    fun Double.roundToDecimalPlaces(i: Int) = if (this.isNaN() || this.isInfinite()) 0.0
    else BigDecimal(this).setScale(i, BigDecimal.ROUND_HALF_UP).toDouble()

    private fun calculateCCT(cx: Double, cy: Double):Double {
        val n = (cx - 0.3320) / (0.1858 - cy)
        val cct = (449 * (n * n * n)) + (3525 * (n * n)) + (6823.3 * n) + 5520.33
        return cct
    }

    private fun calculateContrast(bytes: ByteArray): ContrastObject {
        val temp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val bitmap = temp.rotate(90f)
        val min = intArrayOf(Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE)
        val max = intArrayOf(Int.MIN_VALUE, Int.MIN_VALUE, Int.MIN_VALUE)
        var argb = 0
        var r = 0
        var g = 0
        var b = 0
        val width = bitmap.width
        val height = bitmap.height

        var contrast: Double = 0.0

        var lum1 = 0.0
        var count1 = 0

        val targetSizeWidth = getFocusRatio(currentFocusIconFlag) * width * 0.9
        val targetSizeHeight = getFocusRatio(currentFocusIconFlag) * height * 0.9
        val startX = (width / 2) - (targetSizeWidth / 2)
        val startY = (height / 2) - (targetSizeHeight / 2)

        //Lum1
        for (j in startY.toInt() until (startY + targetSizeHeight).toInt()) {     //left white, right black
            for (i in startX.toInt() until (startX + targetSizeWidth).toInt()) {
                argb = bitmap.getPixel(i, j)
                r = Color.red(argb)
                g = Color.green(argb)
                b = Color.blue(argb)

                if (r > max[0]) max[0] = r
                if (g > max[1]) max[1] = g
                if (b > max[2]) max[2] = b

                if (r < min[0]) min[0] = r
                if (g < min[1]) min[1] = g
                if (b < min[2]) min[2] = b

                val lum = r * 0.2126 + g * 0.7152 + b * 0.0722
                lum1 += lum
                count1 ++
            }
        }

        lum1 = if (count1 > 0) lum1/count1
        else 1.0

        var luminance1 = (lum1 * 65535) / 256

        luminance1 = if (luminance1 > darkNoiseValue) luminance1 - darkNoiseValue         //125 is black offset
        else if (luminance1 > 65535.0) 65535.0
        else if (luminance1 < darkNoiseValue) 1.0
        else luminance1

        val contrastLum = luminance1

        contrast = contrastLum

        temp.recycle()
        bitmap.recycle()
        Log.i(TAG, "contrast: $lum1: -> $luminance1, $contrast")

        return ContrastObject(luminance1.roundToDecimalPlaces(2), 0.0.roundToDecimalPlaces(2), contrast.roundToDecimalPlaces(2))
    }

    private fun calculateContrastRAW_CV(width: Int, height: Int, bytes: ByteBuffer): ContrastObject {
        var contrast: Double = 0.0
        val colorFilterArrangement = characteristics.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT)

        val mat = Mat(height, width, org.opencv.core.CvType.CV_16U, bytes)
        val dstMat: Mat = Mat(height, width, CvType.CV_16UC3)
        Imgproc.cvtColor(mat, dstMat, Imgproc.COLOR_BayerGR2BGR)

        val bgr = ArrayList<Mat>()
        Core.split(dstMat, bgr)
        val matB = bgr.get(0)
        val matG = bgr.get(1)
        val matR = bgr.get(2)
        val avgB = Core.mean(matB).`val`[0]
        val avgG = Core.mean(matG).`val`[0]
        val avgR = Core.mean(matR).`val`[0]

        val KB = (avgR + avgG + avgB) / (avgB * 3);
        val KG = (avgR + avgG + avgB) / (avgG * 3);
        val KR = (avgR + avgG + avgB) / (avgR * 3);

        val mergeMatList = ArrayList<Mat>()
        val merge = Mat()

        Core.multiply(matB, Scalar(KB), matB)
        Core.multiply(matG, Scalar(KG), matG)
        Core.multiply(matR, Scalar(KR), matR)
        mergeMatList.add(matB)
        mergeMatList.add(matG)
        mergeMatList.add(matR)
        Core.merge(mergeMatList, merge)

        val pixels: ShortArray = ShortArray((merge.total() * merge.channels()).toInt())
        merge.get(0, 0, pixels)

        val pictureMat = Mat()
        merge.convertTo(pictureMat, org.opencv.core.CvType.CV_8U)

//        pictureMat.convertTo(pictureMat,  -1, 1.0, 20.0)
        pictureMat.toBitmap().compress(Bitmap.CompressFormat.JPEG, 80, FileOutputStream(createFile(null,"jpg")))
        return ContrastObject(0.0.roundToDecimalPlaces(2), 0.0.roundToDecimalPlaces(2), contrast.roundToDecimalPlaces(2))
    }

    private fun calculateContrastRAW(width: Int, height: Int, bytes: ByteArray): ContrastObject {
//        File(createFile("raw").toString()).writeBytes(bytes)
        var contrast: Double = 0.0
        val pixels: IntArray = IntArray(bytes.size / 2)
        for (i in bytes.indices) {
            var value = bytes[i].toInt() and 0xFF
            if (i % 2 == 0)
                pixels[i / 2] += value * 256
            else
                pixels[i / 2] += value
        }

        val luminanceBuffer: IntArray = IntArray(width * height)
        val rawConverter: RawConverter = RawConverter(pixels, width, height)
        val colorFilterArrangement = characteristics.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT)
        for(j in 0 until height) {
            for (i in 0 until width) {
                val p = rawConverter.debay(colorFilterArrangement!!, i, j)
                luminanceBuffer[rawConverter.getPixel(i, j)] = luminance(p[0], p[1], p[2]).toInt()
            }
        }

        var count = 0
        var lum1 = 0.0
        for(i in 0 until width * height / 2) {      //up black, down white -> rotate 90: left white, right black
//            val lum = luminance(redBuffer[i], greenBuffer[i], blueBuffer[i])
            val lum = luminanceBuffer[i]
            if (lum > 256 * 10)  {
                lum1 += lum
                count += 1
            }
        }
        lum1 /= count

        count = 0
        var lum2 = 0.0
        for(i in width * height / 2 until width * height) {
//            val lum = luminance(redBuffer[i], greenBuffer[i], blueBuffer[i])
            val lum = luminanceBuffer[i]
            if (lum < 256 * 10) {
                lum2 += lum
                count += 1
            }
        }
        lum2 /= count

//        contrast = contrast(intArrayOf(redBuffer.max()!!, greenBuffer.max()!!, blueBuffer.max()!!),
//                    intArrayOf(redBuffer.min()!!, greenBuffer.min()!!, blueBuffer.min()!!))

        contrast = if (lum2 > lum1) lum2 / lum1
        else lum1 / lum2

//        Log.i(TAG, "maxR: ${redBuffer.max()}, maxG: ${greenBuffer.max()}, maxB: ${blueBuffer.max()}")
//        Log.i(TAG, "minR: ${redBuffer.min()}, minG: ${greenBuffer.min()}, minB: ${blueBuffer.min()}")
        Log.i(TAG, "minLuminance: ${luminanceBuffer.min()}, maxLuminance: ${luminanceBuffer.max()}")
        Log.i(TAG, "Contrast of RAW: $contrast, lum1: $lum1, lum2: $lum2")
//        return if (min != null && max != null) {
//            if (min > 0)
//                (max / min).toDouble()
//            else
//                max.toDouble()
//        } else
//            null

        return ContrastObject(lum1.roundToDecimalPlaces(2), lum2.roundToDecimalPlaces(2), contrast.roundToDecimalPlaces(2))
    }

    private fun luminance(r: Int, g: Int, b: Int): Double {
        val pixels: IntArray = intArrayOf(r, g, b)
        return pixels[0] * 0.2126 + pixels[1] * 0.7152 + pixels[2] * 0.0722
    }

    private fun contrast(rgb1: IntArray, rgb2: IntArray): Double {
        val lum1 = luminance(rgb1[0], rgb1[1], rgb1[2])
        val lum2 = luminance(rgb2[0], rgb2[1], rgb2[2])
        val brightness = max(lum1, lum2)
        val darkest = min(lum1, lum2)
        return (brightness + 1) / (darkest + 1)
    }

    fun Bitmap.rotate(degree: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degree) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    private fun findCircles(bitmap: Bitmap): CircleObject {
        var image = bitmap.toMat()

        val width = image.width()
        val height = image.height()
        val targetSizeWidth = getFocusRatio(currentFocusIconFlag) * width * 0.9
        val targetSizeHeight = getFocusRatio(currentFocusIconFlag) * height * 0.9
        val startX = (width / 2) - (targetSizeWidth / 2)
        val startY = (height / 2) - (targetSizeHeight / 2)

        val roi = org.opencv.core.Rect(
            startX.roundToInt(),
            startY.roundToInt(),
            targetSizeWidth.roundToInt(),
            targetSizeHeight.roundToInt()
        )     //use 1 / 4 of picture to speed up calculation

        image = Mat(image, roi)
        val gray = Mat()
        val gaussian = Mat()
        val th1 = Mat()
        val th2 = Mat()
        val contour: MutableList<MatOfPoint> = ArrayList<MatOfPoint>()

        Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY)
        Imgproc.medianBlur(gray, gray, 3)
        Imgproc.GaussianBlur(gray, gaussian, org.opencv.core.Size(9.0, 9.0), 2.0, 2.0)
        Imgproc.threshold(gaussian, th1, 100.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
        Imgproc.threshold(th1, th2, 127.0, 255.0, Imgproc.THRESH_BINARY_INV)

        Imgproc.findContours(th2, contour, Mat(), Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE)

        for (cnt in contour) {
            val m = Imgproc.moments(cnt)
            val cX = m.m10 / m.m00
            val cY = m.m01 / m.m00
            Imgproc.circle(image, org.opencv.core.Point(cX, cY), 10, Scalar(254.0, 227.0, 1.0), -1)
        }
        pixelArrangement(contour)

        val circleObject = CircleObject(image, contour.size)
//        image.release()
        gray.release()
        gaussian.release()
        th1.release()
        th2.release()
        return circleObject
    }

    private fun pixelArrangement(contour: MutableList<MatOfPoint>) {
        val pointsList = ArrayList<org.opencv.core.Point>()
        val squareSize = sqrt(contour.size.toDouble())      //because picture is crop as rect
        for (cnt in contour) {
            val m  = Imgproc.moments(cnt)
            val point = org.opencv.core.Point(m.m10 / m.m00, m.m01 / m.m00)
            pointsList.add(point)
        }

        var left = Double.MAX_VALUE
        var right = Double.MIN_VALUE
        var up = Double.MAX_VALUE
        var down = Double.MIN_VALUE

        pointsList.forEach {
            if (it.x > right) right = it.x
            if (it.x < left) left = it.x
            if (it.y > down) down = it.y
            if (it.y < up) up = it.y
        }

        val averageX = abs(right - left) / squareSize
        val averageY = abs(down - up) / squareSize

        pointsList.sortWith(compareBy({it.x}, {it.y}))

//        val diffBuffer = ArrayList<Double>()
        var tempX = pointsList[0].x
        var segmentX = 0
        pointsList.forEach {
            val diff = abs(tempX - it.x)
            if (diff >= averageX * 0.5)
                segmentX += 1

//            diffBuffer.add(diff)
            tempX = it.x
        }

        Log.i(TAG, "123, $averageX, $averageY, $segmentX")
    }

    private fun findLines(bitmap: Bitmap): LineObject {
        var image = bitmap.toMat()

        val width = image.width()
        val height = image.height()
        val targetSizeWidth = getFocusRatio(currentFocusIconFlag) * width * 0.9
        val targetSizeHeight = getFocusRatio(currentFocusIconFlag) * height * 0.9
        val startX = (width / 2) - (targetSizeWidth / 2)
        val startY = (height / 2) - (targetSizeHeight / 2)

        val roi = org.opencv.core.Rect(
            startX.roundToInt(),
            startY.roundToInt(),
            targetSizeWidth.roundToInt(),
            targetSizeHeight.roundToInt()
        )     //use 1 / 4 of picture to speed up calculation

        image = Mat(image, roi)

        val edges = Mat()
        val lines = Mat()

        Imgproc.cvtColor(image, edges, Imgproc.COLOR_BGR2GRAY)
        Imgproc.blur(edges, edges, org.opencv.core.Size(3.0, 3.0))
        Imgproc.threshold(edges, edges, 127.0, 255.0, Imgproc.THRESH_BINARY_INV)
        Imgproc.erode(edges, edges, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, org.opencv.core.Size(2.0, 2.0)))
        Imgproc.dilate(edges, edges, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, org.opencv.core.Size(2.0, 2.0)))
        Imgproc.Canny(edges, edges, 100.0, 200.0, 3)

        val threshold = 80
        val minLineSize = 50.0
        val linGap = 50.0

        Imgproc.HoughLinesP(edges, lines, 1.0, Math.PI / 180, threshold, minLineSize, linGap)

        if(lines.rows() > 0) {
            Log.i(TAG, "line count: ${lines.rows()}")
            for (y in 0 until lines.rows()) {
                val vec = lines.get(y, 0)
                val x1 = vec[0]
                val y1 = vec[1]
                val x2 = vec[2]
                val y2 = vec[3]
                val start = org.opencv.core.Point(x1, y1)
                val end = org.opencv.core.Point(x2, y2)
                Imgproc.line(edges, start, end, Scalar(255.0, 0.0, 0.0), 3)
            }
        }

        val lineObject = LineObject(edges, lines.rows())
        image.release()
//        edges.release()
        lines.release()
        return lineObject
    }

    private fun findBlackRate(bitmap: Bitmap): CountOfBlack {
        var image = bitmap.toMat()

        val width = image.width()
        val height = image.height()
        val targetSizeWidth = getFocusRatio(currentFocusIconFlag) * width * 0.9
        val targetSizeHeight = getFocusRatio(currentFocusIconFlag) * height * 0.9
        val startX = (width / 2) - (targetSizeWidth / 2)
        val startY = (height / 2) - (targetSizeHeight / 2)

        val roi = org.opencv.core.Rect(
            startX.roundToInt(),
            startY.roundToInt(),
            targetSizeWidth.roundToInt(),
            targetSizeHeight.roundToInt()
        )     //use 1 / 4 of picture to speed up calculation

        image = Mat(image, roi)

        val gray = Mat()
        val gaussian = Mat()
        val th1 = Mat()
        Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY)
        Imgproc.GaussianBlur(gray, gaussian, org.opencv.core.Size(15.0, 15.0), 0.0)
        Imgproc.threshold(gaussian, th1, 10.0, 255.0, Imgproc.THRESH_BINARY)
        val nonZeroCount = org.opencv.core.Core.countNonZero(th1)
        val totalPixels = width * height
        val blackOfCount = totalPixels - nonZeroCount

        val countOfBlack = CountOfBlack(th1, blackOfCount.toDouble(), totalPixels.toDouble())

        image.release()
        gray.release()
        gaussian.release()
//        th1.release()
        return countOfBlack
    }

    private fun createFile(fileName: String? = null, extension: String): File {
        val sdf = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.TAIWAN)
        val sdf1 = SimpleDateFormat("yyyy_MM_dd_HH_mm", Locale.TAIWAN)

        val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)

//        path = context?.getExternalFilesDir(Environment.DIRECTORY_DCIM)     //application directory

        val dir = File(path, "/CraigCam2")
        if(!dir.isDirectory){
            dir.mkdirs()
        }

        if (fileName == null) {
            return File(dir, "/IMG_${sdf.format(Date())}.$extension")
        } else {
            var file = File(dir, "/$$fileName.$extension")
            var count = 0

            while (file.exists()){
                count += 1
                file = File(dir, "/$fileName($count).$extension")
            }
            return file
        }
    }

    private fun showToast(textSize: Float, text: String, backgroundColor: Int) {
        val toastView: ToastView = ToastView(context!!)
        toastView.setRadius(textSize)
        toastView.setTextSize(textSize)
        toastView.setBackgroundColor(backgroundColor)

        val toast = com.example.toast.Toast(context!!, toastView)
        toast.setPosition(com.example.toast.Toast.Position.CENTER)
        toast.toastView = toastView
        toast.showToast(text)
    }

    private fun showToast(text: String) {
        showToast(50.0f, text, Color.argb(0xAA, 0x00, 0xAA, 0x00))
    }

    private fun pickPictureFromGallery() {
//        val pickImageIntent = Intent(
//            Intent.ACTION_PICK,
//            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
//        )
//
//        startActivityForResult(pickImageIntent, 1)

        var PICK_IMAGE_MULTIPLE = 1
        if (Build.VERSION.SDK_INT < 19) {
            var intent = Intent()
            intent.type = "image/*"
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            intent.action = Intent.ACTION_GET_CONTENT
            startActivityForResult(
                Intent.createChooser(intent, "Select Picture")
                , PICK_IMAGE_MULTIPLE
            )
        } else {
            var intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "image/*"
            startActivityForResult(intent, PICK_IMAGE_MULTIPLE)
        }
    }

    private fun saveData(){
        settings = context!!.getSharedPreferences(PREF_NAME, PRIVATE_MODE)
        settings.edit()
            .putInt(SENSOR_SENSITIVITY, sensorSensitivity)
            .putLong(EXPOSURE_TIME, exposureTime)
            .putFloat(APERTURE, aperture)
            .putBoolean(MANUAL_ENABLE, isManualEnable)
//            .putBoolean(AUTO_ENABLE, isAutoEnable)
            .putBoolean(COLOR_TEMPERATURE_ENABLE, isColorTemperatureEnable)
            .putBoolean(REFRESH_RATE_ENABLE, isRefreshRateEnable)
            .putBoolean(CONTRAST_ENABLE, isContrastEnable)
            .putInt(APERTURE_PROGRESS, apertureProgress)
            .putInt(EXPOSURE_PROGRESS, exposureProgress)
            .putInt(SENSOR_SENSITIVITY_PROGRESS, sensorSensitivityProgress)
            .putInt(FOCUS_ZOOM_SCALE, focusZoomScale)
            .putInt(FOCUS_AREA_LAYOUT_WIDTH, defaultFocusAreaWidth)
            .putInt(FOCUS_AREA_LAYOUT_HEIGHT, defaultFocusAreaHeight)
            .apply()
    }

    private fun readData(){
        settings = context!!.getSharedPreferences(PREF_NAME, PRIVATE_MODE)
        sensorSensitivity = settings.getInt(SENSOR_SENSITIVITY, 0)
        exposureTime = settings.getLong(EXPOSURE_TIME, 0)
        aperture = settings.getFloat(APERTURE, 0f)
        isManualEnable = settings.getBoolean(MANUAL_ENABLE, false)
//        isAutoEnable = settings.getBoolean(AUTO_ENABLE, false)
//        isColorTemperatureEnable = settings.getBoolean(COLOR_TEMPERATURE_ENABLE, false)
//        isRefreshRateEnable = settings.getBoolean(REFRESH_RATE_ENABLE, false)
//        isContrastEnable = settings.getBoolean(CONTRAST_ENABLE, false)
        apertureProgress = settings.getInt(APERTURE_PROGRESS, 0)
        exposureProgress = settings.getInt(EXPOSURE_PROGRESS, 0)
        sensorSensitivityProgress = settings.getInt(SENSOR_SENSITIVITY_PROGRESS, 0)
        focusZoomScale = settings.getInt(FOCUS_ZOOM_SCALE, 1)
        defaultFocusAreaWidth = settings.getInt(FOCUS_AREA_LAYOUT_WIDTH, 1440)
        defaultFocusAreaHeight = settings.getInt(FOCUS_AREA_LAYOUT_HEIGHT, 1896)
    }

    fun readSettingData(){
        settings = context!!.getSharedPreferences(
            SettingFragment.PREF_NAME,
            SettingFragment.PRIVATE_MODE
        )
        isGridEnable = settings.getBoolean(SettingFragment.GRID, false)
        isSoundEnable = settings.getBoolean(SettingFragment.SOUND, false)
        isCloudSyncEnable = settings.getBoolean(SettingFragment.CLOUD_SYNC, false)
        whitePeakValue = settings.getInt(SettingFragment.WHITE_PEAK, 255)
        blackNadirValue = settings.getInt(SettingFragment.BLACK_NADIR, 0)
        darkNoiseValue = settings.getInt(SettingFragment.DARK_NOISE, 70)
        repeatTimesValue = settings.getInt(SettingFragment.REPEAT_TIMES, 0)

        gridLineView = view!!.findViewById(R.id.grid_line)

        if(isGridEnable) {
            gridLineView.visibility = View.VISIBLE
        } else {
            gridLineView.visibility = View.INVISIBLE
        }
    }

    private fun sendToDevice(bytes: ByteArray): BluetoothSocket? {
        try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(m_address)
//                    m_bluetoothSocket = device.createInsecureRfcommSocketToServiceRecord(m_myUUID)
            val bluetoothSocket = device.createRfcommSocketToServiceRecord(m_myUUID)
            BluetoothAdapter.getDefaultAdapter().cancelDiscovery()
            bluetoothSocket!!.connect()

            val outputStream = bluetoothSocket.outputStream
            outputStream.write(bytes)
            return bluetoothSocket
        }
        catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun sendCommand(bytes: ByteArray){
//        if (m_bluetoothSocket != null){
        if (m_isConnected) {
            try {
                m_bluetoothSocket!!.outputStream.write(bytes)
                m_bluetoothSocket!!.outputStream.close()
                Log.i(TAG, "Bluetooth is sent data")
            }catch (e: IOException) {
                disconnect()
                e.printStackTrace()
            }
        } else {
            if (m_address.contains(":"))
                ConnectToDevice(context!!).execute()
        }
    }

    private val readCommandRunnable = Runnable {
       readCommand(m_bluetoothSocket)
    }

    private fun readCommand(bluetoothSocket: BluetoothSocket?){
        if (bluetoothSocket != null) {
            try {
                val bytes: ByteArray = ByteArray(20)
                var receivedCount = 0
                var receivedData: String = ""
                var done = false
                bluetoothSocket.inputStream.read(bytes)

                bytes.forEach {
                    if (receivedCount < 11 && !done) {
                        receivedData += if(it < 0x30) (it + 0x30).toChar()
                            else it.toChar()
                        receivedCount += 1
                    }
                    else
                        done = true
                }

                if (receivedData.contains("MBITSP2020"))
                    m_isAckReceived = true
                Log.i(TAG, "Bluetooth received: $receivedData")
            } catch (e: IOException) {
                e.printStackTrace()
                readCommandHandler.removeCallbacksAndMessages(null)
            }
        }
//        readCommandHandler.postDelayed(readCommandRunnable, 500)
        Log.i(TAG, "readCommand")
    }

    private fun disconnect(){
        if (m_bluetoothSocket != null) {
            try {
                m_bluetoothSocket!!.close()
                m_bluetoothSocket = null
                m_isConnected = false
                Log.i(TAG, "Bluetooth is disconnected")
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private class ConnectToDevice (c: Context): AsyncTask<Void, Void, String>() {
        private var connectSuccess: Boolean = true
        private val context: Context = c

        override fun doInBackground(vararg params: Void?): String? {
            try {
                if (m_bluetoothSocket == null || !m_isConnected) {
                    m_bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                    val device: BluetoothDevice = m_bluetoothAdapter.getRemoteDevice(m_address)
//                    m_bluetoothSocket = device.createInsecureRfcommSocketToServiceRecord(m_myUUID)
                    m_bluetoothSocket = device.createRfcommSocketToServiceRecord(m_myUUID)
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery()
                    m_bluetoothSocket!!.connect()
                    Log.i(TAG, "Bluetooth is connected")
                }
            } catch (e: IOException) {
                connectSuccess = false
                e.printStackTrace()
            }
            return null
        }

        override fun onPostExecute(result: String?) {
            super.onPostExecute(result)
            if(!connectSuccess) {
                Log.i(TAG, "couldn't connect")
            } else {
                m_isConnected = true
            }
        }
    }

    private fun setSensorSensitivity(iso: Int) {
        previewRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, iso)
        captureSession.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler)
//        isoCustomSeekBar.text = "ISO $iso"
        txt_3AValue.text = "ISO $iso"
    }

    private fun setExposureTime(ae: Long) {
        previewRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, ae)      //shutter speed = 1 / (10^9 / ae) sec.
        captureSession.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler)
//        tvCustomSeekBar.text = "1/${"%.1f".format(10.toDouble().pow(9.toDouble()) / ae)}s"
        txt_3AValue.text = "1/${"%.1f".format(10.toDouble().pow(9.toDouble()) / ae)}s"
    }

    private fun setApertureSize(aperture: Float) {
        previewRequestBuilder.set(CaptureRequest.LENS_APERTURE, aperture)
        captureSession.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler)
//        avCustomSeekBar.text = "F$aperture"
    }

    private fun initialize3A() {
        if (sensorSensitivity != 0) previewRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, sensorSensitivity)     //20200331 Craig return last state
        if (exposureTime.toInt() != 0)  previewRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureTime)
        if (aperture.toInt() != 0)  previewRequestBuilder.set(CaptureRequest.LENS_APERTURE, aperture)
        if (zoom != null)   previewRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoom)
    }

    private fun automate3A() {
        if (flashSupported)
            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
        else
            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)

        previewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)

        val apertures = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
        when {
            apertures != null -> {
                val av = apertures[apertures.size - 1]
                setApertureSize(av)
            }
        }

        captureSession.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler)
    }

    private fun manual3A(measurementItem: Measurement) {
        previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
        captureSession.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler)

        setExposureTime(exposureTime)
        setSensorSensitivity(sensorSensitivity)

        val apertures = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
        when {
            apertures != null -> {
                val av = apertures[apertures.size - 1]
                setApertureSize(av)
            }
        }
//        when(measurementItem) {
//            Measurement.Contrast -> {           //iso 50, tv 350
//                val ae = (10.0.pow(9) / 350).roundToLong()
//                val iso = 50
//                setExposureTime(ae)
//                setSensorSensitivity(iso)
//            }
//            Measurement.ColorTemperature -> {
//                val iso = 50
//                val ae = (10.0.pow(9) / 350).roundToLong()
//                setExposureTime(ae)
//                setSensorSensitivity(iso)
//            }
//            Measurement.RefreshRate -> {
//            }
//            Measurement.None -> {
//                val iso = 50
//                val ae = (10.0.pow(9) / 350).roundToLong()
//                setExposureTime(ae)
//                setSensorSensitivity(iso)
//            }
//        }
    }

    fun backToSendCommand() {
        val commandWhite = MBITSP2020().produceCommand(MBITSP2020.Mode.MODE2, 1920, 1080, 0, 0, 0, 0,
            Color.argb(0, whitePeakValue, whitePeakValue, whitePeakValue),
            Color.argb(0, 0, 0, 0),
            Color.argb(0, 0, 0, 0),
            Color.argb(0, 0, 0, 0))

        lifecycleScope.launch(Dispatchers.IO) {
            m_bluetoothSocket = sendToDevice(commandWhite)
            readCommandHandler.post(readCommandRunnable)
        }

        when(currentMeasurement) {
            Measurement.Contrast -> {
                lifecycleScope.launch(Dispatchers.IO) {
                    m_bluetoothSocket = sendToDevice(commandWhite)
                    if (m_bluetoothSocket == null)
                        m_bluetoothSocket = sendToDevice(commandWhite)
                    readCommandHandler.post(readCommandRunnable)
                }
            }
            Measurement.ColorTemperature -> {
                lifecycleScope.launch(Dispatchers.IO) {
                    m_bluetoothSocket = sendToDevice(commandWhite)
                    readCommandHandler.post(readCommandRunnable)
                }
            }
            Measurement.RefreshRate -> {

            }
            Measurement.None -> {

            }
        }
    }

    inner class LightSensorListener: SensorEventListener {
        private var lux = 0f
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            Log.i(TAG, "Sensor: ${sensor!!.name}, accuracy: $accuracy")
        }

        override fun onSensorChanged(event: SensorEvent?) {
            if (event!!.sensor.type == Sensor.TYPE_LIGHT) {
//                Log.i(TAG, "Lux: ${event.values[0]}")
                lux = event.values[0]
            }
        }

        fun getLux(): Float {
            return lux
        }
    }

    private fun getFocusRatio(focusIconSize: FocusIconSize): Double {
        return when (focusIconSize) {
            FocusIconSize.ZOOM1_8 -> 1 / 8.0
            FocusIconSize.ZOOM2_8 -> 2 / 8.0
            FocusIconSize.ZOOM3_8 -> 3 / 8.0
            FocusIconSize.ZOOM4_8 -> 4 / 8.0
            FocusIconSize.ZOOM5_8 -> 5 / 8.0
            FocusIconSize.ZOOM6_8 -> 6 / 8.0
            FocusIconSize.ZOOM7_8 -> 7 / 8.0
            FocusIconSize.ZOOM8_8 -> 8 / 8.0
            else -> 8 / 8.0
        }
    }

    private fun setZoomArea(focusZoomScale: Int) {
        readData()
        focusAreaLayout.layoutParams.width = defaultFocusAreaWidth
        focusAreaLayout.layoutParams.height = defaultFocusAreaHeight

        Log.i(TAG, "focusAreaLayout: ${focusAreaLayout.width} x ${focusAreaLayout.height}")

        val layoutParams = focusAreaLayout.layoutParams
        var ratio = 8.0
        when (focusZoomScale) {
            0 -> {
                ratio = 1 / 8.0
                currentFocusIconFlag = FocusIconSize.ZOOM1_8
                this.focusZoomScale = 0
            }
            1 -> {
                ratio = 2 / 8.0
                currentFocusIconFlag = FocusIconSize.ZOOM2_8
                this.focusZoomScale = 1
            }
            2 -> {
                ratio = 3 / 8.0
                currentFocusIconFlag = FocusIconSize.ZOOM3_8
                this.focusZoomScale = 2
            }
            3 -> {
                ratio = 4 / 8.0
                currentFocusIconFlag = FocusIconSize.ZOOM4_8
                this.focusZoomScale = 3
            }
            4 -> {
                ratio = 5 / 8.0
                currentFocusIconFlag = FocusIconSize.ZOOM5_8
                this.focusZoomScale = 4
            }
            5 -> {
                ratio = 6 / 8.0
                currentFocusIconFlag = FocusIconSize.ZOOM6_8
                this.focusZoomScale = 5
            }
            6 -> {
                ratio = 7 / 8.0
                currentFocusIconFlag = FocusIconSize.ZOOM7_8
                this.focusZoomScale = 6
            }
            7 -> {
                ratio = 8 / 8.0
                currentFocusIconFlag = FocusIconSize.ZOOM8_8
                this.focusZoomScale = 7
            }
        }
        currentFocusIconSizeWidth = layoutParams.width * ratio
        currentFocusIconSizeHeight = layoutParams.height * ratio

        layoutParams.width = currentFocusIconSizeWidth.toInt()
        layoutParams.height = currentFocusIconSizeHeight.toInt()
        focusAreaLayout.layoutParams = layoutParams
    }

    companion object {
        /**
         * Conversion from screen rotation to JPEG orientation.
         */
        private val ORIENTATIONS = SparseIntArray()
        init {
            ORIENTATIONS.append(Surface.ROTATION_0, 0)
            ORIENTATIONS.append(Surface.ROTATION_90, 90)
            ORIENTATIONS.append(Surface.ROTATION_180, 180)
            ORIENTATIONS.append(Surface.ROTATION_270, 270)
        }

        private const val FRAGMENT_DIALOG = "dialog"

        /**
         * Tag for the [Log].
         */
        private const val TAG = "Camera2BasicFragment"

        /**
         * Camera state: Showing camera preview.
         */
        private const val STATE_PREVIEW = 0

        /**
         * Max preview width that is guaranteed by Camera2 API
         */
        private const val MAX_PREVIEW_WIDTH = 1920

        /**
         * Max preview height that is guaranteed by Camera2 API
         */
        private const val MAX_PREVIEW_HEIGHT = 1080

        private const val IMAGE_BUFFER_SIZE = 10

        /** Maximum time allowed to wait for the result of an image capture */
        private const val IMAGE_CAPTURE_TIMEOUT_MILLIS: Long = 2000

        private const val PRIVATE_MODE = 0
        private const val PREF_NAME = "Camera2Fragment"
        private const val APERTURE = "APERTURE"
        private const val EXPOSURE_TIME = "EXPOSURE_TIME"
        private const val SENSOR_SENSITIVITY = "SENSOR_SENSITIVITY"
        private const val MANUAL_ENABLE = "MANUAL_ENABLE"
//        private const val AUTO_ENABLE = "AUTO_ENABLE"
        private const val FOCUS_ZOOM_SCALE = "FOCUS_ZOOM_SCALE"
        private const val COLOR_TEMPERATURE_ENABLE = "COLOR_TEMPERATURE_ENABLE"
        private const val REFRESH_RATE_ENABLE = "REFRESH_RATE_ENABLE"
        private const val CONTRAST_ENABLE = "CONTRAST_ENABLE"
        private const val APERTURE_PROGRESS = "APERTURE_PROGRESS"
        private const val EXPOSURE_PROGRESS = "EXPOSURE_PROGRESS"
        private const val SENSOR_SENSITIVITY_PROGRESS = "SENSOR_SENSITIVITY_PROGRESS"
        private const val FOCUS_AREA_LAYOUT_WIDTH = "FOCUS_AREA_LAYOUT_WIDTH"
        private const val FOCUS_AREA_LAYOUT_HEIGHT = "FOCUS_AREA_LAYOUT_HEIGHT"

        private const val RAW_FORMAT = ImageFormat.RAW_SENSOR
        private const val JPEG_FORMAT = ImageFormat.JPEG

        private const val COMMAND_RETRY = 3

        private const val ALBUM_NAME = "CraigCam2"

        var m_myUUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        var m_bluetoothSocket: BluetoothSocket? = null
        lateinit var m_bluetoothAdapter: BluetoothAdapter
        var m_isConnected: Boolean = false
        lateinit var m_address: String
        var m_isAckReceived: Boolean = false

        enum class ColorTemperature {
            Warm,
            Normal,
            Cold,
            None
        }

        enum class FocusIconSize {
            ULTRA,
            BIG,
            SMALL,
            ZOOM1_8,
            ZOOM2_8,
            ZOOM3_8,
            ZOOM4_8,
            ZOOM5_8,
            ZOOM6_8,
            ZOOM7_8,
            ZOOM8_8
        }

        enum class Measurement {
            RefreshRate,
            ColorTemperature,
            Contrast,
            None
        }

        private val MinimumTestPattern = MBITSP2020().let{
            it.setMode(MBITSP2020.Mode.MODE2)
            it.setDisplayWidth(0)
            it.setDisplayHeight(0)
            it.setDisplayStartX(0)
            it.setDisplayStartY(0)
            it.setModuleWidth(0 )
            it.setModuleHeight(0)
            it.setGrayScale(MBITSP2020.GrayScaleSets.GRAY_SCALE_1, 255.toByte(), 255.toByte(), 255.toByte())
            it.setGrayScale(MBITSP2020.GrayScaleSets.GRAY_SCALE_2, 0.toByte(), 0.toByte(), 0.toByte())
            it.setGrayScale(MBITSP2020.GrayScaleSets.GRAY_SCALE_3, 0.toByte(), 0.toByte(), 0.toByte())
            it.setGrayScale(MBITSP2020.GrayScaleSets.GRAY_SCALE_4, 0.toByte(), 0.toByte(), 0.toByte())
            it.composeCommand()
        }
        /**
         * Given `choices` of `Size`s supported by a camera, choose the smallest one that
         * is at least as large as the respective texture view size, and that is at most as large as
         * the respective max size, and whose aspect ratio matches with the specified value. If such
         * size doesn't exist, choose the largest one that is at most as large as the respective max
         * size, and whose aspect ratio matches with the specified value.
         *
         * @param choices           The list of sizes that the camera supports for the intended
         *                          output class
         * @param textureViewWidth  The width of the texture view relative to sensor coordinate
         * @param textureViewHeight The height of the texture view relative to sensor coordinate
         * @param maxWidth          The maximum width that can be chosen
         * @param maxHeight         The maximum height that can be chosen
         * @param aspectRatio       The aspect ratio
         * @return The optimal `Size`, or an arbitrary one if none were big enough
         */
        @JvmStatic
        private fun chooseOptimalSize(
            choices: Array<Size>,
            textureViewWidth: Int,
            textureViewHeight: Int,
            maxWidth: Int,
            maxHeight: Int,
            aspectRatio: Size
        ): Size {

            // Collect the supported resolutions that are at least as big as the preview Surface
            val bigEnough = ArrayList<Size>()
            // Collect the supported resolutions that are smaller than the preview Surface
            val notBigEnough = ArrayList<Size>()
            val w = aspectRatio.width
            val h = aspectRatio.height
            for (option in choices) {
                if (option.width <= maxWidth && option.height <= maxHeight &&
                    option.height == option.width * h / w
                ) {
                    if (option.width >= textureViewWidth && option.height >= textureViewHeight) {
                        bigEnough.add(option)
                    } else {
                        notBigEnough.add(option)
                    }
                }
            }

            // Pick the smallest of those big enough. If there is no one big enough, pick the
            // largest of those not big enough.
            if (bigEnough.size > 0) {
                return Collections.min(bigEnough, CompareSizesByArea())
            } else if (notBigEnough.size > 0) {
                return Collections.max(notBigEnough, CompareSizesByArea())
            } else {
                Log.e(TAG, "Couldn't find any suitable preview size")
                return choices[0]
            }
        }

        /** Helper data class used to hold capture metadata with their associated image */
        data class CombinedCaptureResult(
            val image: Image,
            val metadata: CaptureResult,
            val orientation: Int,
            val format: Int,
            val isSavedEnable: Boolean = true,
            val fileName: String? = null
        ) : Closeable {
            override fun close() = image.close()
        }

        data class LineObject(val mat: Mat, val lines: Int)

        data class CircleObject(val mat: Mat, val circles: Int, var file: File? = null)

        data class CountOfBlack(val mat: Mat, val blackOfCount: Double, val totalCount: Double, var file: File? = null)

        data class BlackCircleObject(var mat: Mat, var blackOfCount: Double, var totalCount: Double, var circles: Int, var file: File? = null)

        data class ContrastObject(var lum1: Double, var lum2: Double, var contrast: Double, var file: File? = null)

        data class ColorTemperatureObject(var cxcy: DoubleArray, var cct: Double, var colorTemperature: ColorTemperature, var file: File? = null)

        data class PictureObject(var file: File? = null)
        private const val PERMISSIONS_REQUEST_CODE = 10
        private val PERMISSIONS_REQUIRED = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }
}