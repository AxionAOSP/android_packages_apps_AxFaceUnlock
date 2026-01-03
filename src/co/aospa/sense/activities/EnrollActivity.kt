/*
 * Copyright (C) 2025 AxionOS
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.sense.activities

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.hardware.face.FaceManager
import android.os.*
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import co.aospa.sense.R
import co.aospa.sense.ui.screens.EnrollScreen
import co.aospa.sense.ui.screens.EnrollUiState
import co.aospa.sense.util.Constants
import co.aospa.sense.util.PreferenceHelper
import co.aospa.sense.util.Util

class EnrollActivity : ComponentActivity() {

    private var enrollmentCancel = CancellationSignal()
    private var faceManager: FaceManager? = null
    private var preferenceHelper: PreferenceHelper? = null
    private var token: ByteArray? = null
    private var userId = 0
    private var hasCameraPermission = false
    private var isActivityPaused = false
    private var isFinishing = false
    private var enrolledSamples = 0
    private val totalSamples = 5
    private val handler = Handler(Looper.getMainLooper())
    private val timeoutRunnable = Runnable {
        if (!isActivityPaused && !uiState.isComplete && !isFinishing) {
            isFinishing = true
            startActivity(Intent(this@EnrollActivity, TryAgainActivity::class.java).apply {
                putExtra(Constants.EXTRA_KEY_CHALLENGE_TOKEN, token)
                putExtra(Intent.EXTRA_USER_ID, userId)
            })
            finish()
        }
    }

    private var uiState by mutableStateOf(EnrollUiState())

    private val enrollmentCallback = object : FaceManager.EnrollmentCallback() {
        override fun onEnrollmentProgress(remaining: Int) {
            if (Util.IS_DEBUG_LOGGING) Log.d(TAG, "onEnrollmentProgress: remaining=$remaining")
            
            enrolledSamples = totalSamples - remaining
            val progress = (enrolledSamples.toFloat() / totalSamples) * 100f
            
            runOnUiThread {
                uiState = uiState.copy(
                    progress = progress,
                    isFaceDetected = true,
                    isComplete = remaining == 0
                )
                
                if (remaining == 0) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (!isDestroyed) startFinishActivity()
                    }, 1500)
                }
            }
        }

        override fun onEnrollmentHelp(helpMessageId: Int, message: CharSequence) {
            if (Util.IS_DEBUG_LOGGING) Log.d(TAG, "onEnrollmentHelp: $message (id=$helpMessageId)")
            runOnUiThread {
                uiState = uiState.copy(
                    errorMessage = message.toString(), 
                    isError = true,
                    isFaceDetected = false
                )
                handler.postDelayed({
                    if (!isDestroyed) {
                        uiState = uiState.copy(isError = false)
                    }
                }, 500)
            }
        }

        override fun onEnrollmentError(errorMessageId: Int, message: CharSequence) {
            if (Util.IS_DEBUG_LOGGING) Log.d(TAG, "onEnrollmentError: $message")
            if (!isActivityPaused && !isFinishing) {
                isFinishing = true
                startActivity(Intent(this@EnrollActivity, TryAgainActivity::class.java).apply {
                    putExtra(Constants.EXTRA_KEY_CHALLENGE_TOKEN, token)
                    putExtra(Intent.EXTRA_USER_ID, userId)
                })
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferenceHelper = PreferenceHelper(this)
        faceManager = getSystemService(FaceManager::class.java)
        token = intent.getByteArrayExtra(Constants.EXTRA_KEY_CHALLENGE_TOKEN)
        userId = intent.getIntExtra(Intent.EXTRA_USER_ID, 0)

        if (savedInstanceState != null && token == null) {
            token = savedInstanceState.getByteArray(Constants.EXTRA_KEY_CHALLENGE_TOKEN)
            userId = savedInstanceState.getInt(Intent.EXTRA_USER_ID)
        }

        setContent {
            EnrollScreen(
                progress = uiState.progress,
                isError = uiState.isError,
                errorMessage = uiState.errorMessage,
                isFaceDetected = uiState.isFaceDetected,
                onBackPressed = { finish() }
            )
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != 0) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA)
        } else {
            hasCameraPermission = true
        }
    }

    override fun onResume() {
        super.onResume()
        isActivityPaused = false
        if (hasCameraPermission) {
            startEnrollment()
        }
    }

    override fun onPause() {
        super.onPause()
        isActivityPaused = true
        stopEnrollment()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putByteArray(Constants.EXTRA_KEY_CHALLENGE_TOKEN, token)
        outState.putInt(Intent.EXTRA_USER_ID, userId)
    }

    private fun startEnrollment() {
        uiState = EnrollUiState()
        enrolledSamples = 0

        if (token != null && token!!.isNotEmpty()) {
            if (Util.IS_DEBUG_LOGGING) Log.d(TAG, "Starting enrollment via FaceManager")
            faceManager?.enroll(
                Util.getUserId(this),
                token,
                enrollmentCancel,
                enrollmentCallback,
                intArrayOf(1)
            )
            handler.postDelayed(timeoutRunnable, ENROLL_TIMEOUT_MS)
        } else {
            Log.e(TAG, "No enrollment token!")
            finish()
        }
    }

    private fun stopEnrollment() {
        handler.removeCallbacks(timeoutRunnable)
        enrollmentCancel.cancel()
        enrollmentCancel = CancellationSignal()
    }

    private fun startFinishActivity() {
        val intent = Intent().apply {
            token?.let { putExtra(Constants.EXTRA_KEY_CHALLENGE_TOKEN, it) }
            if (userId != UserHandle.USER_NULL) {
                putExtra(Intent.EXTRA_USER_ID, userId)
            }
            component = ComponentName.unflattenFromString(
                "com.android.settings/com.android.settings.biometrics.face.FaceEnrollFinish"
            )
        }
        startActivityForResult(intent, REQUEST_FINISH)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA) {
            if (grantResults.isNotEmpty() && grantResults[0] == 0) {
                hasCameraPermission = true
            } else {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_FINISH) {
            setResult(resultCode)
            finish()
        }
    }

    companion object {
        private const val TAG = "EnrollActivity"
        private const val REQUEST_CAMERA = 0
        private const val REQUEST_FINISH = 1
        private const val ENROLL_TIMEOUT_MS = 15000L
    }
}
