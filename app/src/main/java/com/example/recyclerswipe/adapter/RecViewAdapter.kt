package com.example.recyclerswipe.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.RatingBar
import android.widget.TextView
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.cargicamera2.R
import com.example.cargicamera2.databinding.RecViewRowLayoutBinding
import com.example.recyclerswipe.model.DataItem

class RecViewAdapter(private val mContext: Context, private val mDataItemList: ArrayList<DataItem>): RecyclerView.Adapter<RecViewAdapter.RecViewHolder>(){
    private var mBinding : RecViewRowLayoutBinding ?= null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecViewHolder {
        mBinding = DataBindingUtil.inflate(LayoutInflater.from(mContext), R.layout.rec_view_row_layout, parent, false)
        return RecViewHolder(mBinding!!)
    }

    override fun onBindViewHolder(holder: RecViewHolder, position: Int) {
        holder.itemView.findViewById<TextView>(R.id.date).text = mDataItemList[position].date
        holder.itemView.findViewById<TextView>(R.id.contrast).text = mDataItemList[position].contrast.toString()
        holder.itemView.findViewById<TextView>(R.id.refresh_rate).text = mDataItemList[position].refreshRate.toString() + "Hz"
        holder.itemView.findViewById<TextView>(R.id.color_temperature).text = mDataItemList[position].colorTemperature
//        holder.itemView.findViewById<TextView>(R.id.date_text).text = mDataItemList[position].date
    }


    override fun getItemCount(): Int {
        return mDataItemList.size
    }

    fun removeItem(position: Int){
        mDataItemList.removeAt(position)
        notifyItemRemoved(position)
    }

    fun restoreItem(item: DataItem, position: Int){
        mDataItemList.add(position, item)
        notifyItemInserted(position)
    }

    inner class RecViewHolder(mBinding: RecViewRowLayoutBinding):RecyclerView.ViewHolder(mBinding.root)
}