/**
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Simple app to demonstrate CameraX Video capturing with Recorder ( to local files ), with the
 * following simple control follow:
 *   - user starts capture.
 *   - this app disables all UI selections.
 *   - this app enables capture run-time UI (pause/resume/stop).
 *   - user controls recording with run-time UI, eventually tap "stop" to end.
 *   - this app informs CameraX recording to stop with recording.stop() (or recording.close()).
 *   - CameraX notify this app that the recording is indeed stopped, with the Finalize event.
 *   - this app starts VideoViewer fragment to view the captured result.
*/

package com.example.android.camerax.video.fragments

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.Image
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.concurrent.futures.await
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.whenCreated
import androidx.navigation.NavController
import androidx.navigation.Navigation
import com.example.android.camerax.video.R
import com.example.android.camerax.video.apriltag
import com.example.android.camerax.video.databinding.FragmentCaptureBinding
import com.example.android.camerax.video.extensions.getAspectRatio
import com.example.android.camerax.video.extensions.getAspectRatioString
import com.example.android.camerax.video.extensions.getNameString
import edu.wpi.first.math.geometry.Pose3d
import edu.wpi.first.math.geometry.Transform3d
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID


class CaptureFragment : Fragment() {

    // UI with ViewBinding
    private var _captureViewBinding: FragmentCaptureBinding? = null
    private val captureViewBinding get() = _captureViewBinding!!
    private val captureLiveStatus = MutableLiveData<String>()

    /** Host's navigation controller */
    private val navController: NavController by lazy {
        Navigation.findNavController(requireActivity(), R.id.fragment_container)
    }

    private val cameraCapabilities = mutableListOf<CameraCapability>()

    // Camera UI  states and inputs
    enum class UiState {
        IDLE,       // Not recording, all UI controls are active.
        RECORDING,  // Camera is recording, only display Pause/Resume & Stop button.
        FINALIZED,  // Recording just completes, disable all RECORDING UI controls.
        RECOVERY    // For future use.
    }
    private var cameraIndex = 0
    private var qualityIndex = DEFAULT_QUALITY_IDX
    private var audioEnabled = false

    private val mainThreadExecutor by lazy { ContextCompat.getMainExecutor(requireContext()) }
    private var enumerationDeferred:Deferred<Unit>? = null

    // main cameraX capture functions
    /**
     *   Always bind preview + video capture use case combinations in this sample
     *   (VideoCapture can work on its own). The function should always execute on
     *   the main thread.
     */


