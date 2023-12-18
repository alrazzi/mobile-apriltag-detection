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

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.media.Image
import android.os.Bundle
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
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

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

            // Process each frame for object detection here
            // Overlay detected objects on the camera preview
            // Continue processing frames in real-time

            //Log.d("Analysis", "Image width: " + image.width)
            //Log.d("Analysis", "Image height: " + image.height)
            val pixelArray = imageToPixelArray(image.image!!)
//            imageDiagnostics(image)
//            Log.d("Analysis", apriltag.stringFromJNI(pixelArray, image.width, image.height))
//            val imageAsString = imageToByteArray(image).joinToString { byte->byte.toString() }
//            Log.d("Analysis", imageAsString)
            var result = apriltag.getApriltagResult(pixelArray, image.width, image.height);
            if(result.isTagDetected){
                var camToTarget = result.camToTargetPacket?.getTransform3d();
                var x = camToTarget?.getX();
                var y = camToTarget?.getY();
                var z = camToTarget?.getZ();
                var yaw = camToTarget?.getRotation()?.getZ()?.times((180.0/Math.PI));
                var pitch = camToTarget?.getRotation()?.getY()?.times((180.0/Math.PI));
                var roll = camToTarget?.getRotation()?.getX()?.times((180.0/Math.PI));

                var camPose = camToTarget?.let { calcCamPose(it, Pose3d()) };
            }
            image.close()
        }
        return imageAnalysis
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