/*
 * Copyright (C) 2025 AxionOS
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.sense.controller

import android.content.Context
import android.graphics.Bitmap
import android.os.*
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import co.aospa.sense.camera.CameraXService
import co.aospa.sense.util.Constants.MSG_UNLOCK_FACE_NOT_FOUND
import co.aospa.sense.util.Util
import java.lang.ref.WeakReference

class FaceAuthenticationController(
    context: Context,
    private var callback: ServiceCallback?
) {
    interface ServiceCallback {
        fun handleFrame(bitmap: Bitmap): Int
        fun onCameraError()
        fun onTimeout(withFace: Boolean)
    }

    private val contextRef = WeakReference(context)
    private var cameraService: CameraXService? = null
    private var handler: Handler? = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var faceDetectedDuringSession = false

    private val frameCallback = object : CameraXService.FrameCallback {
        override fun onFrame(bitmap: Bitmap) {
            if (!isRunning) return

            val result = callback?.handleFrame(bitmap) ?: -1

            if (result == 0) {
                stop()
                return
            }

            if (result != MSG_UNLOCK_FACE_NOT_FOUND) {
                faceDetectedDuringSession = true
                handler?.removeMessages(MSG_TIME_OUT_NO_FACE)
            }

            cameraService?.resetProcessing()
        }

        override fun onError(error: Exception) {
            Log.e(TAG, "Camera error: ${error.message}")
            callback?.onCameraError()
            stop()
        }
    }

    fun start(lifecycleOwner: LifecycleOwner) {
        Log.i(TAG, "start")
        val context = contextRef.get() ?: return

        isRunning = true
        faceDetectedDuringSession = false

        cameraService = CameraXService(context, lifecycleOwner)
        cameraService?.start(frameCallback)

        handler?.sendEmptyMessageDelayed(MSG_TIME_OUT_NO_FACE, MATCH_TIME_OUT_NO_FACE_MS)
        handler?.sendEmptyMessageDelayed(MSG_TIME_OUT_WITH_FACE, MATCH_TIME_OUT_WITH_FACE_MS)
    }

    fun stop() {
        Log.i(TAG, "stop")
        isRunning = false

        handler?.removeMessages(MSG_TIME_OUT_NO_FACE)
        handler?.removeMessages(MSG_TIME_OUT_WITH_FACE)

        cameraService?.stop()
        cameraService = null
    }

    init {
        handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    MSG_TIME_OUT_NO_FACE -> {
                        if (Util.IS_DEBUG_LOGGING) Log.d(TAG, "Timeout (no face)")
                        callback?.onTimeout(false)
                        stop()
                    }
                    MSG_TIME_OUT_WITH_FACE -> {
                        if (Util.IS_DEBUG_LOGGING) Log.d(TAG, "Timeout (with face)")
                        callback?.onTimeout(faceDetectedDuringSession)
                        stop()
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "FaceAuthController"
        private const val MSG_TIME_OUT_NO_FACE = 1
        private const val MSG_TIME_OUT_WITH_FACE = 2
        private const val MATCH_TIME_OUT_NO_FACE_MS = 3000L
        private const val MATCH_TIME_OUT_WITH_FACE_MS = 4800L
    }
}
