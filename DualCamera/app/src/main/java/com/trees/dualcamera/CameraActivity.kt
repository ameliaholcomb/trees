package com.trees.dualcamera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.trees.dualcamera.helpers.CameraPermissionHelper
import kotlinx.android.synthetic.main.activity_camera.*
import java.util.concurrent.Executor

const val TAG = "Amelia"
const val TOF_WIDTH = 240
const val TOF_HEIGHT = 180
const val NORM_WIDTH = 640
const val NORM_HEIGHT = 480
data class DualCamera(val logicalId: String, val physicalNormId: String, val physicalTofId: String)
typealias DualCameraOutputs =
        Triple<MutableList<Surface>?, MutableList<Surface>?, MutableList<Surface>?>


class CameraActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        startDualCameraCapture()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            Toast.makeText(
                applicationContext,
                "Camera permission is needed to run this application",
                Toast.LENGTH_LONG
            )
                .show()
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) { // Permission denied with checking "Do not ask again".
                CameraPermissionHelper.launchPermissionSettings(this)
            }
            finish()
        }

    }
    // second fun
    private fun findDualCamera(manager: CameraManager): DualCamera {
        // Find a logical camera with the appropriate capabilities
        val logicalCamera = manager.cameraIdList.map {
            Pair(manager.getCameraCharacteristics(it), it)
        }.filter {
            // Filter by logical cameras
            it.first.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)!!.contains(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA)
        }.filter {
            // Filter by cameras that can produce depth output
            it.first.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)!!.contains(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT)
        }[0] // Choose the first logical camera that meets our requirements

        // Find an RGB and ToF physical camera within this logical camera
        // HuaWei does not appropriately identify the ToF camera, but through experience we happen to
        // know that it is camera #4. This would change with another phone.
