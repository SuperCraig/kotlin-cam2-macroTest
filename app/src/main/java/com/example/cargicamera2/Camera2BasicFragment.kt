package com.example.cargicamera2

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.content.res.Configuration
import android.graphics.*
import android.hardware.camera2.*
import android.media.*
import android.os.*
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.*
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.cargicamera2.extensions.MBITSP2020
import com.example.cargicamera2.extensions.OrientationLiveData
import com.example.cargicamera2.fragments.PermissionsFragment
import com.example.cargicamera2.room.History
import com.example.cargicamera2.room.HistoryViewModel
import com.example.cargicamera2.services.showToast
import com.example.cargicamera2.ui.AutoFitTextureView
import com.example.cargicamera2.ui.ErrorDialog
import com.example.cargicamera2.ui.FocusView
import com.example.cargicamera2.ui.GridLineView
import com.example.extensions.adaptiveThreshold
import com.example.extensions.canny
import com.example.extensions.toBitmap
import com.example.extensions.toMat
import com.example.imagegallery.model.ImageGalleryUiModel
import com.example.imagegallery.service.MediaHelper
import com.example.lib.CustomSeekBar
import kotlinx.android.synthetic.main.fragment_camera2_basic.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.Scalar
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

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

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture) = true

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
                    captureSession.setRepeatingRequest(previewRequestBuilder.build(), captureCallback, backgroundHandler)
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

    /** Live data listener for changes in the device orientation relative to the camera */
    private lateinit var relativeOrientation: OrientationLiveData

    private lateinit var settings: SharedPreferences
    private var aperture: Float = 0f
    private var exposureTime: Long = 0
    private var sensorSensitivity: Int = 0

    private var isAutoEnable: Boolean = false
    private var isManualEnable: Boolean = false
    private var isContrastEnable: Boolean = false
    private var isColorTemperatureEnable: Boolean = false
    private var isRefreshRateEnable: Boolean = false

    private var isGridEnable: Boolean = false
    private var isSoundEnable: Boolean = false
    private var isCloudSyncEnable: Boolean = false

    private var progressbarShutter: ProgressBar ?= null

    private var fingerSpacing: Float = 0f
    private var zoomLevel: Float = 0f
    private var zoom: Rect? = null

    private lateinit var gridLineView: GridLineView

    private val mediaActionSound: MediaActionSound = MediaActionSound()

    private var shutterTimes = 0
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


    /**
     * This a callback object for the [ImageReader]. "onImageAvailable" will be called when a
     * still image is ready to be saved.
     */
    private val onImageAvailableListener = ImageReader.OnImageAvailableListener {
        backgroundHandler?.post(ImageSaver(it.acquireNextImage(), createFile("jpg")))
    }

    /**
     * A [CameraCaptureSession.CaptureCallback] that handles events related to JPEG capture.
     */
    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {

        private fun process(result: CaptureResult) {
            captureResult = result
            when (state) {
                STATE_PREVIEW -> Unit // Do nothing when the camera preview is working normally.
                STATE_WAITING_LOCK -> capturePicture(result)
                STATE_WAITING_PRECAPTURE -> {
                    // CONTROL_AE_STATE can be null on some devices
                    val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                    if (aeState == null ||
                        aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                        aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED
                    ) {
                        state = STATE_WAITING_NON_PRECAPTURE
                    }
                }
                STATE_WAITING_NON_PRECAPTURE -> {
                    // CONTROL_AE_STATE can be null on some devices
                    val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        state = STATE_PICTURE_TAKEN
                        captureStillPicture()
                    }
                }
            }
        }

        private fun capturePicture(result: CaptureResult) {
            val afState = result.get(CaptureResult.CONTROL_AF_STATE)
            if (afState == null) {
                captureStillPicture()
            } else if (afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED
                || afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED
            ) {
                // CONTROL_AE_STATE can be null on some devices
                val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                    state = STATE_PICTURE_TAKEN
                    captureStillPicture()
                } else {
                    runPrecaptureSequence()
                }
            }
        }

        override fun onCaptureProgressed(
            session: CameraCaptureSession,
            request: CaptureRequest,
            partialResult: CaptureResult
        ) {
            process(partialResult)
        }

        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            process(result)
        }

        override fun onCaptureSequenceAborted(session: CameraCaptureSession, sequenceId: Int) {
            super.onCaptureSequenceAborted(session, sequenceId)
            Log.i(TAG, "onCaptureSequenceAborted")
        }

    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        var view: View = inflater.inflate(R.layout.fragment_camera2_basic, container, false)
        m_address = arguments?.getString(SplashScreenActivity.EXTRA_ADDRESS).toString()
        Log.i(TAG,"onCreateView")
        Log.i(TAG, "Paired address: $m_address")
        return view
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<View>(R.id.btnPicture).setOnClickListener(this)
        view.findViewById<View>(R.id.btnManual).setOnClickListener(this)
        view.findViewById<View>(R.id.btnAuto).setOnClickListener(this)
        view.findViewById<View>(R.id.btnContrast).setOnClickListener(this)
        view.findViewById<View>(R.id.btnRefreshRate).setOnClickListener(this)
        view.findViewById<View>(R.id.btnColorTemperature).setOnClickListener(this)
        view.findViewById<View>(R.id.btnPhotoBox).setOnClickListener(this)
        view.findViewById<View>(R.id.btnRecordBar).setOnClickListener(this)
        view.findViewById<View>(R.id.btnSetting).setOnClickListener(this)

        readData()          //read shared preferneces data & apply
        readSettingData()

        val isoCustomSeekBar: CustomSeekBar = view.findViewById(R.id.isoCustomSeekBar)
        isoCustomSeekBar.text = "ISO $sensorSensitivity"
        isoCustomSeekBar.setOnTouchListener { _, _ ->
            val progress = isoCustomSeekBar.progress
            val range = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
            val max1 = range!!.upper //10000
            val min1 = range.lower //100
            val iso: Int = progress * (max1 - min1) / 100 + min1
            previewRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, iso)
