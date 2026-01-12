/*
 * Copyright (C) 2025 AxionOS
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.sense.ml

import android.content.Context
import android.graphics.Bitmap
import android.provider.Settings
import co.aospa.sense.util.GpuDelegateHelper
import co.aospa.sense.util.Loggable
import co.aospa.sense.util.logD
import org.tensorflow.lite.*
import org.tensorflow.lite.nnapi.NnApiDelegate
import org.tensorflow.lite.support.common.*
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.*
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.*
import kotlin.math.*
import kotlinx.coroutines.*

class FaceNetModel(context: Context) : Loggable {

    private val imgSize = 160
    private val embeddingDim = 512

    private var interpreter: Interpreter
    private var gpuDelegate: Delegate? = null
    private var nnApiDelegate: NnApiDelegate? = null

    private val imageTensorProcessor = ImageProcessor.Builder()
        .add(ResizeOp(imgSize, imgSize, ResizeOp.ResizeMethod.BILINEAR))
        .add(NormalizeOp(127.5f, 127.5f))
        .build()

    init {
        val interpreterOptions = Interpreter.Options()
        
        gpuDelegate = GpuDelegateHelper.createGpuDelegate()
        if (gpuDelegate != null) {
            interpreterOptions.addDelegate(gpuDelegate)
            logD("FaceNetModel: Applied GPU Delegate")
            Settings.Secure.putInt(context.contentResolver, "face_recognition_gpu", 1)
        } else {
             try {
                nnApiDelegate = NnApiDelegate()
                interpreterOptions.addDelegate(nnApiDelegate)
                logD("FaceNetModel: Applied NNAPI Delegate")
            } catch (e: Exception) {
                logD("FaceNetModel: NNAPI Delegate unavailable: ${e.message}")
                interpreterOptions.numThreads = 4
                logD("FaceNetModel: Fallback to CPU with 4 threads")
            }
            Settings.Secure.putInt(context.contentResolver, "face_recognition_gpu", 0)
        }
        
        interpreter = Interpreter(FileUtil.loadMappedFile(context, "facenet_512.tflite"), interpreterOptions)
        logD("FaceNetModel initialized")
    }

    suspend fun getEmbedding(image: Bitmap): FloatArray = withContext(Dispatchers.IO) {
        val tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(image)
        val processedImage = imageTensorProcessor.process(tensorImage)
        runFaceNet(processedImage.buffer)[0]
    }

    private fun runFaceNet(inputs: Any): Array<FloatArray> {
        val outputs = Array(1) { FloatArray(embeddingDim) }
        interpreter.run(inputs, outputs)
        return outputs
    }

    fun cosineDistance(x1: FloatArray, x2: FloatArray): Float {
        var mag1 = 0.0f
        var mag2 = 0.0f
        var product = 0.0f
        for (i in x1.indices) {
            mag1 += x1[i].pow(2)
            mag2 += x2[i].pow(2)
            product += x1[i] * x2[i]
        }
        mag1 = sqrt(mag1)
        mag2 = sqrt(mag2)
        return product / (mag1 * mag2)
    }

    suspend fun compare(bitmap1: Bitmap, bitmap2: Bitmap): Float {
        val embed1 = getEmbedding(bitmap1)
        val embed2 = getEmbedding(bitmap2)
        return cosineDistance(embed1, embed2)
    }

    fun close() {
        interpreter.close()
        (gpuDelegate as? AutoCloseable)?.close()
        nnApiDelegate?.close()
    }
}
