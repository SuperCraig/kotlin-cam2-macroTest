package com.example.cargicamera2

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.hardware.camera2.*
import android.hardware.camera2.CameraCaptureSession.CaptureCallback
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.Image
import android.media.ImageReader
import android.os.*
import android.util.LruCache
import android.util.Size
import android.util.SparseIntArray
import android.view.*
import android.view.TextureView.SurfaceTextureListener
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.example.cargicamera2.ui.AutoFitTextureView
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import kotlin.Comparator
import kotlin.collections.ArrayList


class Camera2Intent: Fragment(), View.OnClickListener{
    private var mState: Int = STATE_PREVIEW
    private var mMemoryCache: LruCache<String, Bitmap>? = null
    private var mPreviewSize: Size? = null
    private var mCameraId: String? = null
    private var mCameraCharacteristics: CameraCharacteristics? = null
    private var mCaptureResult: CaptureResult? = null
    private var mTextureView: AutoFitTextureView? = null


    private val mSurfaceTextureListener: SurfaceTextureListener = object : SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(
            surface: SurfaceTexture,
            width: Int,
            height: Int
        ) {
            setupCamera(width, height)
            transformImage(width, height)
            openCamera()
        }

        override fun onSurfaceTextureSizeChanged(
            surface: SurfaceTexture,
            width: Int,
            height: Int
        ) {
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            return false
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
    }