//            unlockFocus()
            captureSession.setRepeatingRequest(previewRequestBuilder.build(), captureCallback, backgroundHandler)

            sensorSensitivity = iso
            isoCustomSeekBar.text = "ISO $iso"
            saveData()      //save shared preferences
            false
        }

        val tvCustomSeekBar: CustomSeekBar = view.findViewById(R.id.tvCustomSeekBar)
        tvCustomSeekBar.text = "1/${"%.1f".format(10.toDouble().pow(9.toDouble()) / exposureTime)}s"
        tvCustomSeekBar.setOnTouchListener{_, _ ->
            val progress = tvCustomSeekBar.progress
            val range = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            val max = range!!.upper
            val min = range.lower
            val ae: Long = progress * (max - min) / 100 + min
            previewRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, ae)      //shutter speed = 1 / (10^9 / ae) sec.
//            unlockFocus()

            captureSession.setRepeatingRequest(previewRequestBuilder.build(), captureCallback, backgroundHandler)
            exposureTime = ae

            tvCustomSeekBar.text = "1/${"%.1f".format(10.toDouble().pow(9.toDouble()) / ae)}s"
            saveData()      //save shared preferences
            false
        }

        val avCustomSeekBar: CustomSeekBar = view.findViewById(R.id.avCustomSeekBar)
        avCustomSeekBar.text = "F$aperture"
        avCustomSeekBar.setOnTouchListener{_, _ ->
            val apertures = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
            val apertureScale = avCustomSeekBar.maxProgress / (apertures?.size ?: 1)
            val apertureIndex = avCustomSeekBar.progress/apertureScale

            if(apertureIndex < apertures?.size ?: 1)
                aperture = apertures?.get(apertureIndex)!!

            previewRequestBuilder.set(CaptureRequest.LENS_APERTURE, aperture)
            captureSession.setRepeatingRequest(previewRequestBuilder.build(), captureCallback, backgroundHandler)
            this.aperture = aperture
            avCustomSeekBar.text = "F$aperture"
            saveData()
            false
        }

        val imageView: ImageView = view.findViewById(R.id.btnPhotoBox)
        try {
            val imageGalleryUiModelList: MutableMap<String, ArrayList<ImageGalleryUiModel>> =
                MediaHelper.getImageGallery(this.context!!)
            imageGalleryUiModelList.forEach {
                Log.i("Camera2Fragment", it.key + ": " + it.value)
            }

            val imageList:ArrayList<ImageGalleryUiModel> = imageGalleryUiModelList["Camera"]!!
            file = File(imageList[0].imageUri)
            imageView.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (isManualEnable) {
            btnManual.setImageResource(R.drawable.ic_manual_selection)
            avCustomSeekBar.visibility = View.VISIBLE
            tvCustomSeekBar.visibility = View.VISIBLE
            isoCustomSeekBar.visibility = View.VISIBLE
        } else {
            btnManual.setImageResource(R.drawable.ic_manual)
            avCustomSeekBar.visibility = View.INVISIBLE
            tvCustomSeekBar.visibility = View.INVISIBLE
            isoCustomSeekBar.visibility = View.INVISIBLE
        }

        progressbarShutter = view.findViewById(R.id.progressBar_shutter)

        val stamp = view.findViewById<View>(R.id.android)
        stamp.setOnClickListener(this)

        val gradation = view.findViewById<View>(R.id.gradation)
        gradation.setOnClickListener(this)

        focus = view.findViewById(R.id.focus_view)
        textureView = view.findViewById(R.id.texture)

        textureView.setOnTouchListener(surfaceTextureTouchListener)

        if (m_address.contains(":"))
            ConnectToDevice(context!!).execute()        // connect to bluetooth device
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

        readCommandHandler.postDelayed(readCommandRunnable, 500)

        if (!btnPicture.isEnabled)
            btnPicture.isEnabled = true

        Log.i(TAG, "onResume")
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.btnPicture -> {
                lifecycleScope.launch(Dispatchers.Main) {
                    if (IMAGE_BUFFER_SIZE - shutterTimes <= 1) {
                        openCamera(textureView.width, textureView.height)
                        progressbarShutter?.visibility = View.INVISIBLE
                        shutterTimes = 0
                    }
                }

                // Disable click listener to prevent multiple requests simultaneously in flight
                progressbarShutter?.max = 100
                progressbarShutter?.progress = 0
                progressbarShutter?.visibility = ProgressBar.VISIBLE

                view.findViewById<View>(R.id.btnPicture).isEnabled = false

                // Perform I/O heavy operations in a different scope
                lifecycleScope.launch(Dispatchers.IO) {
                    mRawImageReader?.let {
                        takePhoto(it).use { result ->
                            Log.d(TAG, "Result received: $result")

                            // Save the result to disk
                            val output = saveResult(result)
                            Log.d(TAG, "Image saved: ${output.absolutePath}")

                            MediaScannerConnection.scanFile(
                                context, arrayOf(output.path),
                                arrayOf("image/", "image/x-adobe-dng")
                            ) { path, _ ->
                                Log.i(TAG, "onScanCompleted : $path")
                            }
                        }
                        shutterTimes += 1
                    }

                    takePhoto(mImageReader).use { result ->
                        Log.d(TAG, "Result received: $result")

                        // Save the result to disk
                        val output = saveResult(result)
                        Log.d(TAG, "Image saved: ${output.absolutePath}")

                        // If the result is a JPEG file, update EXIF metadata with orientation info
                        if (output.name.contains("jpg")) {
                            val exif = ExifInterface(output.absolutePath)
                            exif.setAttribute(
                                ExifInterface.TAG_ORIENTATION, result.orientation.toString()
                            )
                            exif.saveAttributes()
                            Log.d(TAG, "EXIF metadata saved: ${output.absolutePath}")
                        }

                        MediaScannerConnection.scanFile(context, arrayOf(output.path),
                            arrayOf("image/jpeg")) { path, _ ->
                            Log.i(TAG, "onScanCompleted : $path")
                        }
                    }
                    shutterTimes += 1

                    var historyViewModel = ViewModelProvider(this@Camera2BasicFragment).get(HistoryViewModel::class.java)
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    val currentDateTime: String = dateFormat.format(Date()) // Find todays date
                    historyViewModel.insert(History(currentDateTime, 20000, 20202020, "Warm Red"))

                    view.findViewById<View>(R.id.btnPicture).isEnabled = true
                }


                Log.i(TAG, "SENSOR_INFO_COLOR_FILTER_ARRANGEMENT: " + characteristics.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT))
                cameraHandler.postDelayed({
                    view.post {
                        if (!btnPicture.isEnabled)
                            btnPicture.isEnabled = true

                        progressbarShutter?.visibility = View.INVISIBLE
                    }
                }, 5000)
            }
            R.id.btnAuto -> {
                val btnAuto = view.findViewById<ImageButton>(R.id.btnAuto)
                isAutoEnable = !isAutoEnable

                if(isAutoEnable){
                    btnAuto.setImageResource(R.drawable.ic_auto_selection)
                }else{
                    btnAuto.setImageResource(R.drawable.ic_auto)
                }

//                if (activity != null) {
//                    AlertDialog.Builder(activity)
//                        .setMessage(R.string.intro_message)
//                        .setPositiveButton(android.R.string.ok, null)
//                        .show()
//                }
            }
            R.id.btnContrast -> {
                val btnContrast = view.findViewById<ImageView>(R.id.btnContrast)
                isContrastEnable = !isContrastEnable

                if(isContrastEnable){
                    btnContrast.setImageResource(R.drawable.ic_contrast_selection)
                }else{
                    btnContrast.setImageResource(R.drawable.ic_contrast)
                }

                sendCommand(MBITSP2020().let{
                    it.setPattern(MBITSP2020.Pattern.CONTRAST_4)
                    it.setDisplayWidth(1920)
                    it.setDisplayHeight(1080)
                    it.setDisplayStartX(0)
                    it.setDisplayStartY(0)
                    it.setModuleWidth(64)
                    it.setModuleHeight(40)
                    it.setGrayScale(MBITSP2020.GrayScaleSets.GRAY_SCALE_1, 255.toByte(), 0, 0)
                    it.setGrayScale(MBITSP2020.GrayScaleSets.GRAY_SCALE_2, 255.toByte(), 255.toByte(), 0)
                    it.setGrayScale(MBITSP2020.GrayScaleSets.GRAY_SCALE_3, 255.toByte(), 0, 255.toByte())
                    it.setGrayScale(MBITSP2020.GrayScaleSets.GRAY_SCALE_4, 255.toByte(), 221.toByte(), 128.toByte())
                    it.composeCommand()
                })
            }
            R.id.btnRefreshRate -> {
                val btnRefreshRate = view.findViewById<ImageView>(R.id.btnRefreshRate)
                isRefreshRateEnable = !isRefreshRateEnable

                if(isRefreshRateEnable){
                    btnRefreshRate.setImageResource(R.drawable.ic_refresh_rate_selection)
                }else{
                    btnRefreshRate.setImageResource(R.drawable.ic_refresh_rate)
                }

                sendCommand(MBITSP2020().let{
                    it.setPattern(MBITSP2020.Pattern.REFRESH_RATE)
                    it.setDisplayWidth(1920)
                    it.setDisplayHeight(1080)
                    it.setDisplayStartX(0)
                    it.setDisplayStartY(0)
                    it.setModuleWidth(64)
                    it.setModuleHeight(40)
                    it.setGrayScale(MBITSP2020.GrayScaleSets.GRAY_SCALE_1, 255.toByte(), 0, 0)
                    it.setGrayScale(MBITSP2020.GrayScaleSets.GRAY_SCALE_2, 255.toByte(), 255.toByte(), 0)
                    it.setGrayScale(MBITSP2020.GrayScaleSets.GRAY_SCALE_3, 255.toByte(), 0, 255.toByte())
                    it.setGrayScale(MBITSP2020.GrayScaleSets.GRAY_SCALE_4, 255.toByte(), 221.toByte(), 128.toByte())
                    it.composeCommand()
                })
            }
            R.id.btnColorTemperature -> {
                val btnColorTemperature = view.findViewById<ImageView>(R.id.btnColorTemperature)
                isColorTemperatureEnable = !isColorTemperatureEnable

                if(isColorTemperatureEnable){
                    btnColorTemperature.setImageResource(R.drawable.ic_color_temperature_selection)
                }else{
                    btnColorTemperature.setImageResource(R.drawable.ic_color_temperature)
                }

                sendCommand(MBITSP2020().let{
                    it.setPattern(MBITSP2020.Pattern.COLOR_TEMPERATURE)
                    it.setDisplayWidth(1920)
                    it.setDisplayHeight(1080)
                    it.setDisplayStartX(0)
                    it.setDisplayStartY(0)
                    it.setModuleWidth(64)
                    it.setModuleHeight(40)
                    it.setGrayScale(MBITSP2020.GrayScaleSets.GRAY_SCALE_1, 255.toByte(), 0, 0)
                    it.setGrayScale(MBITSP2020.GrayScaleSets.GRAY_SCALE_2, 255.toByte(), 255.toByte(), 0)
                    it.setGrayScale(MBITSP2020.GrayScaleSets.GRAY_SCALE_3, 255.toByte(), 0, 255.toByte())
                    it.setGrayScale(MBITSP2020.GrayScaleSets.GRAY_SCALE_4, 255.toByte(), 221.toByte(), 128.toByte())
                    it.composeCommand()
                })
            }
            R.id.btnManual ->{
                val btnManual = view.findViewById<ImageButton>(R.id.btnManual)
                isManualEnable = !isManualEnable

                if(isManualEnable){
                    btnManual.setImageResource(R.drawable.ic_manual_selection)

                    tvCustomSeekBar.visibility = View.VISIBLE
                    avCustomSeekBar.visibility = View.VISIBLE
                    isoCustomSeekBar.visibility = View.VISIBLE
                }else{
                    btnManual.setImageResource(R.drawable.ic_manual)

                    tvCustomSeekBar.visibility = View.INVISIBLE
                    avCustomSeekBar.visibility = View.INVISIBLE
                    isoCustomSeekBar.visibility = View.INVISIBLE
                }
                saveData()
            }
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
        }
    }

    override fun onPause() {
        super.onPause()
        Log.i(TAG, "onPause")
    }

    override fun onStop() {
        super.onStop()
        readCommandHandler.removeCallbacks(readCommandRunnable)
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
                        CompareSizesByArea())

                    mRawImageReader = ImageReader.newInstance(
                        largestRawImageSize.width,
                        largestRawImageSize.height,
                        RAW_FORMAT,
                        IMAGE_BUFFER_SIZE)
                }else{
                    Log.i(TAG, "This device doesn't support Raw")

                    largestRawImageSize = null
                    mRawImageReader = null
                }

                val largestImageSize = Collections.max(
                    listOf(*map.getOutputSizes(JPEG_FORMAT)),
                    CompareSizesByArea())

                mImageReader = ImageReader.newInstance(
                    largestImageSize.width,
                    largestImageSize.height,
                    JPEG_FORMAT, IMAGE_BUFFER_SIZE)

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
        var bufferRect = RectF(0f, 0f, previewSize.height.toFloat(), previewSize.width.toFloat())

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
                            setAutoFlash(previewRequestBuilder)

                            // Finally, we start displaying the camera preview.
                            previewRequest = previewRequestBuilder.build()
                            captureSession.setRepeatingRequest(
                                previewRequest,
                                captureCallback, backgroundHandler
                            )

                            if (sensorSensitivity != 0) previewRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, sensorSensitivity)     //20200331 Craig return last state
                            if (exposureTime.toInt() != 0)  previewRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureTime)
                            if (aperture.toInt() != 0)  previewRequestBuilder.set(CaptureRequest.LENS_APERTURE, aperture)
                            if (zoom != null)   previewRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoom)