//        val physicalTofId = "4"
        val physicalTofId = "3"


        // For now we arbitrarily choose the first physical RGB camera within this logical camera
        // that meets our requirements.
        val physicalNormId = logicalCamera.first.physicalCameraIds.first()

        // A bit of logging about this camera's output formats/configuration:
        var map = manager.getCameraCharacteristics(physicalNormId).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        Log.i(TAG, "Camera $physicalNormId")
        Log.i(TAG, "map: " + map?.toString())
        Log.i(TAG, "map: " + map?.outputFormats?.contentToString())

        map = manager.getCameraCharacteristics(logicalCamera.second).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        Log.i(TAG, "Camera " + logicalCamera.second)
        Log.i(TAG, "map2: " + map?.toString())
        Log.i(TAG, "map2: " + map?.outputFormats?.contentToString())
        Log.i(TAG, "map2: " + map?.getOutputSizes(ImageFormat.DEPTH16)?.contentToString())

        // TODO: Output sizes are null
        //       Double check that camera 4 is used in the other app
        map = manager.getCameraCharacteristics(physicalTofId).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        Log.i(TAG, "Camera $physicalTofId")
        Log.i(TAG, "map3: " + map?.toString())
        Log.i(TAG, "map3: " + map?.outputFormats?.contentToString())
        Log.i(TAG, "map3: " + map?.getOutputSizes(ImageFormat.DEPTH16)?.contentToString())

        // Custom struct
        return DualCamera(logicalId = logicalCamera.second,
            physicalNormId = physicalNormId, physicalTofId = physicalTofId)
    }

    @SuppressLint("MissingPermission")
    fun openDualCamera(
        cameraManager: CameraManager,
        dualCamera: DualCamera,
        executor: Executor = AsyncTask.SERIAL_EXECUTOR,
        callback: (CameraDevice) -> Unit) {

        Log.i(TAG, "Opening dual camera")
        if(!CameraPermissionHelper.hasCameraPermission(this)) {
            CameraPermissionHelper.requestCameraPermission(this)
            return
        }
        cameraManager.openCamera(dualCamera.logicalId, executor, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) = callback(device)
            override fun onDisconnected(camera: CameraDevice) {
                Log.w(TAG, "Camera device ID " + camera.id + " disconnected")
                camera.close()
            }
            override fun onError(camera: CameraDevice, error: Int) {
                Log.e(TAG, "Camera device ID " + camera.id + " error " + error)
                camera.close()
//                this.cameraDevice = null
                // Fatal error. Quit application.
                finish()
            }
        })
    }
    // third fun
    fun createDualCameraSession(cameraManager: CameraManager,
                                dualCamera: DualCamera,
                                targets: DualCameraOutputs,
                                executor: Executor = AsyncTask.SERIAL_EXECUTOR,
                                callback: (CameraCaptureSession) -> Unit) {

        Log.i(TAG, "Creating dual camera sesh")
//        val b = SurfaceUtils.getSurfaceFormat(targets.third!!.first())
        val a = OutputConfiguration(targets.third!!.first())
        // Create 3 sets of output configurations: one for the logical camera, and
        // one for each of the physical cameras.
        val outputConfigsLogical = targets.first?.map { OutputConfiguration(it) }

        val outputConfigsPhysicalNorm = targets.second?.map {
            OutputConfiguration(it).apply { setPhysicalCameraId(dualCamera.physicalNormId) } }
        val outputConfigsPhysicalTof = targets.third?.map {
            OutputConfiguration(it).apply { setPhysicalCameraId(dualCamera.physicalTofId) } }

        // Put all the output configurations into a single flat array
        val outputConfigsAll = arrayOf(
            outputConfigsLogical, outputConfigsPhysicalNorm, outputConfigsPhysicalTof)
            .filterNotNull().flatMap { it }

        // Instantiate a session configuration that can be used to create a session
        val sessionConfiguration = SessionConfiguration(SessionConfiguration.SESSION_REGULAR,
            outputConfigsAll, executor, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) = callback(session)
                override fun onConfigureFailed(session: CameraCaptureSession) = session.device.close()
            })

        // Open the logical camera
        openDualCamera(cameraManager, dualCamera, executor = executor) {

            Log.i(TAG, "Creating capture sesh")
            // Finally create the session and return via callback
            it.createCaptureSession(sessionConfiguration)
        }
    }

    // first fun
    fun startDualCameraCapture() {
        val cameraManager : CameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        // TODO: Must make sure to close the readers!
        val imReaderNorm: ImageReader = ImageReader.newInstance(
            NORM_WIDTH, NORM_HEIGHT, ImageFormat.YUV_420_888, 2)
        // TODO: Depth format not prop correctly
        val imReaderToF: ImageReader = ImageReader.newInstance(
            TOF_WIDTH, TOF_HEIGHT, ImageFormat.DEPTH16, 2)
        val dualCamera: DualCamera = findDualCamera(cameraManager)
        val outputTargets = DualCameraOutputs(
            null, mutableListOf(imReaderNorm.surface), mutableListOf(imReaderToF.surface))
        imReaderToF.setOnImageAvailableListener(onImageAvailableListener(), null)
        imReaderNorm.setOnImageAvailableListener(onImageAvailableListener(), null)

        // Here we open the logical camera, configure the outputs and create a session
        createDualCameraSession(cameraManager, dualCamera, targets = outputTargets) { session ->
            // Create a single request which will have one target for each physical camera
            // NOTE: Each target will only receive frames from its associated physical camera
            val requestTemplate = CameraDevice.TEMPLATE_PREVIEW
            val captureRequest = session.device.createCaptureRequest(requestTemplate).apply {
                arrayOf(imReaderNorm.surface, imReaderToF.surface).forEach { addTarget(it) }
            }.build()

            Log.i(TAG, "Setting capture request")

            // Set the sticky request for the session and we are done
            session.setRepeatingRequest(captureRequest, null, null)
        }
    }
}

class onImageAvailableListener : ImageReader.OnImageAvailableListener {

    override fun onImageAvailable(reader: ImageReader) {
        Log.i(TAG, "Got an image")
        val image = reader.acquireLatestImage()
        image.close()

    }
}