package com.auto_sms.llm

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.facebook.react.bridge.*
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.system.measureTimeMillis

/**
 * LocalLLMModule - Native module for running local LLM models on device
 * Uses llama.cpp to provide local inference capabilities
 */
class LocalLLMModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    private val TAG = "LocalLLMModule"
    private val modelExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var isModelLoaded = false
    private var modelPath: String? = null
    private var currentScope: CoroutineScope? = null

    override fun getName(): String {
        return "LocalLLMModule"
    }

    @ReactMethod
    fun getDeviceInfo(promise: Promise) {
        try {
            // Get the ActivityManager service to access real device memory info
            val activityManager = reactApplicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)

            // Calculate total and available memory in MB
            val totalMemoryMB = memoryInfo.totalMem / (1024 * 1024)
            val availableMemoryMB = memoryInfo.availMem / (1024 * 1024)

            Log.d(TAG, "Real Device Memory - Total: $totalMemoryMB MB, Available: $availableMemoryMB MB")
            
            val deviceInfoMap = Arguments.createMap().apply {
                putString("manufacturer", Build.MANUFACTURER)
                putString("model", Build.MODEL)
                putString("device", Build.DEVICE)
                putString("product", Build.PRODUCT)
                putString("hardware", Build.HARDWARE)
                putInt("sdk", Build.VERSION.SDK_INT)
                putString("cpuAbi", Build.SUPPORTED_ABIS.joinToString())
                putInt("cores", Runtime.getRuntime().availableProcessors())
                // Use the real device memory info instead of JVM heap
                putDouble("freeMemoryMB", availableMemoryMB.toDouble())
                putDouble("totalMemoryMB", totalMemoryMB.toDouble())
            }
            promise.resolve(deviceInfoMap)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting device info", e)
            promise.reject("ERROR_DEVICE_INFO", "Failed to get device info: ${e.message}")
        }
    }

    @ReactMethod
    fun downloadModel(modelUrl: String, modelName: String, promise: Promise) {
        // This is a placeholder. In a real implementation, we would download the model
        // from a URL or use a pre-bundled model in the assets folder
        val modelsDir = File(reactApplicationContext.filesDir, "models")
        if (!modelsDir.exists()) {
            modelsDir.mkdir()
        }
        
        val modelFile = File(modelsDir, modelName)
        if (modelFile.exists()) {
            modelPath = modelFile.absolutePath
            promise.resolve(modelPath)
        } else {
            promise.reject("MODEL_NOT_FOUND", "Model needs to be downloaded or copied from assets")
        }
    }

    @ReactMethod
    fun loadModel(modelPath: String, promise: Promise) {
        if (currentScope != null) {
            currentScope?.cancel()
        }
        
        currentScope = CoroutineScope(Dispatchers.IO)
        currentScope?.launch {
            try {
                // In a real implementation, this would initialize llama.cpp with the model
                val loadTime = measureTimeMillis {
                    // Simulate model loading time
                    delay(2000)
                }
                
                this@LocalLLMModule.modelPath = modelPath
                isModelLoaded = true
                
                withContext(Dispatchers.Main) {
                    Log.d(TAG, "Model loaded in $loadTime ms")
                    promise.resolve(true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading model", e)
                withContext(Dispatchers.Main) {
                    promise.reject("MODEL_LOAD_ERROR", "Failed to load model: ${e.message}")
                }
            }
        }
    }
    
    @ReactMethod
    fun isModelLoaded(promise: Promise) {
        promise.resolve(isModelLoaded)
    }
    
    @ReactMethod(isBlockingSynchronousMethod = true)
    fun isModelLoadedSync(): Boolean {
        return isModelLoaded
    }
    
    @ReactMethod
    fun unloadModel(promise: Promise) {
        if (isModelLoaded) {
            isModelLoaded = false
            modelPath = null
            
            // In a real implementation, this would clean up llama.cpp resources
            promise.resolve(true)
        } else {
            promise.resolve(false)
        }
    }

    @ReactMethod
    fun generateAnswer(question: String, temperature: Float, maxTokens: Int, promise: Promise) {
        if (!isModelLoaded) {
            promise.reject("MODEL_NOT_LOADED", "Model is not loaded")
            return
        }
        
        currentScope = CoroutineScope(Dispatchers.IO)
        currentScope?.launch {
            try {
                Log.d(TAG, "Generating answer for: $question")
                
                // In a real implementation, this would call llama.cpp to generate a response
                val inferenceTime = measureTimeMillis {
                    // Simulate inference time
                    delay(1500)
                }
                
                // Create a simple response based on the question for demonstration
                val response = if (question.lowercase().contains("hello") 
                    || question.lowercase().contains("hi")) {
                    "AI: Hello! I'm a local LLM running on your device. How can I help you today?"
                } else if (question.lowercase().contains("who are you") 
                    || question.lowercase().contains("what are you")) {
                    "AI: I am a local language model running directly on your device for privacy. I can answer questions based on documents you've uploaded."
                } else if (question.lowercase().contains("how") 
                    && question.lowercase().contains("work")) {
                    "AI: I work by running inference directly on your device using llama.cpp. This keeps your data private and works without an internet connection."
                } else {
                    "AI: Sorry, I am not capable of giving this answer. Wait for a call or try with a different question."
                }
                
                withContext(Dispatchers.Main) {
                    Log.d(TAG, "Generated answer in $inferenceTime ms: $response")
                    promise.resolve(response)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error generating answer", e)
                withContext(Dispatchers.Main) {
                    promise.reject("INFERENCE_ERROR", "Failed to generate answer: ${e.message}")
                }
            }
        }
    }
    
    @ReactMethod(isBlockingSynchronousMethod = true)
    fun generateAnswerSync(question: String, temperature: Float, maxTokens: Int): String {
        if (!isModelLoaded) {
            return "AI: Sorry, I am not capable of giving this answer. Wait for a call or try with a different question."
        }
        
        try {
            Log.d(TAG, "Generating answer synchronously for: $question")
            
            // In a real implementation, this would call llama.cpp to generate a response
            // Create a simple response based on the question for demonstration
            return if (question.lowercase().contains("hello") 
                || question.lowercase().contains("hi")) {
                "AI: Hello! I'm a local LLM running on your device. How can I help you today?"
            } else if (question.lowercase().contains("who are you") 
                || question.lowercase().contains("what are you")) {
                "AI: I am a local language model running directly on your device for privacy. I can answer questions based on documents you've uploaded."
            } else if (question.lowercase().contains("how") 
                && question.lowercase().contains("work")) {
                "AI: I work by running inference directly on your device using llama.cpp. This keeps your data private and works without an internet connection."
            } else {
                "AI: Sorry, I am not capable of giving this answer. Wait for a call or try with a different question."
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating answer synchronously", e)
            return "AI: Sorry, I am not capable of giving this answer. Wait for a call or try with a different question."
        }
    }
    
    @ReactMethod
    fun uploadDocument(sourceUri: String, fileName: String, promise: Promise) {
        try {
            val documentsDir = File(reactApplicationContext.filesDir, "documents")
            if (!documentsDir.exists()) {
                documentsDir.mkdir()
            }
            
            val inputStream = reactApplicationContext.contentResolver.openInputStream(android.net.Uri.parse(sourceUri))
            val destinationFile = File(documentsDir, fileName)
            
            if (inputStream == null) {
                promise.reject("UPLOAD_ERROR", "Could not open document")
                return
            }
            
            val outputStream = FileOutputStream(destinationFile)
            val buffer = ByteArray(1024)
            var length: Int
            
            while (inputStream.read(buffer).also { length = it } > 0) {
                outputStream.write(buffer, 0, length)
            }
            
            outputStream.flush()
            outputStream.close()
            inputStream.close()
            
            val result = Arguments.createMap().apply {
                putString("path", destinationFile.absolutePath)
                putString("name", fileName)
                putDouble("size", destinationFile.length().toDouble())
            }
            
            promise.resolve(result)
            
        } catch (e: IOException) {
            Log.e(TAG, "Error uploading document", e)
            promise.reject("UPLOAD_ERROR", "Failed to upload document: ${e.message}")
        }
    }
    
    @ReactMethod
    fun listDocuments(promise: Promise) {
        try {
            val documentsDir = File(reactApplicationContext.filesDir, "documents")
            if (!documentsDir.exists()) {
                documentsDir.mkdir()
                promise.resolve(Arguments.createArray())
                return
            }
            
            val documents = Arguments.createArray()
            for (file in documentsDir.listFiles() ?: emptyArray()) {
                val document = Arguments.createMap().apply {
                    putString("name", file.name)
                    putString("path", file.absolutePath)
                    putDouble("size", file.length().toDouble())
                    putDouble("lastModified", file.lastModified().toDouble())
                }
                documents.pushMap(document)
            }
            
            promise.resolve(documents)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error listing documents", e)
            promise.reject("LIST_ERROR", "Failed to list documents: ${e.message}")
        }
    }
    
    @ReactMethod
    fun deleteDocument(fileName: String, promise: Promise) {
        try {
            val documentsDir = File(reactApplicationContext.filesDir, "documents")
            val file = File(documentsDir, fileName)
            
            if (file.exists()) {
                val deleted = file.delete()
                promise.resolve(deleted)
            } else {
                promise.resolve(false)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting document", e)
            promise.reject("DELETE_ERROR", "Failed to delete document: ${e.message}")
        }
    }

    override fun onCatalystInstanceDestroy() {
        currentScope?.cancel()
        modelExecutor.shutdown()
        super.onCatalystInstanceDestroy()
    }
} 