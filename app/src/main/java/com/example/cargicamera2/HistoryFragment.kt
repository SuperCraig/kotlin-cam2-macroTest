package com.example.cargicamera2

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.recyclerswipe.adapter.RecViewAdapter
import com.example.recyclerswipe.model.DataItem
import com.example.recyclerswipe.uiUtils.RecyclerItemTouchHelper
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.fragment_history.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class HistoryFragment : Fragment(), RecyclerItemTouchHelper.RecyclerItemTouchHelperListener{
    private var mDataItem = ArrayList<DataItem>()
    private var mAdapter: RecViewAdapter?= null

    private var sharePath = "no"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.fragment_history, container, false)
        return v
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mAdapter = RecViewAdapter(this.context!!, setDataItem(14))
        recycler_view.layoutManager = LinearLayoutManager(this.context)
        recycler_view.adapter = mAdapter

        val itemTouchHelperCallback = RecyclerItemTouchHelper(0, ItemTouchHelper.LEFT, this)
        ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(recycler_view)

        object: ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT){
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                TODO("Not yet implemented")
            }
        }

        btn_camera.setOnClickListener {
            fragmentManager?.popBackStack()
        }

        btn_share.setOnClickListener {
            takeScreenshot()
            if(sharePath != "no")
                share(sharePath)
        }

        btn_download.setOnClickListener {
            takeScreenshotOfView(it, it.width, it.height)
        }
    }

    private fun takeScreenshotOfView(view: View, width: Int, height: Int): Bitmap{
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bgDrawable = view.background
        if(bgDrawable != null){
            bgDrawable.draw(canvas)
        }else{
            canvas.drawColor(Color.WHITE)
        }
        view.draw(canvas)
        return bitmap
    }

    private fun getScreenShot(view: View): Bitmap {
        val returnedBitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(returnedBitmap)
        val bgDrawable = view.background
        if (bgDrawable != null) bgDrawable.draw(canvas)
        else canvas.drawColor(Color.WHITE)
        view.draw(canvas)
        return returnedBitmap
    }

    private fun takeScreenshot() {
        val now = Date()
        android.text.format.DateFormat.format("yyyy-MM-dd_hh:mm:ss", now)

        try {
            // image naming and path  to include sd card  appending name you choose for file
            val mPath = Environment.getExternalStorageDirectory().toString() + "/" + now + ".jpeg"

            // create bitmap screen capture
            val window = activity!!.window
            val v1 = window.decorView.rootView
            v1.isDrawingCacheEnabled = true
            val bitmap = Bitmap.createBitmap(v1.drawingCache)
            v1.isDrawingCacheEnabled = false

            val imageFile = File(mPath)

            val outputStream = FileOutputStream(imageFile)
            val quality = 100
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            outputStream.flush()
            outputStream.close()

            //setting screenshot in imageview
            val filePath = imageFile.path

            val ssbitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
            sharePath = filePath

        } catch (e: Throwable) {
            // Several error may come out with file handling or DOM
            e.printStackTrace()
        }

    }

    private fun share(sharePath: String) {
        try{
            Log.d("ffff", sharePath)
            val file = File(sharePath)
            val uri = Uri.fromFile(file)
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "image/*"
            intent.putExtra(Intent.EXTRA_STREAM, uri)
            startActivity(intent)
        }catch (e: Exception){
            e.printStackTrace()
        }
    }

    private fun setDataItem(size: Int): ArrayList<DataItem>{
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        val currentDateTime: String = dateFormat.format(Date()) // Find todays date

        for (i in 1..size){
            val dataItem = DataItem()
            dataItem.date = currentDateTime
            dataItem.title = "Title: " + i
            dataItem.contrast = 5000
            dataItem.refreshRate = 3840
            dataItem.colorTemperature = "Day White"
            mDataItem.add(dataItem)
        }
        return mDataItem
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int, position: Int) {
        if(viewHolder is RecViewAdapter.RecViewHolder){
            val name = mDataItem[viewHolder.adapterPosition].title

            val deleteItem = mDataItem[viewHolder.adapterPosition]
            val delteIndex = viewHolder.adapterPosition

            mAdapter?.removeItem(viewHolder.adapterPosition)

            var snackbar = Snackbar.make(coordinator_layout!!, name!! + " removed from history!", Snackbar.LENGTH_LONG)
            snackbar.setAction("UNDO"){
                mAdapter?.restoreItem(deleteItem, delteIndex)
            }
            snackbar.setActionTextColor(Color.YELLOW)
            snackbar.show()
        }
    }
}