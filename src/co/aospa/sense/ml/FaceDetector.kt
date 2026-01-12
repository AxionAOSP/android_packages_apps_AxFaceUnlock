/*
 * Copyright (C) 2025 AxionOS
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.sense.ml

import android.content.Context
import android.graphics.*
import co.aospa.sense.util.GpuDelegateHelper
import co.aospa.sense.util.Loggable
import co.aospa.sense.util.logD
import org.tensorflow.lite.*
import org.tensorflow.lite.nnapi.NnApiDelegate
import org.tensorflow.lite.support.common.*
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.*
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*
import kotlinx.coroutines.*

class FaceDetector(context: Context) : Loggable {

    companion object {
        private const val MODEL_FILE = "blaze_face_short_range.tflite"
        private const val INPUT_SIZE = 128
        private const val NUM_BOXES = 896
        private const val NUM_COORDS = 16
        private const val SCORE_THRESHOLD = 0.5f
        private const val NMS_THRESHOLD = 0.3f
        private const val MIN_FACE_SIZE = 40f
        private const val FACE_PADDING = 0.15f
    }

    data class Detection(
        val boundingBox: RectF,
        val score: Float,
        val keypoints: List<PointF>
    )

    data class FaceResult(
        val croppedFace: Bitmap,
        val keypoints: List<PointF>,
        val faceSize: Int,
        val frameBitmap: Bitmap,
        val faceRect: Rect
    )

    private var interpreter: Interpreter? = null
    private var gpuDelegate: Delegate? = null
    private var nnApiDelegate: NnApiDelegate? = null
    private val anchors: FloatArray by lazy { generateAnchors() }
    
    private val imageTensorProcessor = ImageProcessor.Builder()
        .add(ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
        .add(NormalizeOp(127.5f, 127.5f))
        .build()

    init {
        try {
            val options = Interpreter.Options()
            
            gpuDelegate = GpuDelegateHelper.createGpuDelegate()
            if (gpuDelegate != null) {
                options.addDelegate(gpuDelegate)
                logD("FaceDetector: Applied GPU Delegate")
            } else {
                try {
                    nnApiDelegate = NnApiDelegate()
                    options.addDelegate(nnApiDelegate)
                    logD("FaceDetector: Applied NNAPI Delegate")
                } catch (e: Exception) {
                    logD("FaceDetector: NNAPI Delegate unavailable: ${e.message}")
                    options.numThreads = 2
                    logD("FaceDetector: Fallback to CPU with 2 threads")
                }
            }
            
            interpreter = Interpreter(FileUtil.loadMappedFile(context, MODEL_FILE), options)
            logD("BlazeFace detector initialized")
        } catch (e: Exception) {
            logD("Failed to initialize BlazeFace: ${e.message}")
        }
    }

    suspend fun detectFaces(bitmap: Bitmap): List<Detection> = withContext(Dispatchers.IO) {
        val interp = interpreter ?: return@withContext emptyList()

        val tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(bitmap)
        val processedImage = imageTensorProcessor.process(tensorImage)
        val inputBuffer = processedImage.buffer

        val regressors = Array(1) { Array(NUM_BOXES) { FloatArray(NUM_COORDS) } }
        val classifiers = Array(1) { Array(NUM_BOXES) { FloatArray(1) } }

        val outputs = mapOf(
            0 to regressors,
            1 to classifiers
        )

        try {
            interp.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)
        } catch (e: Exception) {
            logD("Inference failed: ${e.message}")
            return@withContext emptyList()
        }

        val detections = mutableListOf<Detection>()
        val scaleX = bitmap.width.toFloat()
        val scaleY = bitmap.height.toFloat()

        for (i in 0 until NUM_BOXES) {
            val score = sigmoid(classifiers[0][i][0])
            if (score > SCORE_THRESHOLD) {
                val anchorX = anchors[i * 4]
                val anchorY = anchors[i * 4 + 1]

                val cx = (regressors[0][i][0] / INPUT_SIZE + anchorX) * scaleX
                val cy = (regressors[0][i][1] / INPUT_SIZE + anchorY) * scaleY
                val w = (regressors[0][i][2] / INPUT_SIZE) * scaleX
                val h = (regressors[0][i][3] / INPUT_SIZE) * scaleY

                val left = cx - w / 2
                val top = cy - h / 2
                val right = cx + w / 2
                val bottom = cy + h / 2

                val keypoints = mutableListOf<PointF>()
                for (j in 0 until 6) {
                    val kx = (regressors[0][i][4 + (j * 2)] / INPUT_SIZE + anchorX) * scaleX
                    val ky = (regressors[0][i][4 + (j * 2) + 1] / INPUT_SIZE + anchorY) * scaleY
                    keypoints.add(PointF(kx, ky))
                }

                if (w > MIN_FACE_SIZE && h > MIN_FACE_SIZE) {
                    detections.add(Detection(
                        RectF(left, top, right, bottom),
                        score,
                        keypoints
                    ))
                }
            }
        }

        nonMaxSuppression(detections)
    }

    suspend fun getCroppedFace(frameBitmap: Bitmap): FaceResult? = withContext(Dispatchers.IO) {
        val detections = detectFaces(frameBitmap)

        if (detections.isEmpty()) {
            return@withContext null
        }

        val largest = detections.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
            ?: return@withContext null

        val rect = largest.boundingBox
        val padW = rect.width() * FACE_PADDING
        val padH = rect.height() * FACE_PADDING

        val left = max(0f, rect.left - padW).toInt()
        val top = max(0f, rect.top - padH).toInt()
        val right = min(frameBitmap.width.toFloat(), rect.right + padW).toInt()
        val bottom = min(frameBitmap.height.toFloat(), rect.bottom + padH).toInt()

        if (right <= left || bottom <= top) return@withContext null

        val croppedFace = Bitmap.createBitmap(frameBitmap, left, top, right - left, bottom - top)

        val faceRect = Rect(
            rect.left.toInt(),
            rect.top.toInt(),
            rect.right.toInt(),
            rect.bottom.toInt()
        )

        FaceResult(
            croppedFace,
            largest.keypoints,
            rect.width().toInt(),
            frameBitmap,
            faceRect
        )
    }

    private fun generateAnchors(): FloatArray {
        val anchors = mutableListOf<Float>()

        val strides = intArrayOf(8, 16)
        val anchorCounts = intArrayOf(2, 6)

        for ((idx, stride) in strides.withIndex()) {
            val gridRows = INPUT_SIZE / stride
            val gridCols = INPUT_SIZE / stride
            val anchorCount = anchorCounts[idx]

            for (y in 0 until gridRows) {
                for (x in 0 until gridCols) {
                    val anchorX = (x + 0.5f) / gridCols
                    val anchorY = (y + 0.5f) / gridRows

                    repeat(anchorCount) {
                        anchors.add(anchorX)
                        anchors.add(anchorY)
                        anchors.add(1f)
                        anchors.add(1f)
                    }
                }
            }
        }

        return anchors.toFloatArray()
    }

    private fun sigmoid(x: Float): Float = 1f / (1f + exp(-x))

    private fun nonMaxSuppression(detections: List<Detection>): List<Detection> {
        if (detections.isEmpty()) return emptyList()

        val sorted = detections.sortedByDescending { it.score }.toMutableList()
        val result = mutableListOf<Detection>()

        while (sorted.isNotEmpty()) {
            val best = sorted.removeAt(0)
            result.add(best)

            sorted.removeAll { iou(best.boundingBox, it.boundingBox) > NMS_THRESHOLD }
        }

        return result
    }

    private fun iou(a: RectF, b: RectF): Float {
        val intersection = RectF()
        if (!intersection.setIntersect(a, b)) return 0f

        val intersectionArea = intersection.width() * intersection.height()
        val unionArea = a.width() * a.height() + b.width() * b.height() - intersectionArea

        return if (unionArea > 0) intersectionArea / unionArea else 0f
    }

    fun close() {
        interpreter?.close()
        interpreter = null
        (gpuDelegate as? AutoCloseable)?.close()
        nnApiDelegate?.close()
    }
}