    private var mCameraDevice: CameraDevice? = null
    private val mCameraDeviceStateCallback: CameraDevice.StateCallback =
        object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                mCameraDevice = camera
                createCameraPreviewSession()
                // Toast.makeText(getApplicationContext(), "Camera Opened!", Toast.LENGTH_SHORT).show();
            }

            override fun onDisconnected(camera: CameraDevice) {
                camera.close()
                mCameraDevice = null
            }

            override fun onError(camera: CameraDevice, error: Int) {
                camera.close()
                mCameraDevice = null
            }
        }

    private var mPreviewCaptureRequest: CaptureRequest? = null
    private var mPreviewCaptureRequestBuilder: CaptureRequest.Builder? = null
    private var mCameraCaptureSession: CameraCaptureSession? = null
    private val mSessionCaptureCallback: CaptureCallback = object : CaptureCallback() {
        private fun process(result: CaptureResult) {
            when (mState) {
                STATE_PREVIEW -> {
                }
                STATE__WAIT_LOCK -> {
                    val afState = result.get(CaptureResult.CONTROL_AF_STATE)
                    if (afState == CaptureRequest.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                        afState == CaptureRequest.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED
                    ) {
                        /*
	                        unLockFocus();
	                        Toast.makeText(getApplicationContext(), "Focus Lock Successful", Toast.LENGTH_SHORT).show();
	                        */
                        mState = STATE__PICTURE_CAPTURED
                        captureStillImage()
                    }
                }
            }
        }

        override fun onCaptureStarted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            timestamp: Long,
            frameNumber: Long
        ) {
            super.onCaptureStarted(session, request, timestamp, frameNumber)
        }

        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            super.onCaptureCompleted(session, request, result)
            process(result)
        }

        override fun onCaptureFailed(
            session: CameraCaptureSession,
            request: CaptureRequest,
            failure: CaptureFailure
        ) {
            super.onCaptureFailed(session, request, failure)
            Toast.makeText(
                context,
                "Focus Lock Unsuccessful",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private var mBackgroundThread: HandlerThread? = null
    private var mBackgroundHandler: Handler? = null
    private val mUiHandler: Handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message?) {
            super.handleMessage(msg)
        }
    }

    private lateinit var mImageReader: ImageReader
    private val mOnImageAvailableListener: ImageReader.OnImageAvailableListener =
        ImageReader.OnImageAvailableListener { reader ->
            mBackgroundHandler?.post(
                ImageSaver(
                    reader.acquireNextImage(), mUiHandler,
                    mCaptureResult!!, mCameraCharacteristics!!
                )
            )
        }

    private lateinit var mRawImageReader: ImageReader
    private val mOnRawImageAvailableListener: ImageReader.OnImageAvailableListener =
        ImageReader.OnImageAvailableListener { reader ->
            mBackgroundHandler?.post(
                ImageSaver(
                    reader.acquireNextImage(), mUiHandler,
                    mCaptureResult!!, mCameraCharacteristics!!
                )
            )
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_camera2_basic, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        mTextureView = view.findViewById<AutoFitTextureView>(R.id.texture);

        val btnTake = view.findViewById<ImageView>(R.id.btnPicture)
        btnTake.setOnClickListener {
            takePhoto(view)
        }
    }

    override fun onResume() {
        super.onResume()
        openBackgroundThread()
        if (mTextureView!!.isAvailable) {
            setupCamera(mTextureView!!.width, mTextureView!!.height)
            transformImage(mTextureView!!.width, mTextureView!!.height)
            openCamera()
        } else {
            mTextureView!!.surfaceTextureListener = mSurfaceTextureListener
        }
    }

    override fun onPause() {
        closeCamera()
        closeBackgoundThread()
        super.onPause()
    }

    fun takePhoto(view: View?) {
        /*
	        Intent callCameraApplicationIntent = new Intent();
	        callCameraApplicationIntent.setAction(MediaStore.ACTION_IMAGE_CAPTURE);
	        File photoFile = null;
	        try {
	           photoFile = createImageFile();

	        } catch (IOException e) {
	            e.printStackTrace();
	        }
	        callCameraApplicationIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(photoFile));
	        startActivityForResult(callCameraApplicationIntent, ACTIVITY_START_CAMERA_APP);
	        */
        lockFocus()
    }

    private fun getBitmapFromMemoryCache(key: String?): Bitmap? {
        return mMemoryCache!![key]
    }

    fun setBitmapToMemoryCache(key: String?, bitmap: Bitmap?) {
        if (getBitmapFromMemoryCache(key) == null) {
            mMemoryCache!!.put(key, bitmap)
        }
    }

    private fun setupCamera(width: Int, height: Int) {
        val cameraManager = activity?.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            for (cameraId in cameraManager.cameraIdList) {
                val cameraCharacteristics =
                    cameraManager.getCameraCharacteristics(cameraId)
                if (!contains(
                        cameraCharacteristics[CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES],
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW
                    )!!
                ) {
                    continue
                }
                if (cameraCharacteristics[CameraCharacteristics.LENS_FACING] ===
                    CameraCharacteristics.LENS_FACING_FRONT
                ) {
                    continue
                }
                val map: StreamConfigurationMap? =
                    cameraCharacteristics[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP]

                val largestImageSize: Size =  Collections.max(
                    listOf(*map!!.getOutputSizes(ImageFormat.JPEG)),
                    CompareSizesByArea())

                val largestRawImageSize: Size = Collections.max(
                    listOf(*map.getOutputSizes(ImageFormat.RAW_SENSOR)),
                    CompareSizesByArea())

                mImageReader = ImageReader.newInstance(
                    largestImageSize.width,
                    largestImageSize.height,
                    ImageFormat.JPEG,
                    1
                )

                mImageReader.setOnImageAvailableListener(
                    mOnImageAvailableListener,
                    mBackgroundHandler
                )

                mRawImageReader = ImageReader.newInstance(
                    largestRawImageSize.width,
                    largestRawImageSize.height,
                    ImageFormat.RAW_SENSOR,
                    1
                )

                mRawImageReader.setOnImageAvailableListener(
                    mOnRawImageAvailableListener,
                    mBackgroundHandler
                )

                mPreviewSize = getPreferredPreviewSize(
                    map.getOutputSizes(SurfaceTexture::class.java),
                    width,
                    height
                )
                mCameraId = cameraId
                mCameraCharacteristics = cameraCharacteristics
                return
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun getPreferredPreviewSize(
        mapSizes: Array<Size>,
        width: Int,
        height: Int
    ): Size {
        val collectorSizes: ArrayList<Size> = ArrayList()
        for (option in mapSizes) {
            if (width > height) {
                if (option.width > width &&
                    option.height > height
                ) {
                    collectorSizes.add(option)
                }
            } else {
                if (option.width > height &&
                    option.height > width
                ) {
                    collectorSizes.add(option)
                }
            }
        }
        return if (collectorSizes.size > 0) {
            Collections.min(collectorSizes) { o1, o2 ->
                java.lang.Long.signum(
                    o1!!.width * o1.height - o2!!.width * o2.height.toLong()
                )
            }
        } else mapSizes[0]
    }

    @SuppressLint("MissingPermission")
    private fun openCamera() {
        val cameraManager = activity?.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            cameraManager.openCamera(mCameraId!!, mCameraDeviceStateCallback, mBackgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun closeCamera() {
        if (mCameraCaptureSession != null) {
            mCameraCaptureSession!!.close()
            mCameraCaptureSession = null
        }
        if (mCameraDevice != null) {
            mCameraDevice!!.close()
            mCameraDevice = null
        }
    }

    private fun createCameraPreviewSession() {
        try {
            val surfaceTexture = mTextureView!!.surfaceTexture
            surfaceTexture.setDefaultBufferSize(mPreviewSize!!.width, mPreviewSize!!.height)
            val previewSurface = Surface(surfaceTexture)
            mPreviewCaptureRequestBuilder =
                mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            mPreviewCaptureRequestBuilder!!.addTarget(previewSurface)
            mCameraDevice!!.createCaptureSession(
                listOf(
                    previewSurface, mImageReader.surface,
                    mRawImageReader.surface
                ),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Toast.makeText(
                            context,
                            "create camera session failed!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    override fun onConfigured(session: CameraCaptureSession) {
                        if (mCameraDevice == null) {
                            return
                        }
                        try {
                            mPreviewCaptureRequest = mPreviewCaptureRequestBuilder!!.build()
                            mCameraCaptureSession = session
                            mCameraCaptureSession!!.setRepeatingRequest(
                                mPreviewCaptureRequest!!,
                                mSessionCaptureCallback,
                                mBackgroundHandler
                            )
                        } catch (e: CameraAccessException) {
                            e.printStackTrace()
                        }
                    }
                }, null
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun openBackgroundThread() {
        mBackgroundThread = HandlerThread("Camera2 background thread")
        mBackgroundThread!!.start()
        mBackgroundHandler = Handler(mBackgroundThread!!.getLooper())
    }

    private fun closeBackgoundThread() {
        mBackgroundThread!!.quitSafely()
        try {
            mBackgroundThread!!.join()
            mBackgroundThread = null
            mBackgroundHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    private fun lockFocus() {
        try {
            mState = STATE__WAIT_LOCK
            mPreviewCaptureRequestBuilder!!.set(
                CaptureRequest.CONTROL_AF_TRIGGER,
                CaptureRequest.CONTROL_AF_TRIGGER_START
            )
            mCameraCaptureSession!!.capture(
                mPreviewCaptureRequestBuilder!!.build(),
                mSessionCaptureCallback, mBackgroundHandler
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun unLockFocus() {
        try {
            mState = STATE_PREVIEW
            mPreviewCaptureRequestBuilder!!.set(
                CaptureRequest.CONTROL_AF_TRIGGER,
                CaptureRequest.CONTROL_AF_TRIGGER_CANCEL
            )
            mCameraCaptureSession!!.setRepeatingRequest(
                mPreviewCaptureRequestBuilder!!.build(),
                mSessionCaptureCallback, mBackgroundHandler
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun captureStillImage() {
        try {
            val captureStillBuilder =
                mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureStillBuilder.addTarget(mImageReader.surface)
            captureStillBuilder.addTarget(mRawImageReader.surface)
            val rotation: Int = activity!!.windowManager.defaultDisplay.rotation
            captureStillBuilder[CaptureRequest.JPEG_ORIENTATION] = ORIENTATIONS[rotation]

            val captureCallback: CaptureCallback = object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureStarted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    timestamp: Long,
                    frameNumber: Long
                ) {
                    super.onCaptureStarted(session, request, timestamp, frameNumber)
                    try {

                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }

                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    super.onCaptureCompleted(session, request, result)

                    /*
	                            Toast.makeText(getApplicationContext(),
	                                    "Image Captured!", Toast.LENGTH_SHORT).show();
	                            */mCaptureResult = result
                    unLockFocus()
                }
            }
            mCameraCaptureSession!!.stopRepeating()
            mCameraCaptureSession!!.capture(
                captureStillBuilder.build(), captureCallback, null
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun transformImage(width: Int, height: Int) {
        if (mPreviewSize == null || mTextureView == null) {
            return
        }
        val matrix = Matrix()
        val rotation: Int = activity!!.windowManager.getDefaultDisplay().getRotation()
        val textureRectF = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val previewRectF = RectF(0f, 0f, mPreviewSize!!.getHeight().toFloat(),
            mPreviewSize!!.getWidth().toFloat()
        )
        val centerX: Float = textureRectF.centerX()
        val centerY: Float = textureRectF.centerY()
        if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
            previewRectF.offset(
                centerX - previewRectF.centerX(),
                centerY - previewRectF.centerY()
            )
            matrix.setRectToRect(textureRectF, previewRectF, Matrix.ScaleToFit.FILL)
            val scale = Math.max(
                width.toFloat() / mPreviewSize!!.getWidth(),
                height.toFloat() / mPreviewSize!!.getHeight()
            )
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
        }
        mTextureView!!.setTransform(matrix)
    }

    private class CompareSizeByArea : Comparator<Size?> {
        override fun compare(o1: Size?, o2: Size?): Int {
            return java.lang.Long.signum(
                o1!!.width.toLong() * o1.height -
                        o2!!.width.toLong() * o2.height
            )
        }
    }

    private fun contains(modes: IntArray?, mode: Int): Boolean? {
        if (modes == null) {
            return false
        }
        for (i in modes) {
            if (i == mode) {
                return true
            }
        }
        return false
    }

    private class ImageSaver internal constructor(
        image: Image,
        handler: Handler,
        captureResult: CaptureResult,
        cameraCharacteristics: CameraCharacteristics
    ) : Runnable {
        private val mImage: Image = image
        private val mHandler: Handler = handler
        private val mCaptureResult: CaptureResult = captureResult
        private val mCameraCharacteristics: CameraCharacteristics = cameraCharacteristics
        override fun run() {
            when (mImage.format) {
                ImageFormat.JPEG -> {
                    val byteBuffer: ByteBuffer = mImage.planes[0].buffer
                    val bytes = ByteArray(byteBuffer.remaining())
                    byteBuffer.get(bytes)
                    var fileOutputStream: FileOutputStream? = null
                    try {
                        val output = createFile("jpg")
                        fileOutputStream = FileOutputStream(output)
                        fileOutputStream.write(bytes)
                    } catch (e: IOException) {
                        e.printStackTrace()
                    } finally {
                        mImage.close()
                        if (fileOutputStream != null) {
                            try {
                                fileOutputStream.close()
                            } catch (e: IOException) {
                                e.printStackTrace()
                            }
                        }
                        val message = mHandler.obtainMessage()
                        message.sendToTarget()
                    }
                }
                ImageFormat.RAW_SENSOR -> {
                    val dngCreator = DngCreator(mCameraCharacteristics, mCaptureResult)
                    var rawFileOutputStream: FileOutputStream? = null
                    try {
                        val output = createFile("dng")
                        rawFileOutputStream = FileOutputStream(output)
                        dngCreator.writeImage(rawFileOutputStream, mImage)
                    } catch (e: IOException) {
                        e.printStackTrace()
                    } finally {
                        mImage.close()
                        if (rawFileOutputStream != null) {
                            try {
                                rawFileOutputStream.close()
                            } catch (e: IOException) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }
        }

        private fun createFile(extension: String): File {
            val sdf = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.US)
            val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)

            val dir = File(path, "/CraigCam2")
            if(!dir.isDirectory){
                dir.mkdirs()
            }
            return File(dir, "/IMG_${sdf.format(Date())}.$extension")
        }
    }



    companion object {
        private val ORIENTATIONS = SparseIntArray()
        init {
            ORIENTATIONS.append(Surface.ROTATION_0, 0)
            ORIENTATIONS.append(Surface.ROTATION_90, 90)
            ORIENTATIONS.append(Surface.ROTATION_180, 180)
            ORIENTATIONS.append(Surface.ROTATION_270, 270)
        }

        private const val IMAGE_FILE_LOCATION: String = "image_file_location"
        private const val ACTIVITY_START_CAMERA_APP = 0
        private const val STATE_PREVIEW = 0
        private const val STATE__WAIT_LOCK = 1
        private const val STATE__PICTURE_CAPTURED = 2
    }

    override fun onClick(v: View?) {
        TODO("Not yet implemented")
    }
}