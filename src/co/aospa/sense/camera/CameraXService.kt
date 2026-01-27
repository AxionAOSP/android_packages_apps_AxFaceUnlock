/*
 * Copyright (C) 2025 AxionOS
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.sense.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import co.aospa.sense.util.Loggable
import co.aospa.sense.util.logD
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import android.os.SystemProperties
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop

class CameraXService(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) : Loggable {

    interface FrameCallback {
        fun onFrame(bitmap: Bitmap)
        fun onError(error: Exception)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var preview: Preview? = null
    private var callback: FrameCallback? = null
    private var isProcessing = false
    private val analyzerExecutor = Executors.newSingleThreadExecutor()

    private var frameBitmap: Bitmap? = null
    private var surfaceProvider: Preview.SurfaceProvider? = null
    private var isBound = false

    private val targetSize: Size by lazy { Size(640, 480) }

    fun setSurfaceProvider(provider: Preview.SurfaceProvider?) {
        val hadProvider = surfaceProvider != null
        surfaceProvider = provider
        
        if (provider != null && isBound && preview != null) {
            preview?.surfaceProvider = provider
        } else if (provider != null && cameraProvider != null && !hadProvider) {
            mainHandler.post { rebindWithPreview() }
        }
    }

    fun start(frameCallback: FrameCallback) {
        callback = frameCallback
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCamera()
            } catch (e: Exception) {
                logD("Failed to get camera provider: ${e.message}")
                callback?.onError(e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    @ExperimentalCamera2Interop
    private fun getCameraSelector(): CameraSelector {
        val cameraId = SystemProperties.get("ro.face.sense_service.camera_id", "")
        if (cameraId.isNotEmpty()) {
            logD("Requested camera ID from prop: $cameraId")
            return CameraSelector.Builder()
                .addCameraFilter { cameraInfos ->
                    val allIds = cameraInfos.map { Camera2CameraInfo.from(it).cameraId }
                    logD("Available camera IDs: $allIds")
                    
                    val filtered = cameraInfos.filter {
                        try {
                            val infoId = Camera2CameraInfo.from(it).cameraId
                            infoId == cameraId || infoId.endsWith(":$cameraId")
                        } catch (e: Exception) {
                            false
                        }
                    }
                    
                    if (filtered.isEmpty()) {
                        logD("Camera ID $cameraId not found in available list, searching for front camera...")
                        val frontCameras = cameraInfos.filter {
                            it.lensFacing == CameraSelector.LENS_FACING_FRONT
                        }
                        if (frontCameras.isEmpty()) {
                            logD("No front camera found, using first available camera")
                            cameraInfos.take(1)
                        } else {
                            frontCameras
                        }
                    } else {
                        logD("Selected camera ID(s): ${filtered.map { Camera2CameraInfo.from(it).cameraId }}")
                        filtered
                    }
                }
                .build()
        }

        logD("No camera ID prop, using default front camera")
        return CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
            .build()
    }

    @ExperimentalCamera2Interop
    private fun bindCamera() {
        val provider = cameraProvider ?: return
        val cameraSelector = getCameraSelector()

        imageAnalysis = createImageAnalysis()
        imageAnalysis?.setAnalyzer(analyzerExecutor, analyzer)

        try {
            provider.unbindAll()
            if (surfaceProvider != null) {
                preview = Preview.Builder()
                    .setTargetResolution(targetSize)
                    .build()
                preview?.surfaceProvider = surfaceProvider
                provider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
            } else {
                provider.bindToLifecycle(lifecycleOwner, cameraSelector, imageAnalysis)
            }

            isBound = true
            logD("Camera bound successfully, preview=${surfaceProvider != null}")
        } catch (e: Exception) {
            logD("Failed to bind camera: ${e.message}")
            callback?.onError(e)
        }
    }

    @ExperimentalCamera2Interop
    private fun rebindWithPreview() {
        val provider = cameraProvider ?: return
        val cameraSelector = getCameraSelector()

        try {
            provider.unbindAll()
            preview = Preview.Builder()
                .setTargetResolution(targetSize)
                .build()
            preview?.surfaceProvider = surfaceProvider

            imageAnalysis = createImageAnalysis()
            imageAnalysis?.setAnalyzer(analyzerExecutor, analyzer)

            provider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
            isBound = true
            logD("Camera rebound with preview")
        } catch (e: Exception) {
            logD("Failed to rebind camera: ${e.message}")
        }
    }

    private fun createImageAnalysis(): ImageAnalysis {
        return ImageAnalysis.Builder()
            .setTargetResolution(targetSize)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
    }

    @ExperimentalGetImage
    private val analyzer = ImageAnalysis.Analyzer { image ->
        if (isProcessing) {
            image.close()
            return@Analyzer
        }
        isProcessing = true

        try {
             val plane = image.planes[0]
             val rowStride = plane.rowStride
             val pixelStride = plane.pixelStride
             val width = image.width
             val height = image.height
             val buffer = plane.buffer
             buffer.rewind()

             if (frameBitmap == null || 
                 frameBitmap!!.width != width || 
                 frameBitmap!!.height != height) {
                 frameBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
             }

             if (rowStride == width * pixelStride) {
                 frameBitmap!!.copyPixelsFromBuffer(buffer)
             } else {
                 val packedSize = width * height * pixelStride
                 val packedBuffer = ByteBuffer.allocate(packedSize)
                 val rowBuffer = ByteArray(width * pixelStride)
                 for (y in 0 until height) {
                     buffer.position(y * rowStride)
                     buffer.get(rowBuffer)
                     packedBuffer.put(rowBuffer)
                 }
                 packedBuffer.rewind()
                 frameBitmap!!.copyPixelsFromBuffer(packedBuffer)
             }

            val rotation = image.imageInfo.rotationDegrees
            val matrix = Matrix().apply {
                postRotate(rotation.toFloat(), frameBitmap!!.width / 2f, frameBitmap!!.height / 2f)
                postScale(-1f, 1f, frameBitmap!!.width / 2f, frameBitmap!!.height / 2f)
            }

            val rotatedWidth = if (rotation == 90 || rotation == 270) frameBitmap!!.height else frameBitmap!!.width
            val rotatedHeight = if (rotation == 90 || rotation == 270) frameBitmap!!.width else frameBitmap!!.height

            val transformed = Bitmap.createBitmap(
                frameBitmap!!,
                0, 0,
                frameBitmap!!.width,
                frameBitmap!!.height,
                matrix,
                false
            )

            callback?.onFrame(transformed)
        } catch (e: Exception) {
            logD("Frame processing error: ${e.message}")
        } finally {
            isProcessing = false
            image.close()
        }
    }

    fun stop() {
        logD("Stopping camera")
        mainHandler.post {
            isBound = false
            cameraProvider = null
            imageAnalysis = null
            preview = null
            callback = null
            frameBitmap = null
            surfaceProvider = null
            try {
                if (!analyzerExecutor.isShutdown) {
                    analyzerExecutor.shutdown()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun resetProcessing() {
        isProcessing = false
    }
}
