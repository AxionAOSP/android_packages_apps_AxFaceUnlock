/*
 * Copyright (C) 2025 AxionOS
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.sense.ml

import android.graphics.*
import co.aospa.sense.util.Loggable
import co.aospa.sense.util.logD
import kotlin.math.*

import kotlinx.coroutines.*

class FaceRecognition(
    private val faceNet: FaceNetModel,
    private val antiSpoof: AntiSpoofModel,
    private val database: EmbeddingDatabase
) : Loggable {

    data class Result(
        val matched: Boolean,
        val similarity: Float,
        val boundingBox: Rect,
        val spoof: AntiSpoofModel.Result? = null
    )

    suspend fun addFace(userId: Int, croppedFace: Bitmap) = withContext(Dispatchers.IO) {
        val embedding = faceNet.getEmbedding(croppedFace)
        database.save(userId, embedding)
        logD("Added face for user $userId")
    }

    suspend fun addFaceWithEmbedding(userId: Int, embedding: FloatArray) = withContext(Dispatchers.IO) {
        database.save(userId, embedding)
        logD("Added face embedding for user $userId")
    }

    suspend fun getEmbedding(croppedFace: Bitmap): FloatArray = withContext(Dispatchers.IO) {
        faceNet.getEmbedding(croppedFace)
    }

    fun calculateSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        return faceNet.cosineDistance(embedding1, embedding2)
    }

    suspend fun checkSpoof(croppedFace: Bitmap): AntiSpoofModel.Result = withContext(Dispatchers.IO) {
        antiSpoof.detect(croppedFace)
    }

    suspend fun recognize(userId: Int, croppedFace: Bitmap, frame: Bitmap, faceRect: Rect): Result = withContext(Dispatchers.IO) {
        val stored = database[userId]

        if (stored.isNullOrEmpty()) {
            logD("No stored faces for user $userId")
            return@withContext Result(false, 0f, faceRect)
        }

        val capturedEmbedding = faceNet.getEmbedding(croppedFace)
        val similarity = stored.maxOf { storedEmbedding ->
            faceNet.cosineDistance(capturedEmbedding, storedEmbedding)
        }
        
        val spoof = antiSpoof.detect(croppedFace)
        val matched = similarity > THRESHOLD && !spoof.isSpoof

        logD("Recognition: sim=$similarity threshold=$THRESHOLD matched=$matched spoof=${spoof.isSpoof}")
        Result(matched, similarity, faceRect, spoof)
    }

    fun removeFaces(userId: Int) {
        database.clear(userId)
        logD("Removed faces for user $userId")
    }

    companion object {
        private const val THRESHOLD = 0.4f
    }
}
