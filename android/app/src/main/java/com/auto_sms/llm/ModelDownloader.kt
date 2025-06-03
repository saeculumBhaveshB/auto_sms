package com.auto_sms.llm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.channels.ReadableByteChannel

/**
 * Utility class for downloading and managing LLM models
 */
class ModelDownloader(private val context: Context) {
    private val TAG = "ModelDownloader"
    
    companion object {
        // URL for the Phi-3-mini 4-bit quantized model
        // Note: In a real app, you would host this file on a CDN or use a more robust download method
        const val PHI3_MINI_Q4_URL = "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q4.gguf"
        
        // Model file names
        const val PHI3_MINI_Q4_FILENAME = "phi-3-mini-q4.gguf"
    }
    
    /**
     * Downloads the Phi-3-mini model if it doesn't exist
     * @return The path to the model file
     */
    suspend fun downloadPhiModelIfNeeded(): String = withContext(Dispatchers.IO) {
        val modelsDir = File(context.filesDir, "models")
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
        
        val modelFile = File(modelsDir, PHI3_MINI_Q4_FILENAME)
        
        // If the model already exists and is not empty, return its path
        if (modelFile.exists() && modelFile.length() > 0) {
            Log.d(TAG, "✅ Model already exists at ${modelFile.absolutePath}")
            return@withContext modelFile.absolutePath
        }
        
        // Download the model
        try {
            Log.d(TAG, "📥 Downloading Phi-3-mini model from $PHI3_MINI_Q4_URL")
            
            val url = URL(PHI3_MINI_Q4_URL)
            val connection = url.openConnection()
            connection.connectTimeout = 30000 // 30 seconds
            connection.readTimeout = 300000  // 5 minutes
            
            val contentLength = connection.contentLength
            Log.d(TAG, "📊 Model size: ${contentLength / (1024 * 1024)} MB")
            
            val inputChannel: ReadableByteChannel = Channels.newChannel(connection.getInputStream())
            val outputStream = FileOutputStream(modelFile)
            val fileChannel: FileChannel = outputStream.channel
            
            var bytesTransferred: Long = 0
            val buffer = 1024 * 1024 * 10 // 10MB buffer
            var position: Long = 0
            
            while (bytesTransferred < contentLength) {
                val transferred = fileChannel.transferFrom(inputChannel, position, buffer.toLong())
                if (transferred <= 0) break
                position += transferred
                bytesTransferred += transferred
                
                // Log progress
                val progress = (bytesTransferred.toDouble() / contentLength.toDouble() * 100).toInt()
                Log.d(TAG, "⏳ Download progress: $progress%")
            }
            
            fileChannel.close()
            outputStream.close()
            inputChannel.close()
            
            Log.d(TAG, "✅ Model downloaded successfully to ${modelFile.absolutePath}")
            return@withContext modelFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error downloading model: ${e.message}")
            throw e
        }
    }
    
    /**
     * Checks if the Phi-3-mini model exists
     * @return True if the model exists, false otherwise
     */
    fun doesPhiModelExist(): Boolean {
        val modelsDir = File(context.filesDir, "models")
        val modelFile = File(modelsDir, PHI3_MINI_Q4_FILENAME)
        return modelFile.exists() && modelFile.length() > 0
    }
    
    /**
     * Gets the path to the Phi-3-mini model file
     * @return The path to the model file, or null if it doesn't exist
     */
    fun getPhiModelPath(): String? {
        val modelsDir = File(context.filesDir, "models")
        val modelFile = File(modelsDir, PHI3_MINI_Q4_FILENAME)
        
        return if (modelFile.exists() && modelFile.length() > 0) {
            modelFile.absolutePath
        } else {
            null
        }
    }
} 