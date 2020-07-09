package com.example.craigCam2.room

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.craigCam2.Camera2BasicFragment
import com.example.craigCam2.R

import kotlinx.android.synthetic.main.rec_view_row_single_layout.view.*


class SingleHistoryAdapter: ListAdapter<History, SingleHistoryAdapter.HistoryHolder>(DIFF_CALLBACK) {
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
    ): HistoryHolder {
        val itemView: View = LayoutInflater.from(parent.context).inflate(R.layout.rec_view_row_single_layout, parent, false)
        return HistoryHolder(itemView)
    }

    override fun onBindViewHolder(holder: HistoryHolder, position: Int) {
        val currentHistory: History = getItem(position)

        holder.textViewDate.text = currentHistory.date
        if (currentHistory.contrast != "0") {
            holder.textViewHistory.text = "Contrast"
            holder.textViewData.text = currentHistory.contrast
        }else if (currentHistory.refreshRate != "0") {
            holder.textViewHistory.text = "Refresh Rate"
            holder.textViewData.text = currentHistory.refreshRate.toString()
        }else if (currentHistory.colorTemperature != Camera2BasicFragment.Companion.ColorTemperature.None.name) {
            holder.textViewHistory.text = "Color Temperature"
            holder.textViewData.text = currentHistory.colorTemperature
        }
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
        var textViewHistory: TextView = itemView.txt_history
        var textViewData: TextView = itemView.txt_data
    }

    interface OnItemClickListener{
        fun onItemClick(history: History)
    }

    fun setOnItemClickListener(listener: OnItemClickListener){
        this.listener = listener
    }
}