package com.trees.android.treelabeler

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.trees.android.treelabeler.databinding.EditActivityBinding

class EditActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.edit_activity)

        val data = ArrayList<String>()
        val requestCode = 102

        // Get permission for the data
        when (PackageManager.PERMISSION_GRANTED) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) -> {
                // You can use the API that requires the permission.
                // Just get images from storage for now
                val projection = arrayOf(MediaStore.Images.ImageColumns.DATA)
                applicationContext.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    null
                )?.use {cursor ->
                    while (cursor.moveToNext()) {
                        val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.DATA)
                        data.add(cursor.getString(dataColumn))
                    }
                }
            }
            else -> {
                // You can directly ask for the permission.
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), requestCode)
            }
        }

        val index = this.intent.getIntExtra("CURRENT_INDEX", 0)

        val contentBinding: EditActivityBinding = DataBindingUtil.setContentView(
            this, R.layout.edit_activity)

        contentBinding.treeEditImage.labelRectangles.add(LabelRectangle(200f,200f,400f, 400f))
        if (data != null) {
            val treeImage = decodeSampledBitmapFromFile(data[index])
            contentBinding.treeEditImage.treeImage = treeImage
        }

        val fab: FloatingActionButton = findViewById(R.id.edit_fab)
        val canvasView: TreeEditorCanvasView = findViewById(R.id.tree_edit_image)
        fab.setOnClickListener { view ->
            canvasView.onClickAddFab(fab, view)
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
