package com.example.cargicamera2

import android.app.AlertDialog
import android.content.DialogInterface
import android.graphics.Color
import android.media.ExifInterface
import android.media.MediaScannerConnection
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.Log.d
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.example.imagegallery.adapter.GalleryImageAdapter
import com.example.imagegallery.adapter.GalleryImageClickListener
import com.example.imagegallery.adapter.Image
import com.example.imagegallery.fragment.GalleryFullscreenFragment
import com.example.imagegallery.model.ImageGalleryUiModel
import com.example.imagegallery.service.MediaHelper
import kotlinx.android.synthetic.main.activity_opencv_main.*
import kotlinx.android.synthetic.main.fragment_photobox.*
import java.io.File

class PhotoboxFragment : Fragment(), GalleryImageAdapter.OnItemClickListener, View.OnClickListener{

    private val TAG = "PhotoboxFragment"
    // gallery column count
    private val SPAN_COUNT = 3

    private val imageList = ArrayList<Image>()
    lateinit var galleryAdapter: GalleryImageAdapter

    private var imageGalleryUiModelList:MutableMap<String, ArrayList<ImageGalleryUiModel>> = mutableMapOf()

    private var isMultiSelectable = false

    private var originalDistance = 0.0

    private var currentPosition: Int? = null
    private var imageUrlList: ArrayList<String> = ArrayList<String>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.fragment_photobox, container, false)
        return v
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnSelect.setOnClickListener(this)
        btn_camera.setOnClickListener(this)
        btnInfo.setOnClickListener(this)
        btn_download.setOnClickListener(this)
        btn_share.setOnClickListener(this)
        btn_delete_trash.setOnClickListener(this)

//        imageGalleryUiModelList = MediaHelper.getImageGallery(this.context!!)

        // init adapter
