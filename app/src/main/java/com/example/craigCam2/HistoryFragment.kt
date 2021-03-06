package com.example.craigCam2

import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.craigCam2.room.History
import com.example.craigCam2.room.HistoryAdapter
import com.example.craigCam2.room.SingleHistoryAdapter
import com.example.craigCam2.room.HistoryViewModel
import com.example.craigCam2.room.RoomRecyclerItemTouchHelper
import com.example.craigCam2.room.RoomRecyclerItemTouchHelper_SingleHIstory
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

//class HistoryFragment : Fragment(), RoomRecyclerItemTouchHelper.RecyclerItemTouchHelperListener{
class HistoryFragment: Fragment(), RoomRecyclerItemTouchHelper_SingleHIstory.RecyclerItemTouchHelperListener{
    private var mDataItem = ArrayList<DataItem>()
    private var mAdapter: RecViewAdapter?= null

    private var sharePath = "no"

    private lateinit var historyViewModel: HistoryViewModel
    private lateinit var adapter: HistoryAdapter
    private lateinit var singleAdapter: SingleHistoryAdapter

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

        adapter = HistoryAdapter()
        singleAdapter = SingleHistoryAdapter()      //202000611 Craig for single history data
        historyViewModel = ViewModelProvider(this).get(HistoryViewModel::class.java)
        historyViewModel.getAllNotes().observe(this.viewLifecycleOwner, androidx.lifecycle.Observer { it ->
            adapter.submitList(it)
            singleAdapter.submitList(it)
//            it.forEach {
//                Log.d("Contrast: ", it.contrast.toString())
//                Log.d("Refresh Rate: ", it.refreshRate.toString())
//                Log.d("Color Temperature: ", it.colorTemperature.toString())
//            }
        })


//        mAdapter = RecViewAdapter(this.context!!, setDataItem(historyViewModel.getAllNotes()))        //old version itemList 20200406
        recycler_view.layoutManager = LinearLayoutManager(this.context)
        recycler_view.setHasFixedSize(true)     //20200406 Craig

//        recycler_view.adapter = adapter
        recycler_view.adapter = singleAdapter       //20200611 Craig

//        val itemTouchHelperCallback = RoomRecyclerItemTouchHelper(0, ItemTouchHelper.LEFT, this)
        val itemTouchHelperCallback = RoomRecyclerItemTouchHelper_SingleHIstory(0, ItemTouchHelper.LEFT, this)
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

            }
        }

        btn_camera.setOnClickListener {
            val fragmentManager = activity!!.supportFragmentManager
            fragmentManager.popBackStack()
        }

        btn_share.setOnClickListener {
            takeScreenshot()
            if(sharePath != "no")
                share(sharePath)
        }

        btn_download.setOnClickListener {
            takeScreenshot()

            //screenshot effect 20200611
            pnlFlash.visibility = View.VISIBLE
            var fade: AlphaAnimation = AlphaAnimation(1.0f, 0.0f)
            fade.duration = 50
            fade.setAnimationListener(object: Animation.AnimationListener {
                override fun onAnimationRepeat(animation: Animation?) {
                    TODO("Not yet implemented")
                }
                override fun onAnimationEnd(animation: Animation?) {
                    pnlFlash.visibility = View.INVISIBLE
                }
                override fun onAnimationStart(animation: Animation?) {
                    pnlFlash.visibility = View.VISIBLE
                }
            })
            pnlFlash.animation = fade
        }

        btn_delete_all.setOnClickListener {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
            val currentDateTime: String = dateFormat.format(Date()) // Find todays date
//            historyViewModel.insert(History(currentDateTime,17000, 20020, "Warm White"))

            showDialog(historyViewModel)
//            historyViewModel.deleteAllHistories()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
    }

    private fun takeScreenshot() {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        val currentDateTime: String = dateFormat.format(Date()) // Find todays date

        try {
            // image naming and path  to include sd card  appending name you choose for file
            val mPath = Environment.getExternalStorageDirectory().toString() + "/DCIM/" + currentDateTime + ".jpeg"

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

            sharePath = currentDateTime + ".jpeg"

        } catch (e: Throwable) {
            // Several error may come out with file handling or DOM
            e.printStackTrace()
        }

    }

    private fun share(sharePath: String) {
        try{
            Log.d("HistoryFragment: ", sharePath)
            val tmpFile = File(Environment.getExternalStorageDirectory().toString(), "/DCIM/"+sharePath)
            val uri = FileProvider.getUriForFile(this.context!!, "com.example.craigCam2", tmpFile)
            Log.d("Uri: ", uri.toString())
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "image/*"
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.putExtra(Intent.EXTRA_STREAM, uri)
            startActivity(intent)
        }catch (e: Exception){
            e.printStackTrace()
        }
    }

    private fun setDataItem(histories: LiveData<List<History>>): ArrayList<DataItem>{
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        val currentDateTime: String = dateFormat.format(Date()) // Find todays date

        val listHistory = histories.value

        for (i in 1..listHistory!!.size){
            val dataItem = DataItem()
            dataItem.date = currentDateTime
            dataItem.title = listHistory[i].id.toString()
            dataItem.contrast = listHistory[i].contrast
            dataItem.refreshRate = listHistory[i].refreshRate
            dataItem.colorTemperature = listHistory[i].colorTemperature
            mDataItem.add(dataItem)
        }
        return mDataItem
    }

    private fun showDialog(historyViewModel: HistoryViewModel) {
        lateinit var dialog: AlertDialog

        var  builder = AlertDialog.Builder(this.context, AlertDialog.THEME_HOLO_DARK)

        builder.setTitle("Clear All Histories")

        builder.setMessage("Are you sure?")

        val dialogClickListener = DialogInterface.OnClickListener { _, which ->
            when (which) {
                DialogInterface.BUTTON_POSITIVE -> {
                    historyViewModel.deleteAllHistories()
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

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int, position: Int) {
//        val history: History = adapter.getHistoryAt(viewHolder.adapterPosition)
        val history: History = singleAdapter.getHistoryAt(viewHolder.adapterPosition)       //20200611 Craig
        historyViewModel.delete(history)
        Toast.makeText(context, "Note Deleted", Toast.LENGTH_SHORT).show()

//        val name = history.id
//        val snackbar = Snackbar.make(coordinator_layout!!, name!!.toString() + " removed from history!", Snackbar.LENGTH_LONG)
        val snackbar = Snackbar.make(coordinator_layout!!, "History removed from history!", Snackbar.LENGTH_LONG)
        snackbar.setAction("UNDO"){
            historyViewModel.insert(history)
        }
        snackbar.setActionTextColor(Color.YELLOW)
        snackbar.show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
    }
}