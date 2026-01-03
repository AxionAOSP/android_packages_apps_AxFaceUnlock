/*
 * Copyright (C) 2025 AxionOS
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.sense.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import co.aospa.sense.util.Loggable
import co.aospa.sense.util.logD
import kotlinx.coroutines.*
import java.io.*

class EmbeddingDatabase(context: Context) : Loggable {

    private val embeddings: MutableMap<Int, MutableList<FloatArray>> = mutableMapOf()
    private val lock = Any()
    private val scope = CoroutineScope(Dispatchers.IO)
    private val filesDir = context.filesDir

    init {
        load()
    }

    fun save(userId: Int, embedding: FloatArray) {
        synchronized(lock) {
            val list = embeddings.getOrPut(userId) { mutableListOf() }
            list.add(embedding)
            saveEmbeddingToFile(userId, embedding)
            logD("Saved embedding #${list.size} for user $userId")
        }
    }

    operator fun get(userId: Int): List<FloatArray>? = synchronized(lock) {
        embeddings[userId]?.toList()
    }

    fun count(userId: Int): Int = synchronized(lock) {
        embeddings[userId]?.size ?: 0
    }

    fun hasData(userId: Int): Boolean = synchronized(lock) {
        embeddings[userId]?.isNotEmpty() == true
    }

    fun clear(userId: Int) {
        synchronized(lock) {
            embeddings.remove(userId)
            deleteEmbeddingsForUser(userId)
            logD("Cleared embeddings for user $userId")
        }
    }

    fun reload() {
        scope.launch { synchronized(lock) { load() } }
    }

    private fun saveEmbeddingToFile(userId: Int, embedding: FloatArray) {
        try {
            val filename = "face_${userId}_${System.currentTimeMillis()}.emb"
            val file = File(filesDir, filename)
            DataOutputStream(FileOutputStream(file)).use { out ->
                out.writeInt(embedding.size)
                for (value in embedding) {
                    out.writeFloat(value)
                }
            }
        } catch (e: Exception) {
            logD("Failed to save embedding: ${e.message}")
        }
    }

    private fun deleteEmbeddingsForUser(userId: Int) {
        try {
            filesDir.listFiles { _, name -> name.startsWith("face_${userId}_") }?.forEach {
                it.delete()
            }
        } catch (e: Exception) {
            logD("Failed to delete embeddings: ${e.message}")
        }
    }

    private fun load() {
        try {
            embeddings.clear()
            filesDir.listFiles { _, name -> name.startsWith("face_") && name.endsWith(".emb") }?.forEach { file ->
                try {
                    val parts = file.name.split("_")
                    if (parts.size >= 2) {
                        val userId = parts[1].toInt()
                        DataInputStream(FileInputStream(file)).use { input ->
                            val size = input.readInt()
                            val embedding = FloatArray(size)
                            for (i in 0 until size) {
                                embedding[i] = input.readFloat()
                            }
                            embeddings.getOrPut(userId) { mutableListOf() }.add(embedding)
                        }
                    }
                } catch (e: Exception) {
                    logD("Failed to load file ${file.name}: ${e.message}")
                }
            }
            logD("Loaded embeddings for ${embeddings.size} users")
        } catch (e: Exception) {
            logD("Failed to load embeddings: ${e.message}")
        }
    }
}