    @SuppressLint("UnsafeOptInUsageError")
    private fun bindVisionDetection(): ImageAnalysis {
        val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(requireContext())) { image: ImageProxy ->
            val pixelArray = imageToPixelArray(image.image!!)
            var ids = apriltag.apriltagMap.keys.toIntArray();
            var result = apriltag.getApriltagResult(pixelArray, image.width, image.height, ids);
            val camPoseTextView = captureViewBinding.textView2
            if(result.isTagDetected){
                var camToTarget = result.camToTargetPacket?.getTransform3d();
                var camPose = camToTarget?.let { apriltag.apriltagMap.get(result.id)?.let { it1 -> calcCamPose(it, it1) } };
                if(camPose !=null){
                    Log.d("Cam Pose", poseToString(camPose));
                    val newText = poseToString(camPose);
                    camPoseTextView?.text = newText
                }else{
                    camPoseTextView?.text = "no est."
                }
            }else{
                camPoseTextView?.text = "no est."
            }
            image.close()
        }
        return imageAnalysis
    }

    private var bluetoothSocket: BluetoothSocket? = null

    private fun sendCommand(camPose: Pose3d) {
        // Replace this with your ESP-32 device's MAC address
        val esp32DeviceAddress = "00:00:00:00:00:00"

        // If the Bluetooth socket is not connected, establish the connection
        if (bluetoothSocket == null || !bluetoothSocket!!.isConnected) {
            connectToBluetoothDevice(esp32DeviceAddress)
        }

        // If the Bluetooth socket is now connected, send the command
        if (bluetoothSocket?.isConnected == true) {
            sendCommandToDevice(camPose)
        } else {
            // Handle the case where the Bluetooth socket is not connected
            Log.e(TAG, "Bluetooth socket is not connected")
        }
    }

    private fun connectToBluetoothDevice(deviceAddress: String) {
        val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
        var bluetoothDevice = bluetoothAdapter?.getRemoteDevice("The");

        if (bluetoothAdapter == null) {
            // Device doesn't support Bluetooth
            Log.e(TAG, "Device doesn't support Bluetooth")
            return
        }

        val device: BluetoothDevice? = bluetoothAdapter.getRemoteDevice(deviceAddress)

        if (device == null) {
            // Device not found
            Log.e(TAG, "Bluetooth device not found")
            return
        }

        // Launch a coroutine to perform Bluetooth connection in the background
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Get a BluetoothSocket for the connection
                if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                }
                bluetoothSocket = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))

                // Connect to the remote device through the socket. This is a blocking call.
                bluetoothSocket?.connect()
            } catch (e: IOException) {
                // Handle connection or IO exceptions
                Log.e(TAG, "Error connecting to Bluetooth device: ${e.message}")
            }
        }
    }

    private fun sendCommandToDevice(camPose: Pose3d) {
        // Ensure that the Bluetooth socket is connected before sending data
        if (bluetoothSocket?.isConnected == true) {
            try {
                // Send the command to ESP-32
                val outputStream = bluetoothSocket!!.outputStream
                val message = "Hello ESP-32"
                outputStream.write(message.toByteArray())
                // Handle exceptions appropriately
            } catch (e: IOException) {
                // Handle IO exceptions
                Log.e(TAG, "Error sending command: ${e.message}")
            }
        } else {
            // The Bluetooth socket is not connected; handle this case as needed
            Log.e(TAG, "Bluetooth socket is not connected")
        }
    }
        private fun testCamToTarget(camToTarget: Transform3d){
        var x = camToTarget.getX();
        var y = camToTarget.getY();
        var z = camToTarget.getZ();
        var yaw = camToTarget.getRotation().getZ().times((180.0/Math.PI));
        var pitch = camToTarget.getRotation().getY().times((180.0/Math.PI));
        var roll = camToTarget.getRotation().getX().times((180.0/Math.PI));
    }
    private fun poseToString(pose: Pose3d): String {
//        var x = String.format("%.2f", pose.x)
//        var y = String.format("%.2f", pose.y)
//        var z = String.format("%.2f", pose.z)
//
//        var yaw = pose.getRotation().getZ().times((180.0/Math.PI));
//        var pitch = pose.getRotation().getY().times((180.0/Math.PI));
//        var roll = pose.getRotation().getX().times((180.0/Math.PI));
//
//        var yaw_s = String.format("%.0f", yaw)
//        var pitch_s = String.format("%.0f", pitch)
//        var roll_s = String.format("%.0f", roll)
//
//        return "($x,$y,$z)\n($yaw_s,$pitch_s,$roll_s)"; //xyz, yaw pitch roll
////        return String.format("(%4d,%4d,%4d)\n(%4s,%4s,%4s)", x, y, z, yaw_s, pitch_s, roll_s);
        val x = String.format("%.2f", pose.x).padStart(5)
        val y = String.format("%.2f", pose.y).padStart(5)
        val z = String.format("%.2f", pose.z).padStart(5)

        val yaw = pose.rotation.z * (180.0 / Math.PI)
        val pitch = pose.rotation.y * (180.0 / Math.PI)
        val roll = pose.rotation.x * (180.0 / Math.PI)

        val yaw_s = String.format("%.0f", yaw).padStart(5)
        val pitch_s = String.format("%.0f", pitch).padStart(5)
        val roll_s = String.format("%.0f", roll).padStart(5)
        var whatthehell = pitch_s.length
        var whatTHEHELL = "($x,$y,$z)".length

        val result = "($x,$y,$z)\n($yaw_s,$pitch_s,$roll_s)"
        return result;
    }

    private fun calcCamPose(camToTarget: Transform3d, targetPose: Pose3d): Pose3d{
        var targetToCam = camToTarget.inverse();
        var camPose = targetPose.transformBy(targetToCam);
        return camPose;
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun imageDiagnostics(imageProxy: ImageProxy){
        val image = imageProxy.image
        if (image != null) {
            val pixelArray = imageToPixelArray(image)
            Log.d("analysis", "# Planes: " + image.planes.size)
            Log.d("analysis", "# pixels: " + pixelArray.size)
            Log.d("analysis", "first pixel: " + pixelToString(pixelArray.get(0)))
            Log.d("analysis", "image width: " + image.width)
            Log.d("analysis", "image height: " + image.height)
            Log.d("analysis", "image size: " + image.width*image.height)
//            Log.d("analysis", "are all pixels the same? " + areAllPixelsTheSame(pixelArray))
        }else{
            Log.d("analysis", "image is null!")
        }
    }

    private fun pixelToString(pixel: IntArray): String{
        var str = "(";
        for(index in pixel.indices){
            var num = pixel[index]
            str+=num
            str+= ", "
        }
        str+=")"
        return str
    }

    private fun areAllPixelsTheSame(pixelArray: Array<IntArray>): Boolean{
        val normalPixel = intArrayOf(16,16,16,255)
        for(i in pixelArray.indices){
            var pixel = pixelArray[i]
            for(j in normalPixel.indices){
                var normalVal = normalPixel[j]
                var currentVal = pixel[j]
                if(normalVal != currentVal){
                    Log.d("UNIQUE PIXEL", "Pixel #: " + i)
                    Log.d("UNIQUE PIXEL", "Pixel vals: " + pixelToString(pixel))
                }
            }
        }
        return true
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun imageToPixelArray(image: Image): Array<IntArray>{
        val rgbaPlane = image.planes[0]
        val buffer = rgbaPlane.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val pixelArray = ArrayList<IntArray>()
        // Now you can iterate through the pixel data
        for (i in 0 until bytes.size step 4) {
            val red = bytes[i].toInt() and 0xFF // Red component
            val green = bytes[i + 1].toInt() and 0xFF // Green component
            val blue = bytes[i + 2].toInt() and 0xFF // Blue component
            val alpha = bytes[i + 3].toInt() and 0xFF // Alpha component
            val pixel = intArrayOf(red, green, blue, alpha);
            pixelArray.add(pixel)
            // Process the RGBA values
        }
        return pixelArray.toTypedArray()
    }

    // Convert ImageProxy to Bitmap
    private fun imageToByteArray(imageProxy: ImageProxy): ByteArray {
        @OptIn(markerClass = [ExperimentalGetImage::class]) val image = imageProxy.image
        val buffer = image!!.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer[bytes]
        return bytes
    }
    private suspend fun bindCaptureUsecase() {
        val cameraProvider = ProcessCameraProvider.getInstance(requireContext()).await()

        val cameraSelector = getCameraSelector(cameraIndex)

        // create the user required QualitySelector (video resolution): we know this is
        // supported, a valid qualitySelector will be created.
        val quality = cameraCapabilities[cameraIndex].qualities[qualityIndex]
        val qualitySelector = QualitySelector.from(quality)

        captureViewBinding.previewView.updateLayoutParams<ConstraintLayout.LayoutParams> {
            val orientation = this@CaptureFragment.resources.configuration.orientation
            dimensionRatio = quality.getAspectRatioString(quality,
                (orientation == Configuration.ORIENTATION_PORTRAIT))
        }

        val preview = Preview.Builder()
            .setTargetAspectRatio(quality.getAspectRatio(quality))
            .build().apply {
                setSurfaceProvider(captureViewBinding.previewView.surfaceProvider)
            }

        val analyzer = bindVisionDetection()
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                viewLifecycleOwner,
                cameraSelector,
                preview,
                analyzer
            )
        } catch (exc: Exception) {
            // we are on main thread, let's reset the controls on the UI.
            Log.e(TAG, "Use case binding failed", exc)
            resetUIandState("bindToLifecycle failed: $exc")
        }
    }

    /**
     * Retrieve the asked camera's type(lens facing type). In this sample, only 2 types:
     *   idx is even number:  CameraSelector.LENS_FACING_BACK
     *          odd number:   CameraSelector.LENS_FACING_FRONT
     */
    private fun getCameraSelector(idx: Int) : CameraSelector {
//        Log.d("Blue Compat", context?.packageManager?.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH).toString());
//        Log.d("Cam Compat", context?.packageManager?.hasSystemFeature(PackageManager.FEATURE_CAMERA_EXTERNAL).toString());
        if (cameraCapabilities.size == 0) {
            Log.i(TAG, "Error: This device does not have any camera, bailing out")
            requireActivity().finish()
        }
        return (cameraCapabilities[idx % cameraCapabilities.size].camSelector)
    }

    data class CameraCapability(val camSelector: CameraSelector, val qualities:List<Quality>)
    /**
     * Query and cache this platform's camera capabilities, run only once.
     */
    init {
        enumerationDeferred = lifecycleScope.async {
            whenCreated {
                val provider = ProcessCameraProvider.getInstance(requireContext()).await()

                provider.unbindAll()
                for (camSelector in arrayOf(
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    CameraSelector.DEFAULT_FRONT_CAMERA
                )) {
                    try {
                        // just get the camera.cameraInfo to query capabilities
                        // we are not binding anything here.
                        if (provider.hasCamera(camSelector)) {
                            val camera = provider.bindToLifecycle(requireActivity(), camSelector)
                            QualitySelector
                                .getSupportedQualities(camera.cameraInfo)
                                .filter { quality ->
                                    listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD)
                                        .contains(quality)
                                }.also {
                                    cameraCapabilities.add(CameraCapability(camSelector, it))
                                }
                        }
                    } catch (exc: java.lang.Exception) {
                        Log.e(TAG, "Camera Face $camSelector is not supported")
                    }
                }
            }
        }
    }

    /**
     * One time initialize for CameraFragment (as a part of fragment layout's creation process).
     * This function performs the following:
     *   - initialize but disable all UI controls except the Quality selection.
     *   - set up the Quality selection recycler view.
     *   - bind use cases to a lifecycle camera, enable UI controls.
     */
    private fun initCameraFragment() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (enumerationDeferred != null) {
                enumerationDeferred!!.await()
                enumerationDeferred = null
            }
            initializeQualitySectionsUI()

            bindCaptureUsecase()
        }
    }



    /**
     * ResetUI (restart):
     *    in case binding failed, let's give it another change for re-try. In future cases
     *    we might fail and user get notified on the status
     */
    private fun resetUIandState(reason: String) {
        cameraIndex = 0
        qualityIndex = DEFAULT_QUALITY_IDX
        audioEnabled = false
        initializeQualitySectionsUI()
    }

    /**
     *  initializeQualitySectionsUI():
     *    Populate a RecyclerView to display camera capabilities:
     *       - one front facing
     *       - one back facing
     *    User selection is saved to qualityIndex, will be used
     *    in the bindCaptureUsecase().
     */
    private fun initializeQualitySectionsUI() {
        val selectorStrings = cameraCapabilities[cameraIndex].qualities.map {
            it.getNameString()
        }
    }

    // System function implementations
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _captureViewBinding = FragmentCaptureBinding.inflate(inflater, container, false)
        return captureViewBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initCameraFragment()

