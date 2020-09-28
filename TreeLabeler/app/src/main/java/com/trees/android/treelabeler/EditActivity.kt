package com.trees.android.treelabeler

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.LifecycleOwner
import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.trees.android.treelabeler.databinding.EditActivityBinding
import java.io.File
import java.io.FileNotFoundException


class EditActivity : AppCompatActivity(), LifecycleOwner {
    var contentBinding: EditActivityBinding? = null

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

        contentBinding = DataBindingUtil.setContentView(
            this, R.layout.edit_activity)

        if (data != null) {
            val treeImage = decodeSampledBitmapFromFile(data[index])
            contentBinding?.treeEditImage?.treeImage = treeImage
            val imagePath = data[index].split("/")
            val imageFileName = imagePath.last()
            val csvFileName = imageFileName.replace(".jpg", ".csv")
            contentBinding?.treeEditImage?.csvFileName = csvFileName
            val file = File(applicationContext.filesDir, csvFileName)
            try {
                val rows: List<Map<String, String>> = csvReader().readAllWithHeader(file)
                for (row in rows) {
                    val left = row["left"]?.toFloatOrNull()
                    val right = row["right"]?.toFloatOrNull()
                    val top = row["top"]?.toFloatOrNull()
                    val bottom = row["bottom"]?.toFloatOrNull()
                    if ((left != null) and (right != null) and (top != null) and (bottom != null)) {
                        contentBinding?.treeEditImage?.labelRectangles?.add(LabelRectangle(left!!,top!!,right!!,bottom!!))
                    }
                }
            } catch (e: FileNotFoundException) { }
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

    override fun onDestroy() {
        val rectangles = contentBinding?.treeEditImage?.labelRectangles
        val csvFileName = contentBinding?.treeEditImage?.csvFileName
        val file = File(applicationContext.filesDir, csvFileName!!)
        if (rectangles != null && rectangles.count() > 0) {
            var rows = mutableListOf(listOf("left", "top", "right", "bottom"))
            for (rectangle in rectangles) {
                val row = listOf(rectangle.left.toString(), rectangle.top.toString(), rectangle.right.toString(), rectangle.bottom.toString())
                rows.add(row)
            }
            csvWriter().writeAll(rows, file)
        }
        super.onDestroy()
    }
}
