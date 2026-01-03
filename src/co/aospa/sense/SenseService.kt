/*
 * Copyright (C) 2025 AxionOS
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.sense

import android.Manifest
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.biometrics.BiometricFaceConstants
import android.hardware.camera2.CameraManager
import android.os.*
import android.util.Log
import androidx.core.content.ContextCompat

import co.aospa.sense.controller.FaceAuthenticationController
import co.aospa.sense.controller.FaceEnrollController
import co.aospa.sense.camera.ServiceLifecycleOwner
import co.aospa.sense.util.Constants
import co.aospa.sense.util.PreferenceHelper
import co.aospa.sense.util.Util
import co.aospa.sense.vendor.VendorImpl

import java.util.*

import vendor.aospa.biometrics.face.ISenseService
import vendor.aospa.biometrics.face.ISenseServiceReceiver

class SenseService : Service() {

    private var cameraAuthController: FaceAuthenticationController? = null
    private var cameraEnrollController: FaceEnrollController? = null
    private var cameraManager: CameraManager? = null
    private var senseReceiver: ISenseServiceReceiver? = null
    private var preferenceHelper: PreferenceHelper? = null
    private var service: SenseServiceWrapper? = null
    private var vendorImpl: VendorImpl? = null
    private var authLifecycleOwner: ServiceLifecycleOwner? = null
    private var enrollLifecycleOwner: ServiceLifecycleOwner? = null
    private var cameraId = 0
    private var challengeCount = 0
    private var userId = 0
    private var challenge: Long = 0
    private var enrollToken: ByteArray? = null
    private var isAuthenticated = false
    private var workHandler: FaceHandler? = null

    private val authCallback = object : FaceAuthenticationController.ServiceCallback {
        override fun handleFrame(bitmap: Bitmap): Int {
            if (Util.IS_DEBUG_LOGGING) Log.d(TAG, "handleFrame start")

            val result = vendorImpl?.compare(bitmap) ?: Constants.MSG_UNLOCK_FAILED

            if (Util.IS_DEBUG_LOGGING) Log.d(TAG, "handleFrame result=$result")

            synchronized(this@SenseService) {
                if (cameraAuthController == null) return -1
                if (result == 0) {
                    isAuthenticated = true
                    onAuthenticated()
                }
            }
            return result
        }

        override fun onTimeout(withFace: Boolean) {
            if (Util.IS_DEBUG_LOGGING) Log.d(TAG, "onTimeout, withFace=$withFace")
            try {
                senseReceiver?.onAuthenticated(
                    0, -1, preferenceHelper?.getByteArrayValueByKey(Constants.SHARED_KEY_ENROLL_TOKEN)
                )
            } catch (e: RemoteException) {
                e.printStackTrace()
            }
            stopAuthentication()
        }

        override fun onCameraError() {
            try {
                senseReceiver?.onError(BiometricFaceConstants.FACE_ERROR_CANCELED, 0)
            } catch (e: RemoteException) {
                e.printStackTrace()
            }
            stopAuthentication()
        }
    }

    private val enrollCallback = object : FaceEnrollController.CameraCallback {
        private val ENROLLMENT_SAMPLE_COUNT = 5
        private var errorCount = 0
        private val MAX_ERRORS = 3

        override fun handleSaveFeature(bitmap: Bitmap): Int {
            val result = vendorImpl?.saveFeature(bitmap) ?: -1

            synchronized(this@SenseService) {
                if (cameraEnrollController == null) return -1

                try {
                    val faceIds = 1
                    if (result == 0) {
                        if (Util.IS_DEBUG_LOGGING) Log.d(TAG, "Enrollment complete")
                        val faceId = preferenceHelper?.getIntValueByKey(Constants.SHARED_KEY_FACE_ID) ?: 0
                        if (faceId > 0) {
                            vendorImpl?.deleteFeature(faceId)
                        }
                        preferenceHelper?.saveIntValue(Constants.SHARED_KEY_FACE_ID, faceIds)
                        preferenceHelper?.saveByteArrayValue(Constants.SHARED_KEY_ENROLL_TOKEN, enrollToken)
                        Util.setFaceUnlockAvailable(applicationContext)
                        errorCount = 0
                        stopEnroll()
                        senseReceiver?.onEnrollResult(faceIds, userId, 0)
                    } else if (result == Constants.MSG_UNLOCK_KEEP) {
                        if (Util.IS_DEBUG_LOGGING) Log.d(TAG, "Enrollment in progress")
                        val enrolledCount = vendorImpl?.getEnrolledCount(userId) ?: 0
                        val remaining = ENROLLMENT_SAMPLE_COUNT - enrolledCount
                        if (Util.IS_DEBUG_LOGGING) Log.d(TAG, "Samples: $enrolledCount/$ENROLLMENT_SAMPLE_COUNT")
                        senseReceiver?.onEnrollResult(faceIds, userId, remaining)
                    } else if (result == Constants.MSG_UNLOCK_FAILED) {
                        errorCount++
                        if (Util.IS_DEBUG_LOGGING) Log.d(TAG, "Enrollment error, count: $errorCount/$MAX_ERRORS")
                        if (errorCount >= MAX_ERRORS) {
                            errorCount = 0
                            stopEnroll()
                            senseReceiver?.onError(BiometricFaceConstants.FACE_ERROR_UNABLE_TO_PROCESS, 0)
                        } else {
                            senseReceiver?.onAcquired(userId, BiometricFaceConstants.FACE_ACQUIRED_NOT_DETECTED, 0)
                        }
                    }
                } catch (e: RemoteException) {
                    e.printStackTrace()
                }
            }
            return result
        }

        override fun handleSaveFeatureResult(result: Int) {}

        override fun onFaceDetected() {}

        override fun onTimeout() {
            try {
                stopEnroll()
                senseReceiver?.onError(BiometricFaceConstants.FACE_ERROR_TIMEOUT, 0)
            } catch (e: RemoteException) {
                e.printStackTrace()
            }
        }

        override fun onCameraError() {
            try {
                stopEnroll()
                senseReceiver?.onError(BiometricFaceConstants.FACE_ERROR_CANCELED, 0)
            } catch (e: RemoteException) {
                e.printStackTrace()
            }
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        return START_REDELIVER_INTENT
    }

    override fun onBind(intent: Intent): IBinder? {
        if (Util.IS_DEBUG_LOGGING) Log.i(TAG, "onBind")
        return service
    }

    override fun onCreate() {
        super.onCreate()
        if (Util.IS_DEBUG_LOGGING) Log.i(TAG, "onCreate")
        cameraManager = getSystemService(CameraManager::class.java)
        service = SenseServiceWrapper()
        val handlerThread = HandlerThread(TAG, -2)
        handlerThread.start()
        workHandler = FaceHandler(handlerThread.looper)
        preferenceHelper = PreferenceHelper(this)
        vendorImpl = VendorImpl(this)
        userId = Util.getUserId(this)
        if (!Util.isFaceUnlockDisabledByDPM(this)) {
            workHandler?.post {
                vendorImpl?.init()
                Log.d(TAG, "VendorImpl initialized")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (Util.IS_DEBUG_LOGGING) Log.d(TAG, "onDestroy")
        vendorImpl?.release()
        authLifecycleOwner?.stop()
    }

    private fun onAuthenticated() {
        try {
            senseReceiver?.onAuthenticated(
                preferenceHelper?.getIntValueByKey(Constants.SHARED_KEY_FACE_ID) ?: 0,
                userId,
                preferenceHelper?.getByteArrayValueByKey(Constants.SHARED_KEY_ENROLL_TOKEN)
            )
            stopAuthentication()
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }

    private fun stopEnroll() {
        cameraEnrollController?.stop(enrollCallback)
        cameraEnrollController = null
        enrollLifecycleOwner?.stop()
        enrollLifecycleOwner = null
        enrollToken = null
    }

    private fun stopAuthentication() {
        synchronized(this) {
            cameraAuthController?.stop()
            cameraAuthController = null
            authLifecycleOwner?.stop()
            authLifecycleOwner = null
        }
    }

    private fun stopCurrentWork() {
        if (cameraAuthController != null) {
            try {
                senseReceiver?.onError(BiometricFaceConstants.FACE_ERROR_USER_CANCELED, 0)
            } catch (e: RemoteException) {
                e.printStackTrace()
            }
            stopAuthentication()
        }
        if (cameraEnrollController != null) {
            try {
                senseReceiver?.onError(BiometricFaceConstants.FACE_ERROR_USER_CANCELED, 0)
            } catch (e: RemoteException) {
                e.printStackTrace()
            }
            stopEnroll()
        }
    }

    private inner class SenseServiceWrapper : ISenseService.Stub() {
        override fun getFeature(feature: Int, faceId: Int): Boolean = false

        override fun setFeature(feature: Int, enable: Boolean, cryptoToken: ByteArray?, faceId: Int) {}

        override fun setCallback(faceServiceReceiver: ISenseServiceReceiver?) {
            senseReceiver = faceServiceReceiver
        }

        override fun enroll(cryptoToken: ByteArray?, timeout: Int, disabledFeatures: IntArray?) {
            if (Util.IS_DEBUG_LOGGING) Log.d(TAG, "enroll")
            if (Util.isFaceUnlockDisabledByDPM(this@SenseService) || challenge == 0L || cryptoToken == null) {
                Log.e(TAG, "Could not enroll: hasChallenge=${challenge != 0L} hasCryptoToken=${cryptoToken != null}")
                try {
                    senseReceiver?.onError(BiometricFaceConstants.FACE_ERROR_TIMEOUT, 0)
                } catch (e: RemoteException) {
                    e.printStackTrace()
                }
                return
            }

            enrollToken = cryptoToken
            val faceId = preferenceHelper?.getIntValueByKey(Constants.SHARED_KEY_FACE_ID) ?: 0
            if (faceId > 0) {
                vendorImpl?.deleteFeature(faceId - 1)
                preferenceHelper?.removeSharePreferences(Constants.SHARED_KEY_FACE_ID)
                preferenceHelper?.removeSharePreferences(Constants.SHARED_KEY_ENROLL_TOKEN)
            }

            workHandler?.post {
                vendorImpl?.init()
                if (Util.IS_DEBUG_LOGGING) Log.d(TAG, "VendorImpl initialized for enrollment")

                synchronized(this@SenseService) {
                    if (cameraEnrollController == null) {
                        cameraEnrollController = FaceEnrollController.getInstance()
                    }
                    cameraEnrollController?.start(enrollCallback, 0)
                }
            }
        }

        override fun cancel() {
            if (Util.IS_DEBUG_LOGGING) Log.d(TAG, "cancel")
            workHandler?.post {
                cameraAuthController?.let { stopAuthentication() }
                cameraEnrollController?.let { stopEnroll() }
                try {
                    senseReceiver?.onError(BiometricFaceConstants.FACE_ERROR_CANCELED, 0)
                } catch (e: RemoteException) {
                    e.printStackTrace()
                }
            }
        }

        override fun authenticate(operationId: Long) {
            if (Util.IS_DEBUG_LOGGING) Log.d(TAG, "authenticate")

            if (!Util.isFaceUnlockAvailable(this@SenseService) ||
                ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.CAMERA) != 0
            ) {
                try {
                    senseReceiver?.onError(BiometricFaceConstants.FACE_ERROR_CANCELED, 0)
                } catch (e: RemoteException) {
                    e.printStackTrace()
                }
                return
            }

            if (Util.isFaceUnlockDisabledByDPM(this@SenseService)) {
                try {
                    senseReceiver?.onError(BiometricFaceConstants.FACE_ERROR_CANCELED, 0)
                } catch (e: RemoteException) {
                    e.printStackTrace()
                }
                return
            }

            workHandler?.post {
                vendorImpl?.init()
                vendorImpl?.compareStart()

                synchronized(this@SenseService) {
                    authLifecycleOwner = ServiceLifecycleOwner()
                    authLifecycleOwner?.start()

                    if (cameraAuthController == null) {
                        cameraAuthController = FaceAuthenticationController(
                            this@SenseService,
                            authCallback
                        )
                    } else {
                        cameraAuthController?.stop()
                    }
                    cameraAuthController?.start(authLifecycleOwner!!)
                }
            }
        }

        override fun remove(biometricId: Int) {
            if (Util.IS_DEBUG_LOGGING) Log.d(TAG, "remove")
            workHandler?.post {
                val faceId = preferenceHelper?.getIntValueByKey(Constants.SHARED_KEY_FACE_ID) ?: 0
                if (biometricId != 0 && faceId != biometricId) {
                    Log.e(TAG, "Removing biometricId: $biometricId")
                }
                vendorImpl?.deleteFeature(faceId - 1)
                preferenceHelper?.removeSharePreferences(Constants.SHARED_KEY_FACE_ID)
                preferenceHelper?.removeSharePreferences(Constants.SHARED_KEY_ENROLL_TOKEN)
                Util.setFaceUnlockAvailable(applicationContext)
                try {
                    senseReceiver?.onRemoved(if (biometricId == 0) intArrayOf(faceId) else intArrayOf(biometricId), userId)
                } catch (e: RemoteException) {
                    e.printStackTrace()
                }
            }
        }

        override fun enumerate(): Int {
            val faceId = preferenceHelper?.getIntValueByKey(Constants.SHARED_KEY_FACE_ID) ?: 0
            val faceIds = if (faceId > -1) intArrayOf(faceId) else IntArray(0)
            workHandler?.post {
                try {
                    if (Util.IS_DEBUG_LOGGING) Log.d(TAG, "enumerate = $senseReceiver")
                    if (senseReceiver == null) {
                        try {
                            Thread.sleep(50)
                        } catch (e: InterruptedException) {
                            e.printStackTrace()
                        }
                    }
                    senseReceiver?.onEnumerate(faceIds, userId)
                } catch (e: RemoteException) {
                    e.printStackTrace()
                }
            }
            return 0
        }

        override fun getFeatureCount(): Int {
            return if ((preferenceHelper?.getIntValueByKey(Constants.SHARED_KEY_FACE_ID) ?: 0) > -1) 1 else 0
        }

        override fun generateChallenge(timeout: Int): Long {
            if (Util.IS_DEBUG_LOGGING) Log.d(TAG, "generateChallenge + $timeout")
            if (challengeCount <= 0 || challenge == 0L) {
                challenge = Random().nextLong()
            }
            challengeCount += 1
            workHandler?.removeMessages(MSG_CHALLENGE_TIMEOUT)
            workHandler?.sendEmptyMessageDelayed(MSG_CHALLENGE_TIMEOUT, timeout * 1000L)
            return challenge
        }

        override fun revokeChallenge(): Int {
            if (Util.IS_DEBUG_LOGGING) Log.d(TAG, "revokeChallenge")
            challengeCount -= 1
            if (challengeCount <= 0 && challenge != 0L) {
                challenge = 0
                challengeCount = 0
                workHandler?.removeMessages(MSG_CHALLENGE_TIMEOUT)
                stopCurrentWork()
            }
            return 0
        }

        override fun getAuthenticatorId(): Int = -1

        override fun resetLockout(cryptoToken: ByteArray?) {}
    }

    private inner class FaceHandler(looper: Looper) : Handler(looper) {
        override fun handleMessage(message: Message) {
            if (message.what == MSG_CHALLENGE_TIMEOUT) {
                challenge = 0
                challengeCount = 0
                stopCurrentWork()
            }
        }
    }

    companion object {
        private const val TAG = "SenseService"
        private const val MSG_CHALLENGE_TIMEOUT = 100
    }
}