//        val constraintLayout: ConstraintLayout = view.findViewById(R.id.container)

        // Set the size of the PreviewView based on the display dimensions

        // Set the size of the PreviewView based on the display dimensions
//        setPreviewViewSize(constraintLayout)
    }

    private fun setPreviewViewSize(previewView: ConstraintLayout) {
        val displayMetrics = DisplayMetrics()
        requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
        val screenHeight = displayMetrics.widthPixels
        val screenWidth = displayMetrics.heightPixels
        Log.d("dim", "screenWidth: " + screenWidth)
        Log.d("dim", "screenHeight: " + screenHeight)

        val dim_ratio = 3.0/4
        if(screenWidth * dim_ratio > screenHeight){
            previewView.layoutParams.height = screenHeight
            previewView.layoutParams.width = (screenHeight/dim_ratio).toInt()
        }else{
            previewView.layoutParams.width = screenWidth
            previewView.layoutParams.height = (screenWidth*dim_ratio).toInt()
        }

        Log.d("dim", "layout width: " + previewView.layoutParams.width)
        Log.d("dim", "layout height: " + previewView.layoutParams.height)
    }
    override fun onDestroyView() {
        _captureViewBinding = null
        super.onDestroyView()
    }

    companion object {
        // default Quality selection if no input from UI
        const val DEFAULT_QUALITY_IDX = 0
        val TAG:String = CaptureFragment::class.java.simpleName
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    }
}