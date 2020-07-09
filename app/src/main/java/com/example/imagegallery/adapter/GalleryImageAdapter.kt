package com.example.imagegallery.adapter

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.craigCam2.R
import com.example.imagegallery.helper.GlideApp
import com.example.imagegallery.helper.SquareLayout
import kotlinx.android.synthetic.main.activity_main.view.container
import kotlinx.android.synthetic.main.item_gallery_image.view.*
import org.w3c.dom.Text

class GalleryImageAdapter(private val itemList: List<Image>) : RecyclerView.Adapter<GalleryImageAdapter.ImagePickerViewHolder>() {

    private var context: Context? = null
    var listener: GalleryImageClickListener? = null

    private lateinit var onItemClickListener: OnItemClickListener


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImagePickerViewHolder {
        context = parent.context
        val view = LayoutInflater.from(parent.context).inflate(
            R.layout.item_gallery_image, parent,
            false)
        return ImagePickerViewHolder(view)
    }

    override fun getItemCount(): Int {
        return itemList.size
    }

//    override fun onBindViewHolder(holder: GalleryImageAdapter.ViewHolder, position: Int) {
//        holder.bind(position)
//        Log.i("onBindViewHolder", "position: $position")
//    }

    override fun onBindViewHolder(holder: ImagePickerViewHolder, position: Int) {
        val image = itemList[position]

        GlideApp.with(context!!)
            .load(image.imageUrl)
            .centerCrop()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .into(holder.image)

        if (image.isSelected)
            holder.container.setBackgroundColor(Color.parseColor("#3547f0"))
        else
            holder.container.setBackgroundResource(R.color.colorPrimaryDark)

        holder.title.text = image.title
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(position: Int) {
            val image = itemList[position]

            // load image
            GlideApp.with(context!!)
                .load(image.imageUrl)
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(itemView.ivGalleryImage)

            itemView.setOnClickListener {
                onItemClickListener.onItemClick(adapterPosition, it)
            }

            itemView.ivGalleryTitle.text = image.title
        }
    }

    inner class ImagePickerViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        var container: SquareLayout = itemView.container as SquareLayout
        var image: ImageView = itemView.ivGalleryImage as ImageView
        var title: TextView = itemView.ivGalleryTitle as TextView

        init {
            itemView.setOnClickListener {
                onItemClickListener.onItemClick(adapterPosition, it)
            }
        }
    }

    fun setOnItemClickListener(onItemClickListener: OnItemClickListener) {
        this.onItemClickListener = onItemClickListener
    }

    interface OnItemClickListener {
        fun onItemClick(position: Int, v: View)
    }
}