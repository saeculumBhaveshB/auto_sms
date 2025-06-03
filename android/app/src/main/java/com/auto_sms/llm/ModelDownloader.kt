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
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Utility class for downloading and managing LLM models
 */
class ModelDownloader(private val context: Context) {
    private val TAG = "ModelDownloader"
    
    companion object {
        // URL for the Phi-3-mini 4-bit quantized model
        // Note: In a real app, you would host this file on a CDN or use a more robust download method
        const val PHI3_MINI_Q4_URL = "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q4.gguf"
        
        // Fallback URL for a smaller model
        const val PHI3_MINI_Q4_FALLBACK_URL = "https://huggingface.co/TheBloke/phi-2-GGUF/resolve/main/phi-2.Q4_K_M.gguf"
        
        // URL for a tiny test model (~50MB) - for testing only
        const val TEST_MODEL_URL = "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf"
        
        // Model file names
        const val PHI3_MINI_Q4_FILENAME = "phi-3-mini-q4.gguf"
        const val PHI3_MINI_Q4_FALLBACK_FILENAME = "phi-2-q4.gguf"
        const val TEST_MODEL_FILENAME = "tinyllama-q4.gguf"
        
        // For development/testing, set this to true to use the test model
        const val USE_TEST_MODEL = true
    }
    
    // OkHttp client configured for large downloads
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .build()
    
    /**
     * Downloads the Phi-3-mini model if it doesn't exist
     * @return The path to the model file
     */
    suspend fun downloadPhiModelIfNeeded(): String = withContext(Dispatchers.IO) {
        val filename = when {
            USE_TEST_MODEL -> TEST_MODEL_FILENAME
            else -> PHI3_MINI_Q4_FILENAME
        }
        
        val url = when {
            USE_TEST_MODEL -> TEST_MODEL_URL
            else -> PHI3_MINI_Q4_URL
        }
        
        // Create models directory in app's files directory
        val modelsDir = File(context.filesDir, "models")
        val cacheDir = context.cacheDir
        
        Log.d(TAG, "📁 App files directory: ${context.filesDir.absolutePath}")
        Log.d(TAG, "📁 App cache directory: ${cacheDir.absolutePath}")
        
        if (!modelsDir.exists()) {
            Log.d(TAG, "📁 Creating models directory at ${modelsDir.absolutePath}")
            val created = modelsDir.mkdirs()
            if (!created) {
                Log.e(TAG, "❌ Failed to create models directory at ${modelsDir.absolutePath}")
                throw IOException("Failed to create models directory")
            }
        }
        
        // Check available space 
        val freeSpace = modelsDir.freeSpace / (1024 * 1024) // MB
        Log.d(TAG, "📊 Free space available: $freeSpace MB")
        
        if (freeSpace < 1000) { // Need at least 1GB free
            Log.e(TAG, "❌ Not enough free space to download model, only $freeSpace MB available")
            throw IOException("Not enough free space to download model (need at least 1GB)")
        }
        
        val modelFile = File(modelsDir, filename)
        Log.d(TAG, "🔍 Checking for model file at ${modelFile.absolutePath}")
        
        // If the model already exists and is not empty, return its path
        if (modelFile.exists() && modelFile.length() > 0) {
            Log.d(TAG, "✅ Model already exists at ${modelFile.absolutePath} with size ${modelFile.length() / (1024 * 1024)} MB")
            return@withContext modelFile.absolutePath
        }
        
        // Download the model using OkHttp
        try {
            Log.d(TAG, "📥 Downloading model from $url to $filename")
            
            // Create a temporary file for downloading
            val tempFile = File(cacheDir, "$filename.download")
            if (tempFile.exists()) {
                Log.d(TAG, "🧹 Deleting existing temp file")
                tempFile.delete()
            }
            
            val downloadSuccess = downloadWithOkHttp(url, tempFile)
            
            if (downloadSuccess && tempFile.exists() && tempFile.length() > 0) {
                // Move from temp file to final location
                if (modelFile.exists()) modelFile.delete()
                
                val copied = tempFile.copyTo(modelFile, overwrite = true)
                tempFile.delete()
                
                Log.d(TAG, "✅ Model downloaded successfully to ${modelFile.absolutePath} with size ${modelFile.length() / (1024 * 1024)} MB")
                return@withContext modelFile.absolutePath
            } else {
                Log.e(TAG, "❌ Download failed or temp file is empty")
                
                // Try fallback URL if we're not already using test model
                if (!USE_TEST_MODEL) {
                    Log.d(TAG, "🔄 Trying fallback URL")
                    val fallbackUrl = PHI3_MINI_Q4_FALLBACK_URL
                    val fallbackFile = File(modelsDir, PHI3_MINI_Q4_FALLBACK_FILENAME)
                    
                    val fallbackSuccess = downloadWithOkHttp(fallbackUrl, tempFile)
                    
                    if (fallbackSuccess && tempFile.exists() && tempFile.length() > 0) {
                        if (fallbackFile.exists()) fallbackFile.delete()
                        
                        val copied = tempFile.copyTo(fallbackFile, overwrite = true)
                        tempFile.delete()
                        
                        Log.d(TAG, "✅ Fallback model downloaded successfully to ${fallbackFile.absolutePath} with size ${fallbackFile.length() / (1024 * 1024)} MB")
                        return@withContext fallbackFile.absolutePath
                    }
                }
                
                throw IOException("Failed to download model from any source")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error downloading model: ${e.message}", e)
            throw IOException("Error downloading model: ${e.message}", e)
        }
    }
    
    /**
     * Downloads a file using OkHttp with progress reporting
     */
    private suspend fun downloadWithOkHttp(url: String, targetFile: File): Boolean = suspendCoroutine { continuation ->
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "❌ OkHttp download failed: ${e.message}")
                    continuation.resumeWithException(e)
                }
                
