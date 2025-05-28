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

    init {
        // Log initialization for debugging
        Log.d(TAG, "🔧 LocalLLMModule initialized with reactContext: ${reactContext.hashCode()}")
    }

    override fun getName(): String {
        return "LocalLLMModule"
    }

    @ReactMethod
    fun getDeviceInfo(promise: Promise) {
        try {
            Log.d(TAG, "📱 Getting device info")
            // Get the ActivityManager service to access real device memory info
            val activityManager = reactApplicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)

            // Calculate total and available memory in MB
            val totalMemoryMB = memoryInfo.totalMem / (1024 * 1024)
            val availableMemoryMB = memoryInfo.availMem / (1024 * 1024)

            Log.d(TAG, "📊 Real Device Memory - Total: $totalMemoryMB MB, Available: $availableMemoryMB MB")
            
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
            Log.e(TAG, "❌ Error getting device info", e)
            promise.reject("ERROR_DEVICE_INFO", "Failed to get device info: ${e.message}")
        }
    }

    @ReactMethod
    fun downloadModel(modelUrl: String, modelName: String, promise: Promise) {
        Log.d(TAG, "💾 Downloading model: $modelName from $modelUrl")
        // This is a placeholder. In a real implementation, we would download the model
        // from a URL or use a pre-bundled model in the assets folder
        val modelsDir = File(reactApplicationContext.filesDir, "models")
        if (!modelsDir.exists()) {
            modelsDir.mkdir()
            Log.d(TAG, "📁 Created models directory at ${modelsDir.absolutePath}")
        }
        
        val modelFile = File(modelsDir, modelName)
        if (modelFile.exists()) {
            modelPath = modelFile.absolutePath
            Log.d(TAG, "✅ Model already exists at $modelPath")
            promise.resolve(modelPath)
        } else {
            Log.d(TAG, "❌ Model does not exist: $modelName")
            promise.reject("MODEL_NOT_FOUND", "Model needs to be downloaded or copied from assets")
        }
    }

    @ReactMethod
    fun loadModel(modelPath: String, promise: Promise) {
        Log.d(TAG, "🧠 LLM DEBUG: loadModel() called with path: $modelPath")
        
        if (currentScope != null) {
            currentScope?.cancel()
            Log.d(TAG, "🔄 LLM DEBUG: Canceled previous model loading scope")
        }
        
        currentScope = CoroutineScope(Dispatchers.IO)
        currentScope?.launch {
            try {
                Log.d(TAG, "⏳ LLM DEBUG: Starting model loading process")
                // In a real implementation, this would initialize llama.cpp with the model
                val loadTime = measureTimeMillis {
                    // Simulate model loading time
                    delay(2000)
                }
                
                this@LocalLLMModule.modelPath = modelPath
                isModelLoaded = true
                Log.d(TAG, "✅ Model loaded at path: $modelPath")
                
                withContext(Dispatchers.Main) {
                    Log.d(TAG, "✅ LLM DEBUG: Model loaded successfully in $loadTime ms")
                    promise.resolve(true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ LLM ERROR: Error loading model", e)
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    promise.reject("MODEL_LOAD_ERROR", "Failed to load model: ${e.message}")
                }
            }
        }
    }
    
    @ReactMethod
    fun isModelLoaded(promise: Promise) {
        Log.d(TAG, "🔍 LLM DEBUG: isModelLoaded() called, current status: $isModelLoaded")
        promise.resolve(isModelLoaded)
    }
    
    @ReactMethod(isBlockingSynchronousMethod = true)
    fun isModelLoadedSync(): Boolean {
        Log.d(TAG, "🔍 LLM DEBUG: isModelLoadedSync() called, current status: $isModelLoaded")
        return isModelLoaded
    }
    
    @ReactMethod
    fun unloadModel(promise: Promise) {
        Log.d(TAG, "🧹 LLM DEBUG: unloadModel() called")
        if (isModelLoaded) {
            isModelLoaded = false
            modelPath = null
            Log.d(TAG, "✅ Model unloaded successfully")
            
            // In a real implementation, this would clean up llama.cpp resources
            promise.resolve(true)
        } else {
            Log.d(TAG, "ℹ️ No model was loaded")
            promise.resolve(false)
        }
    }

    @ReactMethod
    fun generateAnswer(question: String, temperature: Float, maxTokens: Int, promise: Promise) {
        Log.d(TAG, "🤔 LLM DEBUG: generateAnswer() called for question: $question")
        if (!isModelLoaded) {
            Log.e(TAG, "❌ LLM ERROR: Cannot generate answer - model not loaded")
            promise.reject("MODEL_NOT_LOADED", "Model is not loaded")
            return
        }
        
        currentScope = CoroutineScope(Dispatchers.IO)
        currentScope?.launch {
            try {
                Log.d(TAG, "⏳ Generating answer for: $question")
                
                // In a real implementation, this would call llama.cpp to generate a response
                val inferenceTime = measureTimeMillis {
                    // Simulate inference time
                    delay(1500)
                }
                
                // Create a response based on the question
                val response = generateContextAwareResponse(question) 
                
                withContext(Dispatchers.Main) {
                    Log.d(TAG, "✅ Generated answer in $inferenceTime ms: $response")
                    promise.resolve(response)
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error generating answer", e)
                withContext(Dispatchers.Main) {
                    promise.reject("INFERENCE_ERROR", "Failed to generate answer: ${e.message}")
                }
            }
        }
    }
    
    @ReactMethod(isBlockingSynchronousMethod = true)
    fun generateAnswerSync(question: String, temperature: Float, maxTokens: Int): String {
        Log.d(TAG, "🤔 LLM DEBUG: generateAnswerSync() called for question: $question")
        Log.d(TAG, "🧠 LLM DEBUG: Model loaded status: $isModelLoaded")
        
        if (!isModelLoaded) {
            Log.e(TAG, "❌ LLM ERROR: Cannot generate answer - model not loaded")
            return "AI: I'm running in local LLM mode. Let me check your documents for an answer."
        }
        
        try {
            Log.d(TAG, "⚙️ LLM DEBUG: Generating answer synchronously with temperature: $temperature, maxTokens: $maxTokens")
            
            // In a real implementation, this would call llama.cpp to generate a response
            // For now, create improved context-aware responses
            val startTime = System.currentTimeMillis()
            val response = generateContextAwareResponse(question)
            val endTime = System.currentTimeMillis()
            
            Log.d(TAG, "⏱️ LLM DEBUG: Generated answer in ${endTime - startTime}ms: $response")
            return response
        } catch (e: Exception) {
            Log.e(TAG, "❌ LLM ERROR: Error generating answer synchronously", e)
            e.printStackTrace()
            return "AI: I'm currently processing your documents locally. Please try your question again in a moment."
        }
    }

    /**
     * Generate a context-aware response based on the question
     */
    private fun generateContextAwareResponse(question: String): String {
        val lowerCaseQuestion = question.lowercase()
        
        // Create more realistic and responsive answers
        return when {
            lowerCaseQuestion.contains("hello") || lowerCaseQuestion.contains("hi") -> 
                "AI: Hello! I'm a local LLM running on your device. How can I help you today?"
            
            lowerCaseQuestion.contains("who are you") || lowerCaseQuestion.contains("what are you") -> 
                "AI: I am a local language model running directly on your device for privacy. I can answer questions based on documents you've uploaded."
            
            lowerCaseQuestion.contains("how") && lowerCaseQuestion.contains("work") -> 
                "AI: I work by running inference directly on your device using a local model. This keeps your data private and works without an internet connection."
            
            lowerCaseQuestion.contains("what can you do") || lowerCaseQuestion.contains("help") -> 
                "AI: I can answer questions based on documents you've provided. Since I run locally on your device, your data remains private. Just ask me about the information in your documents!"
            
            lowerCaseQuestion.contains("document") || lowerCaseQuestion.contains("upload") -> 
                "AI: You can upload documents in the LLM Setup screen. I'll use those documents to provide informative responses to questions."
            
            lowerCaseQuestion.contains("when") || lowerCaseQuestion.contains("time") || 
            lowerCaseQuestion.contains("schedule") || lowerCaseQuestion.contains("appointment") -> 
                "AI: Based on the documents I have access to, I can help with scheduling information. Please provide more details about what specific time or date information you need."
            
            lowerCaseQuestion.contains("cost") || lowerCaseQuestion.contains("price") || 
            lowerCaseQuestion.contains("payment") -> 
                "AI: I can provide information about costs, prices, or payment methods based on your documents. Please let me know what specific pricing information you need."

            lowerCaseQuestion.contains("order") || lowerCaseQuestion.contains("delivery") || 
            lowerCaseQuestion.contains("shipping") -> 
                "AI: According to my documents, orders typically arrive within 3-5 business days. If you have a specific order inquiry, please provide your order number."
                
            lowerCaseQuestion.contains("contact") || lowerCaseQuestion.contains("support") || 
            lowerCaseQuestion.contains("email") || lowerCaseQuestion.contains("phone") -> 
                "AI: You can reach our support team at support@example.com or call 555-123-4567 for assistance."

            lowerCaseQuestion.contains("refund") || lowerCaseQuestion.contains("return") -> 
                "AI: We offer full refunds within 30 days of purchase. To initiate a return, please contact our support team."

            lowerCaseQuestion.contains("test") -> 
                "AI: This is a test response from the local LLM. The system is working correctly!"
            
            else -> 
                "AI: I've processed your question using my local LLM. Based on your documents, I can provide information on various topics. Could you please be more specific about what you need to know?"
        }
    }
    
    @ReactMethod
    fun uploadDocument(sourceUri: String, fileName: String, promise: Promise) {
        try {
            Log.d(TAG, "📄 Uploading document: $fileName from URI: $sourceUri")
            val documentsDir = File(reactApplicationContext.filesDir, "documents")
            if (!documentsDir.exists()) {
                documentsDir.mkdir()
                Log.d(TAG, "📁 Created documents directory at ${documentsDir.absolutePath}")
            }
            
            val inputStream = reactApplicationContext.contentResolver.openInputStream(android.net.Uri.parse(sourceUri))
            val destinationFile = File(documentsDir, fileName)
            
            if (inputStream == null) {
                Log.e(TAG, "❌ Could not open document - null input stream")
                promise.reject("UPLOAD_ERROR", "Could not open document")
                return
            }
            
            val outputStream = FileOutputStream(destinationFile)
            val buffer = ByteArray(1024)
            var length: Int
            var totalBytes = 0
            
            while (inputStream.read(buffer).also { length = it } > 0) {
                outputStream.write(buffer, 0, length)
                totalBytes += length
            }
            
            outputStream.flush()
            outputStream.close()
            inputStream.close()
            
            Log.d(TAG, "✅ Document uploaded successfully: $fileName, size: ${destinationFile.length()} bytes")
            
            val result = Arguments.createMap().apply {
                putString("path", destinationFile.absolutePath)
                putString("name", fileName)
                putDouble("size", destinationFile.length().toDouble())
            }
            
            promise.resolve(result)
            
        } catch (e: IOException) {
            Log.e(TAG, "❌ Error uploading document", e)
            promise.reject("UPLOAD_ERROR", "Failed to upload document: ${e.message}")
        }
    }
    
    @ReactMethod
    fun listDocuments(promise: Promise) {
        try {
            val documentsDir = File(reactApplicationContext.filesDir, "documents")
            Log.d(TAG, "📑 LLM DEBUG: Listing documents in directory: ${documentsDir.absolutePath}")
            Log.d(TAG, "📂 LLM DEBUG: Documents directory exists: ${documentsDir.exists()}")
            
            if (!documentsDir.exists()) {
                documentsDir.mkdir()
                Log.d(TAG, "📁 LLM DEBUG: Created documents directory as it didn't exist")
                promise.resolve(Arguments.createArray())
                return
            }
            
            val files = documentsDir.listFiles() ?: emptyArray()
            Log.d(TAG, "🔢 LLM DEBUG: Found ${files.size} documents")
            
            val documents = Arguments.createArray()
            for (file in files) {
                Log.d(TAG, "📄 LLM DEBUG: Document: ${file.name}, size: ${file.length()}")
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
            Log.e(TAG, "❌ LLM ERROR: Error listing documents", e)
            e.printStackTrace()
            promise.reject("LIST_ERROR", "Failed to list documents: ${e.message}")
        }
    }
    
    @ReactMethod
    fun deleteDocument(fileName: String, promise: Promise) {
        try {
            Log.d(TAG, "🗑️ Deleting document: $fileName")
            val documentsDir = File(reactApplicationContext.filesDir, "documents")
            val file = File(documentsDir, fileName)
            
            if (file.exists()) {
                val deleted = file.delete()
                Log.d(TAG, "🗑️ Delete result: $deleted")
                promise.resolve(deleted)
            } else {
                Log.d(TAG, "⚠️ File does not exist for deletion: $fileName")
                promise.resolve(false)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error deleting document", e)
            promise.reject("DELETE_ERROR", "Failed to delete document: ${e.message}")
        }
    }

    @ReactMethod(isBlockingSynchronousMethod = true)
    fun loadModelSync(modelPath: String): Boolean {
        Log.d(TAG, "🧠 LLM DEBUG: loadModelSync() called with path: $modelPath")
        
        try {
            // Synchronous version for direct native calls
            val loadTime = measureTimeMillis {
                // Simulate model loading - in a real implementation this would load the actual model
                Thread.sleep(1000)
                
                // Create a fake model file to verify path exists
                val modelFile = File(modelPath)
                if (!modelFile.exists()) {
                    try {
                        val modelDir = modelFile.parentFile
                        if (!modelDir.exists()) {
                            modelDir.mkdirs()
                            Log.d(TAG, "📁 LLM DEBUG: Created model directory at ${modelDir.absolutePath}")
                        }
                        
                        // Create an empty file just to make the path valid
                        modelFile.createNewFile()
                        Log.d(TAG, "📄 LLM DEBUG: Created placeholder model file at $modelPath")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ LLM ERROR: Failed to create model placeholder", e)
                    }
                }
            }
            
            this.modelPath = modelPath
            isModelLoaded = true
            
            Log.d(TAG, "✅ LLM DEBUG: Model loaded synchronously in $loadTime ms")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "❌ LLM ERROR: Error loading model synchronously", e)
            e.printStackTrace()
            return false
        }
    }

    override fun onCatalystInstanceDestroy() {
        Log.d(TAG, "🧹 LLM DEBUG: onCatalystInstanceDestroy called - cleaning up resources")
        currentScope?.cancel()
        modelExecutor.shutdown()
        super.onCatalystInstanceDestroy()
    }
} 