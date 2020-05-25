package com.trees.android.treelabeler

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class GalleryHolder(val imageView: ImageView) : RecyclerView.ViewHolder(imageView)

class GalleryImageAdapter: RecyclerView.Adapter<GalleryHolder>() {
    var data = listOf<String>()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun getItemCount(): Int {
        return data.size
    }

    override fun onBindViewHolder(holder: GalleryHolder, position: Int) {
        val item = data[position]

        // Calculate the size of image and scale it down to reduce memory usage
        val thumbnail = decodeSampledBitmapFromFile(item, 25, 25)
        holder.imageView.setImageBitmap(thumbnail)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GalleryHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val placeholder = layoutInflater.inflate(
            R.layout.gallery_grid_item, parent, false) as ImageView
        return GalleryHolder(placeholder)
    }

    // Helper method to calculate smaller sample size for image thumbnails
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        // Raw height and width of image
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {

            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    // Decode bitmap from image file
    private fun decodeSampledBitmapFromFile(
        file: String,
        reqWidth: Int,
        reqHeight: Int
    ): Bitmap {
        // First decode with inJustDecodeBounds=true to check dimensions
        return BitmapFactory.Options().run {
            inJustDecodeBounds = true
            BitmapFactory.decodeFile(file, this)

            // Calculate inSampleSize
            inSampleSize = calculateInSampleSize(this, reqWidth, reqHeight)

            // Decode bitmap with inSampleSize set
            inJustDecodeBounds = false

            BitmapFactory.decodeFile(file, this)
        }
    }
}
