package com.trees.android.treelabeler

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.databinding.DataBindingUtil
import com.trees.android.treelabeler.databinding.EditActivityBinding

class EditActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.edit_activity)

        val data = this.intent.getStringArrayListExtra("IMAGES")
        val index = this.intent.getIntExtra("CURRENT_INDEX", 0)

        val contentBinding: EditActivityBinding = DataBindingUtil.setContentView(
            this, R.layout.edit_activity)

        contentBinding.treeEditImage.labelRectangles.add(LabelRectangle(200f,200f,400f, 400f))
        if (data != null) {
            contentBinding.treeEditImage.treeImage = decodeSampledBitmapFromFile(data[index])
        }
    }

    // Decode bitmap from image file
    private fun decodeSampledBitmapFromFile(
        file: String
    ): Bitmap {
        // First decode with inJustDecodeBounds=true to check dimensions
        return BitmapFactory.Options().run {
            BitmapFactory.decodeFile(file, this)
        }
    }
}
