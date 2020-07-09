package com.example.craigCam2.room

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.craigCam2.R
//import com.example.craigCam2.databinding.RecViewRowLayoutBinding
import com.example.craigCam2.databinding.RecViewRowLayoutBindingImpl
import kotlinx.android.synthetic.main.rec_view_row_layout.view.*


class HistoryAdapter : ListAdapter<History, HistoryAdapter.HistoryHolder>(DIFF_CALLBACK){
    companion object{
        private val DIFF_CALLBACK = object: DiffUtil.ItemCallback<History>() {
            override fun areItemsTheSame(oldItem: History, newItem: History): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: History, newItem: History): Boolean {
                return oldItem.contrast == newItem.contrast && oldItem.refreshRate == newItem.refreshRate &&
                        oldItem.colorTemperature == newItem.colorTemperature
            }
        }
    }

    private var listener: OnItemClickListener? = null

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): HistoryAdapter.HistoryHolder {
        val itemView: View = LayoutInflater.from(parent.context).inflate(R.layout.rec_view_row_layout, parent, false)
        return HistoryHolder(itemView)
    }

    override fun onBindViewHolder(holder: HistoryHolder, position: Int) {
        val currentHistory: History = getItem(position)

        holder.textViewDate.text = currentHistory.date
        holder.textViewContrast.text = currentHistory.contrast
        holder.textViewRefreshRate.text = currentHistory.refreshRate.toString()
        holder.textViewColorTemperature.text = currentHistory.colorTemperature
    }

    fun getHistoryAt(position: Int): History{
        return getItem(position)
    }

    inner class HistoryHolder(itemView: View): RecyclerView.ViewHolder(itemView){
        init{
            itemView.setOnClickListener{
                val position = adapterPosition
                if(position != RecyclerView.NO_POSITION){
                    listener?.onItemClick(getItem(position))
                }
            }
        }

        var textViewDate: TextView = itemView.date
        var textViewContrast: TextView = itemView.contrast
        var textViewRefreshRate: TextView = itemView.refresh_rate
        var textViewColorTemperature: TextView = itemView.color_temperature
    }

    interface OnItemClickListener{
        fun onItemClick(history: History)
    }

    fun setOnItemClickListener(listener: OnItemClickListener){
        this.listener = listener
    }
}