package com.example.cargicamera2

import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.example.imagegallery.adapter.GalleryImageAdapter
import com.example.imagegallery.adapter.GalleryImageClickListener
import com.example.imagegallery.adapter.Image
import com.example.imagegallery.fragment.GalleryFullscreenFragment
import com.example.imagegallery.model.ImageGalleryUiModel
import com.example.imagegallery.service.MediaHelper
import kotlinx.android.synthetic.main.fragment_photobox.*
import java.io.File

class PhotoboxFragment : Fragment(), GalleryImageClickListener, View.OnClickListener{

    private val TAG = "PhotoboxFragment"
    // gallery column count
    private val SPAN_COUNT = 3

    private val imageList = ArrayList<Image>()
    lateinit var galleryAdapter: GalleryImageAdapter

    private var imageGalleryUiModelList:MutableMap<String, ArrayList<ImageGalleryUiModel>> = mutableMapOf()

    private var isMultiSelectable = false

    private var originalDistance = 0.0

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

        // init adapter
        galleryAdapter = GalleryImageAdapter(imageList)
        galleryAdapter.listener = this

        // init recyclerview
        recyclerView.layoutManager = GridLayoutManager(this.context, SPAN_COUNT)
        recyclerView.adapter = galleryAdapter

        imageGalleryUiModelList = MediaHelper.getImageGallery(this.context!!)
        if(imageGalleryUiModelList.isNotEmpty()){
            imageGalleryUiModelList.forEach{
                Log.i(TAG, it.key + ": " + it.value)
            }
        }else{
            Log.i(TAG, "imageGalleryUiModelList is empty")
        }

        // load images
        //loadImages()
        loadExtenalImages()
    }

    private fun loadExtenalImages(){
        if(imageList.size > 0) imageList.clear()

        var imageList:ArrayList<ImageGalleryUiModel> = imageGalleryUiModelList["CraigCam2"]!!
        imageList.forEach{
            this.imageList.add(Image(it.imageUri, it.imageUri.substring(it.imageUri.length-10, it.imageUri.length), false))
        }
        galleryAdapter.notifyDataSetChanged()
    }

    override fun onClick(position: Int) {
        // handle click of image

        if(isMultiSelectable){

        }else{
            val bundle = Bundle()
            bundle.putSerializable("images", imageList)
            bundle.putInt("position", position)
            Log.d(TAG, "image detail: ${imageList[position]}")


            val fragmentTransaction = fragmentManager!!.beginTransaction()
            val galleryFragment = GalleryFullscreenFragment()
            galleryFragment.setArguments(bundle)
            galleryFragment.show(fragmentTransaction, "gallery")
        }
    }

    override fun onClick(v: View?) {
        when(v!!.id){
            R.id.btnSelect -> {
                if(isMultiSelectable) {
                    isMultiSelectable = false
                    btnSelect.setImageResource(R.drawable.ic_select)
                }
                else{
                    isMultiSelectable = true
                    btnSelect.setImageResource(R.drawable.ic_cancel)
                }

                recyclerView.layoutManager = GridLayoutManager(this.context, SPAN_COUNT)
                galleryAdapter = GalleryImageAdapter(imageList)
                galleryAdapter.listener = this
                recyclerView.adapter = galleryAdapter

            }
            R.id.btn_camera ->{
                fragmentManager?.popBackStack()
            }
            R.id.btnInfo -> {

            }
            R.id.btn_download -> {

            }
            R.id.btn_share -> {

            }
            R.id.btn_delete_trash -> {
                val file = File(Environment.getExternalStorageDirectory().toString() + "/DCIM", "2020-04-09 14:20:04.jpeg")
                val deleted: Boolean = file.delete()
                Log.d(TAG, "File deleted: ${deleted}")
            }
        }
    }
}