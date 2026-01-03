/*
 * Copyright (C) 2025 AxionOS
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.sense.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import co.aospa.sense.controller.FaceEnrollController
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private var sharedCameraProvider: ProcessCameraProvider? = null
private var previewUseCase: Preview? = null
private var imageAnalysis: ImageAnalysis? = null
private var frameBitmap: Bitmap? = null

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            sharedCameraProvider?.unbindAll()
            sharedCameraProvider = null
            previewUseCase = null
            imageAnalysis = null
            cameraExecutor.shutdown()
        }
    }

    AndroidView(
        factory = { ctx: Context ->
            TextureView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        modifier = modifier.fillMaxSize(),
        update = { textureView ->
            if (textureView.isAvailable) {
                attachPreview(context, lifecycleOwner, textureView, cameraExecutor)
            } else {
                textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                        attachPreview(context, lifecycleOwner, textureView, cameraExecutor)
                    }
                    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                }
            }
        }
    )
}

private fun attachPreview(
    context: Context, 
    lifecycleOwner: LifecycleOwner, 
    textureView: TextureView,
    executor: ExecutorService
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

    cameraProviderFuture.addListener({
        try {
            val cameraProvider = cameraProviderFuture.get()
            sharedCameraProvider = cameraProvider

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build()

            if (previewUseCase == null) {
                previewUseCase = Preview.Builder()
                    .setTargetResolution(Size(640, 480))
                    .build()
            }

            previewUseCase?.surfaceProvider = Preview.SurfaceProvider { request ->
                val surfaceTexture = textureView.surfaceTexture ?: return@SurfaceProvider
                surfaceTexture.setDefaultBufferSize(request.resolution.width, request.resolution.height)
                val surface = Surface(surfaceTexture)
                request.provideSurface(surface, ContextCompat.getMainExecutor(context)) { _ ->
                    surface.release()
                }
            }

            if (imageAnalysis == null) {
                imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()

                imageAnalysis?.setAnalyzer(executor) { image ->
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
             
                         val transformed = Bitmap.createBitmap(
                             frameBitmap!!,
                             0, 0,
                             frameBitmap!!.width,
                             frameBitmap!!.height,
                             matrix,
                             false
                         )

                        FaceEnrollController.getInstance().onFrame(transformed)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        image.close()
                    }
                }
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, previewUseCase, imageAnalysis)
            } catch (e: Exception) {
            }
        } catch (e: Exception) {
        }
    }, ContextCompat.getMainExecutor(context))
}
