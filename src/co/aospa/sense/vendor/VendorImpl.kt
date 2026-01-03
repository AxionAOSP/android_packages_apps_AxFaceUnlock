/*
 * Copyright (C) 2025 AxionOS
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.sense.vendor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import co.aospa.sense.ml.*
import co.aospa.sense.util.Constants
import co.aospa.sense.util.Loggable
import co.aospa.sense.util.logD
import co.aospa.sense.util.Util
import kotlinx.coroutines.*
import kotlin.math.*

class VendorImpl(private val context: Context) : Loggable {

    private var detector: FaceDetector? = null
    private var database: EmbeddingDatabase? = null
    private var recognition: FaceRecognition? = null
    private var lastEnrollTime = 0L
    private var lastProcessTime = 0L
    private var firstEnrollmentEmbedding: FloatArray? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun init() {
        runBlocking {
            logD("Initializing models...")
            try {
                if (detector == null) detector = FaceDetector(context)
                if (database == null) database = EmbeddingDatabase(context)
                if (recognition == null) {
                    recognition = FaceRecognition(
                        FaceNetModel(context),
                        AntiSpoofModel(context),
                        database!!
                    )
                }
                logD("Initialization complete")
            } catch (e: Exception) {
                logD("Initialization failed: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun release() {
        logD("Releasing resources")
        scope.cancel()
        detector?.close()
        detector = null
        recognition = null
    }

    fun compare(frameBitmap: Bitmap): Int = runBlocking {
        if (recognition == null || detector == null) {
            return@runBlocking Constants.MSG_UNLOCK_FAILED
        }

        val start = System.currentTimeMillis()
        val face = detector!!.getCroppedFace(frameBitmap)
            ?: return@runBlocking Constants.MSG_UNLOCK_FACE_NOT_FOUND.also {
                logD("No face detected")
            }

        logD("Face detected: ${face.croppedFace.width}x${face.croppedFace.height}")

        val userId = Util.getUserId(context)
        val result = recognition!!.recognize(userId, face.croppedFace, face.frameBitmap, face.faceRect)

        logD("Recognition took ${System.currentTimeMillis() - start}ms")

        if (result.spoof?.isSpoof == true) {
            logD("SPOOF DETECTED: confidence=${result.spoof.score}")
            return@runBlocking Constants.MSG_UNLOCK_FAILED
        }

        if (result.matched) {
            logD("MATCHED: similarity=${result.similarity}")
            0
        } else {
            logD("NOT MATCHED: similarity=${result.similarity}")
            Constants.MSG_UNLOCK_FAILED
        }
    }

    fun saveFeature(frameBitmap: Bitmap): Int = runBlocking {
        if (recognition == null || detector == null || database == null) {
            logD("Not initialized for enrollment")
            return@runBlocking -1
        }

        val now = System.currentTimeMillis()
        if (now - lastProcessTime < FRAME_INTERVAL_MS) {
            return@runBlocking Constants.MSG_UNLOCK_KEEP
        }
        lastProcessTime = now

        val face = detector!!.getCroppedFace(frameBitmap)
            ?: return@runBlocking -1.also { logD("No face for enrollment") }

        val userId = Util.getUserId(context)
        val count = database!!.count(userId)

        if (!isValidPose(face.keypoints)) {
            return@runBlocking Constants.MSG_UNLOCK_KEEP
        }

        val embedding = recognition!!.getEmbedding(face.croppedFace)

        val spoofResult = recognition!!.checkSpoof(face.croppedFace)
        if (spoofResult.isSpoof) {
            logD("SPOOF detected during enrollment: score=${spoofResult.score}")
            return@runBlocking Constants.MSG_UNLOCK_FAILED
        }

        if (count > 0 && firstEnrollmentEmbedding != null) {
            val similarity = recognition!!.calculateSimilarity(embedding, firstEnrollmentEmbedding!!)
            logD("Face similarity to first enrolled: $similarity")
            if (similarity < FACE_MATCH_THRESHOLD) {
                logD("Different face detected during enrollment, returning error")
                return@runBlocking Constants.MSG_UNLOCK_FAILED
            }
        } else if (count == 0) {
            firstEnrollmentEmbedding = embedding
        }

        lastEnrollTime = now
        recognition!!.addFaceWithEmbedding(userId, embedding)

        val newCount = database!!.count(userId)
        logD("Enrolled $newCount/$ENROLL_COUNT")

        if (newCount >= ENROLL_COUNT) {
            logD("Enrollment complete")
            firstEnrollmentEmbedding = null
            0
        } else {
            Constants.MSG_UNLOCK_KEEP
        }
    }

    fun deleteFeature(faceId: Int) {
        val userId = Util.getUserId(context)
        database?.clear(userId)
        firstEnrollmentEmbedding = null
    }

    fun compareStart() {
        database?.reload()
    }

    fun getEnrolledCount(userId: Int): Int {
        return database?.count(userId) ?: 0
    }

    private fun isValidPose(keypoints: List<PointF>): Boolean {
        if (keypoints.size < 6) return true
        val eyeSpacing = abs(keypoints[1].x - keypoints[0].x)
        val valid = eyeSpacing >= 20f
        if (!valid) logD("Face too small: eyeSpacing=$eyeSpacing")
        return valid
    }

    companion object {
        private const val ENROLL_COUNT = 5
        private const val ENROLL_COOLDOWN_MS = 800L
        private const val FRAME_INTERVAL_MS = 1250L
        private const val FACE_MATCH_THRESHOLD = 0.5f
    }
}
