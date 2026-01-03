/*
 * Copyright (C) 2025 AxionOS
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.sense.controller

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.*
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import co.aospa.sense.SenseApp.Companion.app
import co.aospa.sense.camera.CameraXService
import co.aospa.sense.util.logD
import co.aospa.sense.util.Util

class FaceEnrollController private constructor(private val context: Context?) {

    interface CameraCallback {
        fun handleSaveFeature(bitmap: Bitmap): Int
        fun handleSaveFeatureResult(result: Int)
        fun onCameraError()
        fun onFaceDetected()
        fun onTimeout()
    }

    private val callbacks = mutableListOf<CameraCallback>()
    private var cameraService: CameraXService? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var handler: Handler? = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var isProcessing = false
    private var lastFaceDetected = false

    fun onFrame(bitmap: Bitmap) {
        if (!isRunning || isProcessing) return
        isProcessing = true

        try {
            var result = -1
            synchronized(callbacks) {
                for (callback in callbacks) {
                    val featureResult = callback.handleSaveFeature(bitmap)
                    if (Util.IS_DEBUG_LOGGING) Log.d(TAG, "handleSaveFeature result: $featureResult")
                    if (featureResult != -1) {
                        result = featureResult
                    }
                }

                if (result >= 0 && !lastFaceDetected) {
                    lastFaceDetected = true
                    for (callback in callbacks) {
                        callback.onFaceDetected()
                    }
                } else if (result < 0) {
                     lastFaceDetected = false
                }

                for (callback in callbacks) {
                    callback.handleSaveFeatureResult(result)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame: ${e.message}")
        } finally {
            isProcessing = false
        }
    }

    private val frameCallback = object : CameraXService.FrameCallback {
        override fun onFrame(bitmap: Bitmap) {
            this@FaceEnrollController.onFrame(bitmap)
            cameraService?.resetProcessing()
        }

        override fun onError(error: Exception) {
            Log.e(TAG, "Camera error: ${error.message}")
            synchronized(callbacks) {
                for (callback in callbacks) {
                    callback.onCameraError()
                }
            }
            stopInternal()
        }
    }

    fun setLifecycleOwner(owner: LifecycleOwner?) {
        lifecycleOwner = owner
    }

    fun getCameraService(): CameraXService? = cameraService

    fun start(callback: CameraCallback, timeout: Int) {
        Log.i(TAG, "start: $callback")

        synchronized(callbacks) {
            if (callbacks.contains(callback)) return
            callbacks.add(callback)
        }

        if (!isRunning) {
            isRunning = true
            isProcessing = false
            lastFaceDetected = false

            val owner = lifecycleOwner
            if (owner != null && context != null) {
                cameraService = CameraXService(context, owner)
                cameraService?.start(frameCallback)
            }
        }

        if (timeout > 0) {
            handler?.postDelayed({
                synchronized(callbacks) {
                    if (callbacks.contains(callback)) {
                        callback.onTimeout()
                    }
                }
            }, timeout.toLong())
        }
    }

    fun stop(callback: CameraCallback) {
        Log.i(TAG, "stop: $callback")

        synchronized(callbacks) {
            if (!callbacks.contains(callback)) {
                Log.e(TAG, "Callback already released!")
                return
            }
            callbacks.remove(callback)
            if (callbacks.isNotEmpty()) return
        }

        stopInternal()
    }

    private fun stopInternal() {
        isRunning = false
        cameraService?.stop()
        cameraService = null
        lifecycleOwner = null
        lastFaceDetected = false
    }

    companion object {
        private const val TAG = "FaceEnrollController"

        @SuppressLint("StaticFieldLeak")
        private var instance: FaceEnrollController? = null

        fun getInstance(): FaceEnrollController {
            if (instance == null) {
                instance = FaceEnrollController(app)
            }
            return instance!!
        }
    }
}