//                            unlockFocus()
                            captureSession.setRepeatingRequest(previewRequestBuilder.build(), captureCallback, backgroundHandler)
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
     * Run the precapture sequence for capturing a still image. This method should be called when
     * we get a response in [.captureCallback] from [.lockFocus].
     */
    private fun runPrecaptureSequence() {
        try {
            // This is how to tell the camera to trigger.
            previewRequestBuilder.set(
                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START
            )

            // Tell #captureCallback to wait for the precapture sequence to be set.
            state = STATE_WAITING_PRECAPTURE
            captureSession.capture(
                previewRequestBuilder.build(), captureCallback,
                backgroundHandler
            )
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        }
    }

    /**
     * Capture a still picture. This method should be called when we get a response in
     * [.captureCallback] from both [.lockFocus].
     */
    private fun captureStillPicture() {
        try {
            if (activity == null || cameraDevice == null) return
            val rotation = activity!!.windowManager.defaultDisplay.rotation

            // This is the CaptureRequest.Builder that we use to take a picture.
            val captureBuilder = cameraDevice?.createCaptureRequest(
                CameraDevice.TEMPLATE_STILL_CAPTURE
            )?.apply {
                addTarget(mImageReader.surface)

                mImageReader.let {
                    addTarget(it.surface)
                }

                // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
                // We have to take that into account and rotate JPEG properly.
                // For devices with orientation of 90, we return our mapping from ORIENTATIONS.
                // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
                set(
                    CaptureRequest.JPEG_ORIENTATION,
                    sensorToDeviceRotation(characteristics, rotation)
                )

                // Use the same AE and AF modes as the preview.
                set(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                )
            }?.also { setAutoFlash(it) }

            val captureCallback = object : CameraCaptureSession.CaptureCallback() {

                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    activity!!.showToast("Saved: $file")
                    Log.d(TAG, file.toString())
//                    unlockFocus()
                }
            }

            captureSession.apply {
                stopRepeating()
                abortCaptures()
                capture(captureBuilder?.build()!!, captureCallback, null)
            }

        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        }
    }

    /**
     * Unlock the focus. This method should be called when still image capture sequence is
     * finished.
     */
    private fun unlockFocus() {
        try {
            // Reset the auto-focus trigger
            previewRequestBuilder.set(
                CaptureRequest.CONTROL_AF_TRIGGER,
                CameraMetadata.CONTROL_AF_TRIGGER_CANCEL
            )

            setAutoFlash(previewRequestBuilder)

            captureSession.capture(
                previewRequestBuilder.build(), captureCallback,
                backgroundHandler
            )
            // After this, the camera will go back to the normal state of preview.
            state = STATE_PREVIEW
            captureSession.setRepeatingRequest(
                previewRequest, captureCallback,
                backgroundHandler
            )
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        }
    }

    /**
     * Lock the focus as the first step for a still image capture.
     */
    private fun lockFocus() {
        try {
            // This is how to tell the camera to lock focus.
            previewRequestBuilder.set(
                CaptureRequest.CONTROL_AF_TRIGGER,
                CameraMetadata.CONTROL_AF_TRIGGER_START
            )
            // Tell #captureCallback to wait for the lock.
            state = STATE_WAITING_LOCK
            captureSession.capture(
                previewRequestBuilder.build(), captureCallback,
                backgroundHandler
            )
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        }
    }

    /**
     * Helper function used to capture a still image using the [CameraDevice.TEMPLATE_STILL_CAPTURE]
     * template. It performs synchronization between the [CaptureResult] and the [Image] resulting
     * from the single capture, and outputs a [CombinedCaptureResult] object.
     */
    private suspend fun takePhoto(imageReader: ImageReader):
            CombinedCaptureResult = suspendCoroutine { cont ->

        // Flush any images left in the image reader
        @Suppress("ControlFlowWithEmptyBody")
        while (imageReader.acquireNextImage() != null) {
        }

        // Start a new image queue
        val imageQueue = ArrayBlockingQueue<Image>(IMAGE_BUFFER_SIZE)

        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireNextImage()
            Log.d(TAG, "Image available in queue: ${image.timestamp}")
            imageQueue.add(image)
        }, imageReaderHandler)

        val captureRequest = captureSession.device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
        if(mRawImageReader != null) captureRequest.addTarget(mRawImageReader!!.surface)
        captureRequest.addTarget(mImageReader.surface)

        val rotation = activity!!.windowManager.defaultDisplay.rotation

        //captureRequest.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_OFF)
        captureRequest.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
        captureRequest.set(CaptureRequest.SCALER_CROP_REGION, zoom)
        if (sensorSensitivity != 0) captureRequest.set(CaptureRequest.SENSOR_SENSITIVITY, sensorSensitivity)     //20200331 Craig return last state
        if (exposureTime.toInt() != 0)  captureRequest.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureTime)
        if (aperture.toInt() != 0)  captureRequest.set(CaptureRequest.LENS_APERTURE, aperture)

        captureRequest.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation))
        Log.i(TAG, "takePhoto, rotation: $rotation")

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
                        cameraHandler.post{ mediaActionSound.play(MediaActionSound.SHUTTER_CLICK) }
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
                        while (true) {

                            // Dequeue images while timestamps don't match
                            val image = imageQueue.take()

                            // TODO(owahltinez): b/142011420
                            // if (image.timestamp != resultTimestamp) continue
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                                image.format != ImageFormat.DEPTH_JPEG &&
                                image.timestamp != resultTimestamp
                            ) continue
                            Log.d(TAG, "Matching image dequeued: ${image.timestamp}")

                            // Unset the image reader listener
                            imageReaderHandler.removeCallbacks(timeoutRunnable)
                            imageReader.setOnImageAvailableListener(null, null)

                            // Clear the queue of images, if there are left
                            while (imageQueue.size > 0) {
                                imageQueue.take().close()
                            }

                            cont.resume(
                                CombinedCaptureResult(
                                    image, result, ExifInterface.ORIENTATION_NORMAL, imageReader.imageFormat
                                )
                            )

                            // There is no need to break out of the loop, this coroutine will suspend
                        }
                    }
                }
            },
            cameraHandler
        )
    }


    /** Helper function used to save a [CombinedCaptureResult] into a [File] */
    private suspend fun saveResult(result: CombinedCaptureResult): File = suspendCoroutine { cont ->
        when (result.format) {

            // When the format is JPEG or DEPTH JPEG we can simply save the bytes as-is
            ImageFormat.JPEG, ImageFormat.DEPTH_JPEG -> {
                val buffer = result.image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining()).apply { buffer.get(this) }

                try {
                    val output = createFile("jpg")
//                    FileOutputStream(output).use { it.write(bytes) }

                    val temp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size).rotate(90f)
                    temp.compress(Bitmap.CompressFormat.JPEG, 100, FileOutputStream(output))

                    calculateColorTemp(bytes)
                    progressbarShutter?.progress = 100 / 3 * 2
                    calculateRefreshRate(bytes)
                    progressbarShutter?.progress = 100 /3 * 3
                    progressbarShutter?.visibility = View.INVISIBLE
                    cont.resume(output)
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
                    val output = createFile("dng")
                    FileOutputStream(output).use { dngCreator.writeImage(it, result.image) }

                    val byteBuffer = result.image.planes[0].buffer
                    val byteArray = ByteArray(byteBuffer.remaining())
                    byteBuffer.get(byteArray)

                    calculateContrast(result.image.width, result.image.height, byteArray)
                    progressbarShutter?.progress = 100 /3 * 1

//                    var aver = average(byteArray)
                    Log.i(TAG, "Image size: ${result.image.width} x ${result.image.height}")
                    Log.i(TAG, "Buffer size: ${byteArray.size}")
//                    Log.i(TAG, "Buffer average: ${aver}")

                    Log.i(TAG,"Dng orientation: "+ result.orientation.toString())

                    cont.resume(output)
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

    private fun calculateRefreshRate(bytes: ByteArray) {
        val temp  = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val bitmap = temp.rotate(90f)
        //val mat = Mat()
        //val canny = mat.canny(bitmap) { it.compress(Bitmap.CompressFormat.JPEG, 80, FileOutputStream(createFile("jpg")))}

        findLines(bitmap).toBitmap().compress(Bitmap.CompressFormat.JPEG, 80, FileOutputStream(createFile("jpg")))

        Log.i(TAG, "Find circles fin!")
    }

    private fun calculateColorTemp(bytes: ByteArray) {
        val temp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val bitmap = temp.rotate(90f)
        val argb = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
        var red = Color.red(argb)
        var green = Color.green(argb)
        var blue = Color.blue(argb)
        var n: Double = ((0.23881) * red + (0.25499) * green - (0.58291) * blue) / ((0.11109) * red - (0.85406) * green + (0.52289) * blue)
        var CCT = 449 * n.pow(3) + 3525 * n.pow(2) + 6823.3 * n + 5520.33
        Log.i(TAG, "CCT: $CCT")

//        bitmap.compress(Bitmap.CompressFormat.PNG, 85, FileOutputStream(file))
    }

    private fun calculateContrast(width: Int, height: Int, bytes: ByteArray) {
        File(createFile("raw").toString()).writeBytes(bytes)
//        val rawBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
//        val argb = rawBitmap.getPixel(0, 0)
//        Log.i(TAG, "red: ${Color.red(argb)}, green: ${Color.green(argb)}, blue: ${Color.blue(argb)}")

        val pixels: IntArray = IntArray(bytes.size / 2)
        for (i in bytes.indices) {
            var value = bytes[i].toInt() and 0xFF
            if (i % 2 == 0)
                pixels[i / 2] += value * 256
            else
                pixels[i / 2] += value
        }

        Log.i(TAG, "Pixels max: ${pixels.max()}, Pixels min: ${pixels.min()}")
//        val rChannel = IntArray(width * height)
//        val gChannel = IntArray(width * height)
//        val bChannel = IntArray(width * height)
        val yChannel = IntArray(width * height)

        var min_r = Int.MAX_VALUE
        var min_g = Int.MAX_VALUE
        var min_b = Int.MAX_VALUE
        var max_r = Int.MIN_VALUE
        var max_g = Int.MIN_VALUE
        var max_b = Int.MIN_VALUE

        Log.i(TAG, "Pixels size: ${pixels.size}")
        Log.i(TAG, "Img Width: $width, Height: $height")
        Log.i(TAG, "White balance")
        for (j in 0 until height) {
            for (i in 0 until width) {
                if (abs(i - j) % 2 == 0) {
                    if (pixels[(i + 0) + (j + 0) * width] < min_g && pixels[(i + 0) + (j + 0) * width] != 0) min_g = pixels[(i + 0) + (j + 0) * width]
                    if (pixels[(i + 0) + (j + 0) * width] > max_g) max_g = pixels[(i + 0) + (j + 0) * width]
                }
                if (j % 2 == 0 && i % 2 == 1) {
                    if (pixels[(i + 0) + (j + 0) * width] < min_b && pixels[(i + 0) + (j + 0) * width] != 0) min_b = pixels[(i + 0) + (j + 0) * width]
                    if (pixels[(i + 0) + (j + 0) * width] > max_b) max_b = pixels[(i + 0) + (j + 0) * width]
                }
                if (i % 2 == 0 && j % 2 == 1) {
                    if (pixels[(i + 0) + (j + 0) * width] < min_r && pixels[(i + 0) + (j + 0) * width] != 0) min_r = pixels[(i + 0) + (j + 0) * width]
                    if (pixels[(i + 0) + (j + 0) * width] > max_r) max_r = pixels[(i + 0) + (j + 0) * width]
                }
            }
        }

        Log.i(TAG, "Min r: $min_r, Min g: $min_g, Min b: $min_b")
        Log.i(TAG, "Max r: $max_r, Max g: $max_g, Max b: $max_b")
        Log.i(TAG, "Demosiac")
        for (j in 0 until height - 1) {
            for (i in 0 until width - 1) {
                var r = 0
                var g = 0
                var b = 0
                if (abs(i - j) % 2 == 0) {
                    g =  pixels[(i + 0) + (j + 0) * width]
                } else if (j - 1 >= 0) {
                    g = (pixels[i + (j + 0) * width] + pixels[(i + 1) + (j + 0) * width] + pixels[(i + 0) + (j + 1) * width] + pixels[(i + 0) + (j - 1) * width]) / 4
                } else {
                    g = (pixels[i + (j + 0) * width] + pixels[(i + 1) + (j + 0) * width] + pixels[(i + 0) + (j + 1) * width]) / 3
                }

                if (j % 2 == 0 && i % 2 == 1) {
                    b = pixels[(i + 0) + (j + 0) * width]
                } else {
                    if (i % 2 == 0 && j % 2 == 1 && (i - 1) >= 0 && (j - 1) >= 0)
                        b = (pixels[(i - 1) + (j - 1) * width] + pixels[(i - 1) + (j + 1) * width] + pixels[(i + 1) + (j - 1)* width] + pixels[(i + 1) + (j + 1) * width]) / 4
                    else
                        b = pixels[(i + 1) + (j + 1) * width]

                    if (abs(i - j) % 2 == 0 && i % 2 == 0 && (i - 1) >= 0)
                        b = (pixels[(i - 1) + (j + 0) * width] + pixels[(i + 1) + (j + 0) * width]) / 2
                    else
                        b = pixels[(i + 1) + (j + 0) * width]

                    if (abs(i - j) % 2 == 0 && i % 2 == 1 && (j - 1) >= 0)
                        b = (pixels[(i + 0) + (j - 1) * width] + pixels[(i + 0) + (j + 1) * width]) / 2
                    else
                        b = pixels[(i + 0) + (j + 1) * width]
                }

                if (i % 2 == 0 && j % 2 == 1) {
                    r = pixels[(i + 0) + (j + 0) * width]
                } else {
                    if (j % 2 == 0  && i % 2 == 1 && (i - 1) >= 0 && (j - 1) >= 0)
                        r = (pixels[(i - 1) + (j - 1) * width] + pixels[(i - 1) + (j + 1) * width] + pixels[(i + 1) + (j - 1) * width] + pixels[(i + 1) + (j + 1) * width]) / 4
                    else
                        r = pixels[(i + 1) + (j + 1) * width]

                    if (abs(i - j) % 2 == 0 && i % 2 == 1 && (i - 1) >= 0)
                        r = (pixels[(i - 1) + (j + 0) * width] + pixels[(i + 1) + (j + 0) * width]) / 2
                    else
                        r = pixels[(i + 1) + (j + 0) * width]

                    if (abs(i - j) % 2 == 0 && i % 2 == 0 && (j - 1) >= 0)
                        r = (pixels[(i + 0) + (j - 1) * width] + pixels[(i + 0) + (j + 1) * width]) / 2
                    else
                        r = pixels[(i + 0) + (j + 1) * width]
                }

//                rChannel[i + j * width] = (r - min_r) * 65535 / (max_r - min_r)
//                gChannel[i + j * width] = (g - min_g) * 65535 / (max_g - min_g)
//                bChannel[i + j * width] = (b - min_b) * 65535 / (max_b - min_b)
                yChannel[i + j * width] = ((r * 299) + (g * 578) + (b * 114)) / 1000
            }
        }

        val max = pixels.max()
        var min = Int.MAX_VALUE
        for (element in pixels) {
            if (element in 1 until min)
                min = element
        }
        Log.i(TAG, "Max $max, Min: $min")
//        Log.i(TAG, "contrast ratio: ${max!! / min}")

//        Log.i(TAG, "(0, 0) r: ${rChannel[0]}, g: ${gChannel[0]}, b: ${bChannel[0]}")
//        Log.i(TAG, "(w/2, h/2) r: ${rChannel[(width * height) / 2]}, g: ${gChannel[(width * height) / 2]}, b: ${bChannel[(width * height) / 2]}")
        Log.i(TAG, "(0, 0) Intensity: ${yChannel[0]}, (w/2, h/2) Intensity: ${yChannel[(width * height) / 2]}")
    }

    fun Bitmap.rotate(degree: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degree) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    private fun findCircles(bitmap: Bitmap): Mat {
        val image = bitmap.toMat()
        val hsvImage = Mat()
        val hueImage = Mat()
        val circles = Mat()

        Imgproc.medianBlur(image, image, 3)
        Imgproc.cvtColor(image, hsvImage, Imgproc.COLOR_RGB2HSV)

        val lowerRedHueRange = Mat()
        val upperRedHueRange = Mat()
        Core.inRange(hsvImage, Scalar(0.0, 150.0, 100.0), Scalar(10.0, 255.0, 255.0), lowerRedHueRange)
        Core.inRange(hsvImage, Scalar(160.0, 150.0, 100.0), Scalar(179.0, 255.0, 255.0), upperRedHueRange)
        Core.addWeighted(lowerRedHueRange, 1.0, upperRedHueRange, 1.0, 0.0, hueImage)

        Imgproc.GaussianBlur(hueImage, hueImage, org.opencv.core.Size(9.0, 9.0), 2.0, 2.0)
        Imgproc.HoughCircles(hueImage, circles, Imgproc.CV_HOUGH_GRADIENT, 1.0,
            hueImage.rows() / 8.toDouble(), 100.0, 20.0, 0, 0)

        if (circles.cols() > 0) {
            for (x in 0 until Math.min(circles.cols(), 5)) {
                val circleVec = circles.get(0, x) ?: break
                val center = org.opencv.core.Point(circleVec[0].toInt().toDouble(), circleVec[1].toInt().toDouble())
                val radius = circleVec[2].toInt()

                Imgproc.circle(image, center, 3, Scalar(0.0, 255.0, 0.0), 5)
                Imgproc.circle(image, center, radius, Scalar(0.0, 255.0, 0.0), 2)
            }
        }

        return image
    }

    private fun findLines(bitmap: Bitmap): Mat {
        val image = bitmap.toMat()
        val edges = Mat()
        val lines = Mat()

        Imgproc.cvtColor(image, edges, Imgproc.COLOR_BGR2GRAY)
        Imgproc.blur(edges, edges, org.opencv.core.Size(15.0, 1.0))
        Imgproc.GaussianBlur(edges, edges, org.opencv.core.Size(15.0, 15.0), 0.5)
        Imgproc.erode(edges, edges, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, org.opencv.core.Size(2.0, 2.0)))
        Imgproc.dilate(edges, edges, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, org.opencv.core.Size(2.0, 2.0)))
        Imgproc.Canny(edges, edges, 100.0, 200.0, 3)

        val threshold = 80
        val minLineSize = 20.0
        val linGap = 20.0

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
                Imgproc.line(image, start, end, Scalar(255.0, 0.0, 0.0), 3)
            }
        }
        return image
    }

    private fun createFile(extension: String): File {
        val sdf = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.US)
        val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)

        val dir = File(path, "/CraigCam2")
        if(!dir.isDirectory){
            dir.mkdirs()
        }

        Log.d(TAG, "File dir: " + path.toString())

        return File(dir, "/IMG_${sdf.format(Date())}.$extension")
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
            .apply()
    }

    private fun readData(){
        settings = context!!.getSharedPreferences(PREF_NAME, PRIVATE_MODE)
        sensorSensitivity = settings.getInt(SENSOR_SENSITIVITY, 0)
        exposureTime = settings.getLong(EXPOSURE_TIME, 0)
        aperture = settings.getFloat(APERTURE, 0f)
        isManualEnable = settings.getBoolean(MANUAL_ENABLE, false)
    }

    fun readSettingData(){
        settings = context!!.getSharedPreferences(
            SettingFragment.PREF_NAME,
            SettingFragment.PRIVATE_MODE
        )
        isGridEnable = settings.getBoolean(SettingFragment.GRID, false)
        isSoundEnable = settings.getBoolean(SettingFragment.SOUND, false)
        isCloudSyncEnable = settings.getBoolean(SettingFragment.CLOUD_SYNC, false)

        gridLineView = view!!.findViewById(R.id.grid_line)

        if(isGridEnable) {
            gridLineView.visibility = View.VISIBLE
        } else {
            gridLineView.visibility = View.INVISIBLE
        }
    }

    private fun sendCommand(bytes: ByteArray){
        if (m_bluetoothSocket != null){
            try {
                m_bluetoothSocket!!.outputStream.write(bytes)
                Log.i(TAG, "Bluetooth is sent data")
            }catch (e: IOException) {
                e.printStackTrace()
                disconnect()

                if (m_address.contains(":"))
                    ConnectToDevice(context!!).execute()
            }
        }
    }

    private val readCommandRunnable = Runnable {
       readCommand()
    }

    private fun readCommand(){
        if (m_bluetoothSocket != null) {
            try {
                val bytes: ByteArray = ByteArray(20)
                m_bluetoothSocket!!.inputStream.read(bytes)
                Log.i(TAG, "Bluetooth received: ${bytes[0]}")
            } catch (e: IOException) {
                e.printStackTrace()
                readCommandHandler.removeCallbacks(readCommandRunnable)
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
                    m_bluetoothSocket = device.createInsecureRfcommSocketToServiceRecord(m_myUUID)
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
         * Camera state: Waiting for the focus to be locked.
         */
        private const val STATE_WAITING_LOCK = 1

        /**
         * Camera state: Waiting for the exposure to be precapture state.
         */
        private const val STATE_WAITING_PRECAPTURE = 2

        /**
         * Camera state: Waiting for the exposure state to be something other than precapture.
         */
        private const val STATE_WAITING_NON_PRECAPTURE = 3

        /**
         * Camera state: Picture was taken.
         */
        private const val STATE_PICTURE_TAKEN = 4

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

        private const val RAW_FORMAT = ImageFormat.RAW_SENSOR
        private const val JPEG_FORMAT = ImageFormat.JPEG

        var m_myUUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        var m_bluetoothSocket: BluetoothSocket? = null
        lateinit var m_bluetoothAdapter: BluetoothAdapter
        var m_isConnected: Boolean = false
        lateinit var m_address: String

        @JvmField
        val REQUEST_CAMERA_PERMISSION = 1

        @JvmField
        val PIC_FILE_NAME = "pic.jpg"

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
            val format: Int
        ) : Closeable {
            override fun close() = image.close()
        }

        private const val PERMISSIONS_REQUEST_CODE = 10
        private val PERMISSIONS_REQUIRED = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }
}