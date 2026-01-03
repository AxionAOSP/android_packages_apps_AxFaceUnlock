/*
 * Copyright (C) 2025 AxionOS
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.sense.ml

import android.content.Context
import android.graphics.Bitmap
import co.aospa.sense.util.Loggable
import co.aospa.sense.util.logD
import org.tensorflow.lite.*
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

    private val imageTensorProcessor = ImageProcessor.Builder()
        .add(ResizeOp(imgSize, imgSize, ResizeOp.ResizeMethod.BILINEAR))
        .add(StandardizeOp())
        .build()

    init {
        val interpreterOptions = Interpreter.Options().apply {
            numThreads = 4
        }
        interpreter = Interpreter(FileUtil.loadMappedFile(context, "facenet_512.tflite"), interpreterOptions)
        logD("FaceNetModel initialized (FaceNet-512, CPU)")
    }

    suspend fun getEmbedding(image: Bitmap): FloatArray = withContext(Dispatchers.IO) {
        runFaceNet(convertBitmapToBuffer(image))[0]
    }

    private fun runFaceNet(inputs: Any): Array<FloatArray> {
        val outputs = Array(1) { FloatArray(embeddingDim) }
        interpreter.run(inputs, outputs)
        return outputs
    }

    private fun convertBitmapToBuffer(image: Bitmap) = 
        imageTensorProcessor.process(TensorImage.fromBitmap(image)).buffer

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

    fun close() = interpreter.close()

    class StandardizeOp : TensorOperator {
        override fun apply(p0: TensorBuffer?): TensorBuffer {
            val pixels = p0!!.floatArray
            val mean = pixels.average().toFloat()
            var std = sqrt(pixels.map { pi -> (pi - mean).pow(2) }.sum() / pixels.size.toFloat())
            std = max(std, 1f / sqrt(pixels.size.toFloat()))
            for (i in pixels.indices) {
                pixels[i] = (pixels[i] - mean) / std
            }
            val output = TensorBufferFloat.createFixedSize(p0.shape, DataType.FLOAT32)
            output.loadArray(pixels)
            return output
        }
    }
}