                override fun onResponse(call: Call, response: Response) {
                    if (!response.isSuccessful) {
                        Log.e(TAG, "❌ HTTP error: ${response.code}")
                        continuation.resume(false)
                        return
                    }
                    
                    var outputStream: FileOutputStream? = null
                    try {
                        val contentLength = response.body?.contentLength() ?: -1
                        Log.d(TAG, "📊 Model size: ${contentLength / (1024 * 1024)} MB")
                        
                        outputStream = FileOutputStream(targetFile)
                        val input = response.body?.byteStream()
                        
                        if (input == null) {
                            Log.e(TAG, "❌ Response body is null")
                            continuation.resume(false)
                            return
                        }
                        
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalBytesRead: Long = 0
                        var lastProgressUpdate = 0L
                        
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            
                            // Update progress every 1MB
                            if (contentLength > 0 && totalBytesRead - lastProgressUpdate > 1024 * 1024) {
                                val progress = (totalBytesRead.toDouble() / contentLength.toDouble() * 100).toInt()
                                Log.d(TAG, "⏳ Download progress: $progress% ($totalBytesRead/${contentLength} bytes)")
                                lastProgressUpdate = totalBytesRead
                            }
                        }
                        
                        outputStream.flush()
                        Log.d(TAG, "✅ Download complete: $totalBytesRead bytes")
                        continuation.resume(true)
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error during OkHttp download: ${e.message}")
                        continuation.resumeWithException(e)
                    } finally {
                        outputStream?.close()
                        response.body?.close()
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting up OkHttp download: ${e.message}")
            continuation.resumeWithException(e)
        }
    }
    
    /**
     * Checks if the Phi-3-mini model exists
     * @return True if the model exists, false otherwise
     */
    fun doesPhiModelExist(): Boolean {
        val modelsDir = File(context.filesDir, "models")
        
        val filesToCheck = listOf(
            File(modelsDir, PHI3_MINI_Q4_FILENAME),
            File(modelsDir, PHI3_MINI_Q4_FALLBACK_FILENAME),
            File(modelsDir, TEST_MODEL_FILENAME)
        )
        
        return filesToCheck.any { it.exists() && it.length() > 0 }
    }
    
    /**
     * Gets the path to the Phi-3-mini model file
     * @return The path to the model file, or null if it doesn't exist
     */
    fun getPhiModelPath(): String? {
        val modelsDir = File(context.filesDir, "models")
        
        val filesToCheck = listOf(
            File(modelsDir, if (USE_TEST_MODEL) TEST_MODEL_FILENAME else PHI3_MINI_Q4_FILENAME),
            File(modelsDir, PHI3_MINI_Q4_FALLBACK_FILENAME),
            File(modelsDir, TEST_MODEL_FILENAME)
        )
        
        for (file in filesToCheck) {
            if (file.exists() && file.length() > 0) {
                Log.d(TAG, "✅ Found model at ${file.absolutePath} with size ${file.length() / (1024 * 1024)} MB")
                return file.absolutePath
            }
        }
        
        return null
    }
} 