//        galleryAdapter = GalleryImageAdapter(imageList)
//        galleryAdapter.listener = this

        // load images
        loadExternalImages()

        // init recyclerview
        recyclerView.layoutManager = GridLayoutManager(this.context, SPAN_COUNT)
        recyclerView.adapter = galleryAdapter

    }

    private fun loadExternalImages(){
        imageGalleryUiModelList = MediaHelper.getImageGallery(this.context!!)

        if(imageList.size > 0) imageList.clear()

        val imageList: ArrayList<ImageGalleryUiModel>? = imageGalleryUiModelList["CraigCam2"]
        imageList?.forEach{
            if (!it.imageUri.contains("IMG_")) {
                val strings = it.imageUri.split('/')
                val displayTitle = "${strings[strings.size - 1]}".replace("$", "")
//            this.imageList.add(Image(it.imageUri, it.imageUri.substring(it.imageUri.length-10, it.imageUri.length), false))
                this.imageList.add(Image(it.imageUri, displayTitle, false))
            }
        }

        this.imageList.reverse()        //order by newest one
        galleryAdapter = GalleryImageAdapter(this.imageList)
        galleryAdapter.setOnItemClickListener(this)
        galleryAdapter.notifyDataSetChanged()
    }

    override fun onClick(v: View?) {
        when(v!!.id){
            R.id.btnSelect -> {
                if(isMultiSelectable) {
                    isMultiSelectable = false
                    imageUrlList.clear()
                    btnSelect.setImageResource(R.drawable.ic_select)
                }
                else{
                    isMultiSelectable = true
                    btnSelect.setImageResource(R.drawable.ic_cancel)
                }

                imageList.forEach {
                    it.isSelected = false
                }
                galleryAdapter.notifyDataSetChanged()
            }
            R.id.btn_camera ->{
                val fragmentManager = activity!!.supportFragmentManager
                fragmentManager.popBackStack()
            }
            R.id.btnInfo -> {       //show picture information
                if (isMultiSelectable) {
                    imageUrlList.forEach {imageUrl ->
                        if(imageUrl.contains(".dng")) return
                        try {
                            val exif = ExifInterface(imageUrl)
                            val aperture = exif.getAttribute(ExifInterface.TAG_APERTURE_VALUE)
                            val exposure = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME).toDouble()
                            val iso = exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)
                            val strExposure = "1/${"%.1f".format(1.toDouble() / exposure)}s"
                            Log.i(TAG, "TAG_APERTURE_VALUE: $aperture, TAG_EXPOSURE_TIME: $strExposure, TAG_ISO_SPEED_RATINGS: $iso")
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } else {
                    if (currentPosition != null) {
                        if(imageList[currentPosition!!].imageUrl.contains(".dng")) return
                        Toast.makeText(this.context, "Information clicked", Toast.LENGTH_SHORT).show()
                        try {
                            val exif = ExifInterface(imageList[currentPosition!!].imageUrl)
                            val aperture = exif.getAttribute(ExifInterface.TAG_APERTURE_VALUE)
                            val exposure = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME).toDouble()
                            val iso = exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)
                            val strExposure = "1/${"%.1f".format(1.toDouble() / exposure)}s"
                            Log.i(TAG, "TAG_APERTURE_VALUE: $aperture, TAG_EXPOSURE_TIME: $strExposure, TAG_ISO_SPEED_RATINGS: $iso")
                            Toast.makeText(this.context, "TAG_APERTURE_VALUE: $aperture, TAG_EXPOSURE_TIME: $strExposure, TAG_ISO_SPEED_RATINGS: $iso",
                                Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                }
            }
            R.id.btn_download -> {      //copy picture to phone album folder
                val regex = """(.+)/(.+)\.(.+)""".toRegex()

                if (isMultiSelectable) {
                    imageUrlList.forEach {imageUrl ->
                        val matchResult = regex.matchEntire(imageUrl)
                        val (directory, fileName, extension) = matchResult!!.destructured

                        File(imageUrl).copyTo(
                            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                                "Camera/$fileName.$extension"),
                            true)
                    }
                } else {
                    if (currentPosition != null) {
                        val matchResult = regex.matchEntire(imageList[currentPosition!!].imageUrl)
                        val (directory, fileName, extension) = matchResult!!.destructured

                        File(imageList[currentPosition!!].imageUrl).copyTo(
                            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                                "Camera/$fileName.$extension"),
                            true)

                        currentPosition = null
                    }
                }

                val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                MediaScannerConnection.scanFile(context, arrayOf(File(path, "Camera").toString()), arrayOf("image/jpeg/dng")) { p, _ ->
                    Log.i(TAG, "onScanCompleted : $p")
                }

                isMultiSelectable = false
                imageUrlList.clear()
                imageList.forEach {
                    it.isSelected = false
                }
                galleryAdapter.notifyDataSetChanged()
                btnSelect.setImageResource(R.drawable.ic_select)
            }
            R.id.btn_share -> {     //copy picture to application folder

            }
            R.id.btn_delete_trash -> {      //delete select picture
                showDialog()

                if (isMultiSelectable) {
                    imageUrlList.forEach {imageUrl ->
                        try {
                            if (File(imageUrl).exists()) {
                                context?.contentResolver?.delete(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                    MediaStore.Images.ImageColumns.DATA + "=?", arrayOf(imageUrl)
                                )
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } else {
                    if (currentPosition != null) {
                        if (currentPosition!! < imageList.size && File(imageList[currentPosition!!].imageUrl).exists()) {
                            context?.contentResolver?.delete(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                MediaStore.Images.ImageColumns.DATA + "=?", arrayOf(imageList[currentPosition!!].imageUrl)
                            )
                            imageList.remove(imageList[currentPosition!!])
                        }
                        currentPosition = null
                    }
                }

                val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)

                val dir = File(path, "/CraigCam2")
                MediaScannerConnection.scanFile(context, arrayOf(dir.toString()), arrayOf("image/jpeg", "image/dng")) { p, _ ->
                    Log.i(TAG, "onScanCompleted : $p")
                }

                imageUrlList.clear()
                isMultiSelectable = false
                btnSelect.setImageResource(R.drawable.ic_select)
                loadExternalImages()
                recyclerView.adapter = galleryAdapter
            }
        }
    }

    private fun showDialog() {
        lateinit var dialog: AlertDialog

        var  builder = AlertDialog.Builder(this.context, AlertDialog.THEME_HOLO_DARK)

        builder.setTitle("Delete Picture")

        builder.setMessage("Do you want to delete?")

        val dialogClickListener = DialogInterface.OnClickListener { _, which ->
            when (which) {
                DialogInterface.BUTTON_POSITIVE -> {

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

    override fun onItemClick(position: Int, v: View) {
        Log.i(TAG, "Clicked position: $position")

        // handle click of image
        if(isMultiSelectable){
            if (imageList[position].imageUrl in imageUrlList && position < imageList.size) {
                imageUrlList.remove(imageList[position].imageUrl)
                imageList[position].isSelected = false
                galleryAdapter.notifyDataSetChanged()
            }
            else if (position < imageList.size) {
                imageUrlList.add(imageList[position].imageUrl)
                imageList[position].isSelected = true
                galleryAdapter.notifyDataSetChanged()
            }
        }else{
            if (currentPosition != null) {
                if (imageList[currentPosition!!].isSelected)
                    imageList[currentPosition!!].isSelected = false
                galleryAdapter.notifyDataSetChanged()
            }

            currentPosition = position

            imageList[position].isSelected = true
            galleryAdapter.notifyDataSetChanged()

            val bundle = Bundle()
            bundle.putSerializable("images", imageList)
            bundle.putInt("position", position)
            d(TAG, "image detail: ${imageList[position]}")

            val fragmentManager = activity!!.supportFragmentManager
            val fragmentTransaction = fragmentManager.beginTransaction()
            val galleryFragment = GalleryFullscreenFragment()
            galleryFragment.arguments = bundle
            galleryFragment.show(fragmentTransaction, "gallery")
        }
    }
}