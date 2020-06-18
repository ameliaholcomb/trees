package com.trees.android.treelabeler

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.GridLayoutManager
import com.trees.android.treelabeler.databinding.GalleryActivityBinding
import kotlinx.android.synthetic.main.gallery_activity.*
import java.io.File

class GalleryActivity : AppCompatActivity() {
    val requestCode = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.gallery_activity)
        val adapter = GalleryImageAdapter()
        val contentBinding: GalleryActivityBinding = DataBindingUtil.setContentView(
            this, R.layout.gallery_activity)

        // initializing grid
        val manager = GridLayoutManager(this, 4)
        contentBinding.rvGallery.layoutManager = manager
        contentBinding.rvGallery.adapter = adapter

        // path for storing data
        val file = getAlbumStorageDir(this, "/Trees")

        val dataList = ArrayList<String>()

        //Get permission for storage
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
                        dataList.add(cursor.getString(dataColumn))
                    }
                }
            }
            else -> {
                // You can directly ask for the permission.
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), requestCode)
            }
        }

        adapter.data = dataList

        setSupportActionBar(toolbar)

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_scrolling, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.

        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    //Check if external storage is available to read
    fun isExternalStorageReadable(): Boolean {
        return Environment.getExternalStorageState() in
                setOf(Environment.MEDIA_MOUNTED, Environment.MEDIA_MOUNTED_READ_ONLY)
    }

    private fun getAlbumStorageDir(context: Context, albumName: String): File? {
        // Get the pictures directory that's inside the app-specific directory on
        // external storage.
        val file = File(context.getExternalFilesDir(
            Environment.DIRECTORY_PICTURES), albumName)
        if (!file.mkdirs()) {
            Log.e("IOError", "Directory not created")
        }
        return file
    }
}
