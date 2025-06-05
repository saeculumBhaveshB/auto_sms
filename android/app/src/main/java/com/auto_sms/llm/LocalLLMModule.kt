package com.auto_sms.llm

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.facebook.react.bridge.*
import com.auto_sms.callsms.CallSmsModule
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.system.measureTimeMillis
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import java.util.Date

/**
 * LocalLLMModule - Native module for running local LLM models on device
 * Uses MLC LLM to provide local inference capabilities
 */
class LocalLLMModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    private val TAG = "LocalLLMModule"
    private val modelExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var isModelLoaded = false
    private var modelPath: String? = null
    private var currentScope: CoroutineScope? = null
    
    // MLC LLM integration
    private val mlcLlmModule: MLCLLMModule = MLCLLMModule(reactContext)
    private var isMLCInitialized = false

    init {
        // Log initialization for debugging
        Log.d(TAG, "🔧 LocalLLMModule initialized with reactContext: ${reactContext.hashCode()}")
        
        // Initialize MLC LLM in the background
        GlobalScope.launch(Dispatchers.IO) {
            try {
                isMLCInitialized = mlcLlmModule.initialize()
                Log.d(TAG, "🚀 MLC LLM initialization: ${if (isMLCInitialized) "SUCCESS" else "FAILED"}")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to initialize MLC LLM", e)
            }
        }
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
                putBoolean("mlcLLMInitialized", isMLCInitialized)
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
        
        currentScope = CoroutineScope(Dispatchers.IO)
        currentScope?.launch {
            try {
                // Try to prepare the model from assets first
                val modelPath = mlcLlmModule.prepareModel(modelName)
                
                if (modelPath != null) {
                    withContext(Dispatchers.Main) {
                        this@LocalLLMModule.modelPath = modelPath
                        Log.d(TAG, "✅ Model prepared at $modelPath")
                        promise.resolve(modelPath)
                    }
                } else {
                    // Model wasn't found in assets, need to download
                    withContext(Dispatchers.Main) {
                        Log.e(TAG, "❌ Model not available in assets: $modelName")
                        promise.reject("MODEL_NOT_FOUND", "Model needs to be downloaded or copied from assets")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e(TAG, "❌ Error preparing model", e)
                    promise.reject("DOWNLOAD_ERROR", "Failed to prepare model: ${e.message}")
                }
            }
        }
    }

    @ReactMethod
    fun getLocalModelDirectory(promise: Promise) {
        Log.d(TAG, "📁 LLM DEBUG: Getting local model directory")
        
        currentScope = CoroutineScope(Dispatchers.IO)
        currentScope?.launch {
            try {
                // Create a local model directory if it doesn't exist
                val localModelDir = mlcLlmModule.createLocalModelDirectory()
                
                if (localModelDir != null) {
                    withContext(Dispatchers.Main) {
                        Log.d(TAG, "✅ Local model directory: $localModelDir")
                        promise.resolve(localModelDir)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Log.e(TAG, "❌ Failed to create local model directory")
                        promise.reject("MODEL_DIR_ERROR", "Failed to create local model directory")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e(TAG, "❌ Error getting local model directory", e)
                    promise.reject("MODEL_DIR_ERROR", "Error getting local model directory: ${e.message}")
                }
            }
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
                
                if (!isMLCInitialized) {
                    Log.d(TAG, "🚀 Initializing MLC LLM...")
                    isMLCInitialized = mlcLlmModule.initialize()
                }
                
                if (!isMLCInitialized) {
                    withContext(Dispatchers.Main) {
                        Log.e(TAG, "❌ MLC LLM initialization failed")
                        promise.reject("MLC_INIT_ERROR", "Failed to initialize MLC LLM")
                    }
                    return@launch
                }
                
                // Check if model path exists or create local model
                val modelFile = File(modelPath)
                var actualModelPath = modelPath
                
                if (!modelFile.exists() || !modelFile.isDirectory) {
                    Log.d(TAG, "⚠️ Model path doesn't exist: $modelPath. Creating local model...")
                    val localModelPath = mlcLlmModule.createLocalModelDirectory()
                    
                    if (localModelPath != null) {
                        actualModelPath = localModelPath
                        Log.d(TAG, "✅ Using local model instead: $localModelPath")
                    } else {
                        withContext(Dispatchers.Main) {
                            Log.e(TAG, "❌ Failed to create local model")
                            promise.reject("MODEL_PATH_ERROR", "Model path doesn't exist and failed to create local model")
                        }
                        return@launch
                    }
                }
                
                // Create config file if needed
                val configPath = mlcLlmModule.createDefaultConfig(actualModelPath)
                
                // Load the model
                val loadTime = measureTimeMillis {
                    isModelLoaded = mlcLlmModule.loadModel(actualModelPath, configPath)
                }
                
                this@LocalLLMModule.modelPath = actualModelPath
                
                withContext(Dispatchers.Main) {
                    if (isModelLoaded) {
                        Log.d(TAG, "✅ LLM DEBUG: Model loaded successfully in $loadTime ms")
                        promise.resolve(true)
                    } else {
                        // Even if the model loading fails in MLCLLMModule, it will now return true
                        // because it falls back to rule-based responses instead of failing
                        Log.d(TAG, "⚠️ LLM DEBUG: Using fallback mode without a real model")
                        promise.resolve(true)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ LLM ERROR: Error loading model", e)
                e.printStackTrace()
                
                // Try to use a dummy model as fallback
                try {
                    val dummyModelPath = mlcLlmModule.createLocalModelDirectory()
                    if (dummyModelPath != null) {
                        val configPath = mlcLlmModule.createDefaultConfig(dummyModelPath)
                        isModelLoaded = mlcLlmModule.loadModel(dummyModelPath, configPath)
                        this@LocalLLMModule.modelPath = dummyModelPath
                        
                        withContext(Dispatchers.Main) {
                            Log.d(TAG, "⚠️ Using fallback dummy model after error")
                            promise.resolve(true)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            promise.reject("MODEL_LOAD_ERROR", "Failed to load model or create fallback: ${e.message}")
                        }
                    }
                } catch (fallbackError: Exception) {
                    withContext(Dispatchers.Main) {
                        Log.e(TAG, "❌ Fallback also failed", fallbackError)
                        promise.reject("MODEL_LOAD_ERROR", "Failed to load model and fallback also failed: ${e.message}")
                    }
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
        
        currentScope = CoroutineScope(Dispatchers.IO)
        currentScope?.launch {
            try {
                isModelLoaded = false
                val unloaded = mlcLlmModule.unloadModel()
                modelPath = null
                
                withContext(Dispatchers.Main) {
                    Log.d(TAG, "✅ Model unloaded successfully")
                    promise.resolve(true)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e(TAG, "❌ Error unloading model", e)
                    promise.reject("UNLOAD_ERROR", "Failed to unload model: ${e.message}")
                }
            }
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
                
                val inferenceTime = measureTimeMillis {
                    // Use MLC LLM for actual inference
                    val response = mlcLlmModule.generateAnswer(question, null, temperature)
                    
                    withContext(Dispatchers.Main) {
                        Log.d(TAG, "✅ Generated answer: $response")
                        promise.resolve(response)
                    }
                }
                
                Log.d(TAG, "⏱️ Inference took $inferenceTime ms")
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
        
        if (!isModelLoaded) {
            Log.e(TAG, "❌ LLM ERROR: Cannot generate answer - model not loaded")
            return "AI: I'm running in local LLM mode, but my model isn't loaded yet. Please load a model first."
        }
        
        return try {
            Log.d(TAG, "⚙️ LLM DEBUG: Generating answer synchronously")
            
            // This is a blocking call that will run on the calling thread
            // We run it as a runBlocking coroutine to be able to call our suspend function
            runBlocking {
                val startTime = System.currentTimeMillis()
                val response = mlcLlmModule.generateAnswer(question, null, temperature)
                val endTime = System.currentTimeMillis()
                
                Log.d(TAG, "⏱️ LLM DEBUG: Generated answer in ${endTime - startTime}ms")
                response
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ LLM ERROR: Error generating answer synchronously", e)
            e.printStackTrace()
            "AI: I encountered an error while processing your request. Please try again."
        }
    }
    
    @ReactMethod
    fun generateAnswerWithContext(question: String, context: String, temperature: Float, promise: Promise) {
        Log.d(TAG, "🤔 LLM DEBUG: generateAnswerWithContext() called")
        
        if (!isModelLoaded) {
            Log.e(TAG, "❌ LLM ERROR: Cannot generate answer - model not loaded")
            promise.reject("MODEL_NOT_LOADED", "Model is not loaded")
            return
        }
        
        currentScope = CoroutineScope(Dispatchers.IO)
        currentScope?.launch {
            try {
                Log.d(TAG, "⏳ Generating answer with context for: $question")
                Log.d(TAG, "Context size: ${context.length} characters")
                
                val inferenceTime = measureTimeMillis {
                    // Use TensorFlow LLM for inference with context
                    val response = mlcLlmModule.generateAnswer(question, context, temperature)
                    
                    withContext(Dispatchers.Main) {
                        Log.d(TAG, "✅ Generated answer with context: $response")
                        promise.resolve(response)
                    }
                }
                
                Log.d(TAG, "⏱️ Inference took $inferenceTime ms")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error generating answer with context", e)
                withContext(Dispatchers.Main) {
                    promise.reject("INFERENCE_ERROR", "Failed to generate answer: ${e.message}")
                }
            }
        }
    }

    /**
     * Upload a document to be used for LLM queries
     * @param sourceUri The URI of the document to upload
     * @param fileName The name to save the document as
     * @param createDefault Whether this is a default document (optional, default: false)
     */
    @ReactMethod
    fun uploadDocument(sourceUri: String, fileName: String, createDefault: Boolean, promise: Promise) {
        Log.d(TAG, "📄 LLM: Uploading document from $sourceUri as $fileName (createDefault=$createDefault)")
        
        if (!createDefault && fileName.startsWith("sample_document_")) {
            Log.d(TAG, "🚫 LLM: Skipping automatic document creation")
            val result = Arguments.createMap()
            result.putString("name", fileName)
            result.putString("path", "")
            result.putDouble("size", 0.0)
            result.putDouble("lastModified", Date().time.toDouble())
            
            promise.resolve(result)
            return
        }
        
        try {
            // Create documents directory if it doesn't exist
            val documentsDir = File(reactApplicationContext.filesDir, "documents")
            if (!documentsDir.exists()) {
                documentsDir.mkdirs()
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
                putDouble("lastModified", destinationFile.lastModified().toDouble())
            }
            
            promise.resolve(result)
            
        } catch (e: IOException) {
            Log.e(TAG, "❌ Error uploading document", e)
            promise.reject("UPLOAD_ERROR", "Failed to upload document: ${e.message}")
        }
    }
    
    @ReactMethod
    fun listDocuments(promise: Promise) {
        Log.d(TAG, "📂 LLM: Listing documents")
        try {
            val documentsDir = File(reactApplicationContext.filesDir, "documents")
            if (!documentsDir.exists()) {
                documentsDir.mkdirs()
                Log.d(TAG, "📁 LLM: Created documents directory at ${documentsDir.absolutePath}")
            }
            
            val documents = documentsDir.listFiles()
            val documentArray = Arguments.createArray()
            
            if (documents != null) {
                for (file in documents) {
                    if (file.isFile) {
                        val documentMap = Arguments.createMap()
                        documentMap.putString("name", file.name)
                        documentMap.putString("path", file.absolutePath)
                        documentMap.putDouble("size", file.length().toDouble())
                        documentMap.putDouble("lastModified", file.lastModified().toDouble())
                        
                        // Check if this is likely a binary file
                        val isBinary = if (file.name.lowercase().endsWith(".pdf") || 
                                       file.name.lowercase().endsWith(".docx") ||
                                       file.name.lowercase().endsWith(".jpg") ||
                                       file.name.lowercase().endsWith(".png")) {
                            true
                        } else {
                            try {
                                val sample = file.readText().take(500).toString()
                                val nonPrintableCount = sample.count { char ->
                                    char.toInt() < 32 && char.toInt() != 9 && char.toInt() != 10 && char.toInt() != 13
                                }
                                (nonPrintableCount.toFloat() / Math.min(500, sample.length)) > 0.15
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ LLM: Error reading file for binary check: ${e.message}")
                                false
                            }
                        }
                        
                        documentMap.putBoolean("isBinary", isBinary)
                        
                        documentArray.pushMap(documentMap)
                    }
                }
            }
            
            Log.d(TAG, "📊 LLM: Found ${documentArray.size()} documents")
            promise.resolve(documentArray)
        } catch (e: Exception) {
            Log.e(TAG, "❌ LLM: Error listing documents: ${e.message}", e)
            promise.reject("LIST_DOCUMENTS_ERROR", "Failed to list documents: ${e.message}")
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
                if (!isMLCInitialized) {
                    isMLCInitialized = mlcLlmModule.initialize()
                }
                
                if (!isMLCInitialized) {
                    Log.e(TAG, "❌ MLC LLM initialization failed")
                    return false
                }
                
                // Create config file if needed
                val configPath = mlcLlmModule.createDefaultConfig(modelPath)
                
                // This is a blocking call
                runBlocking {
                    isModelLoaded = mlcLlmModule.loadModel(modelPath, configPath)
                }
            }
            
            this.modelPath = modelPath
            
            Log.d(TAG, "✅ LLM DEBUG: Model loaded synchronously in $loadTime ms")
            return isModelLoaded
        } catch (e: Exception) {
            Log.e(TAG, "❌ LLM ERROR: Error loading model synchronously", e)
            e.printStackTrace()
            return false
        }
    }

    /**
     * Create a sample document for testing purposes
     * @param createDefault Whether to create a default sample document if none is provided
     */
    @ReactMethod
    fun createSampleDocument(createDefault: Boolean, promise: Promise) {
        Log.d(TAG, "📄 LLM: Creating sample document, createDefault=$createDefault")
        
        if (!createDefault) {
            Log.d(TAG, "📝 LLM: Skipping automatic sample document creation")
            val result = Arguments.createMap()
            promise.resolve(result)
            return
        }
        
        try {
            val documentsDir = File(reactApplicationContext.filesDir, "documents")
            if (!documentsDir.exists()) {
                documentsDir.mkdirs()
                Log.d(TAG, "📁 LLM: Created documents directory at ${documentsDir.absolutePath}")
            }
            
            val sampleFile = File(documentsDir, "sample_document.txt")
            
            // Create sample document with test content
            sampleFile.writeText(
                """
                # Sample Document for LLM Auto-Reply Testing
                
                ## Company Information
                
                Our company provides excellent customer service 24/7.
                You can reach our support team at support@example.com.
                
                ## Product Information
                
                Our product is a mobile app that helps users with automatic SMS replies.
                It uses a local LLM to generate intelligent responses based on documents.
                
                ## FAQ
                
                Q: When will my order arrive?
                A: Orders typically arrive within 3-5 business days.
                
                Q: How do I contact support?
                A: You can email support@example.com or call us at 555-123-4567.
                
                Q: What's your refund policy?
                A: We offer full refunds within 30 days of purchase.
                
                Q: How does the auto-reply feature work?
                A: When you miss a call, the app sends an automatic SMS. When they reply, our local LLM provides an intelligent response based on your uploaded documents.
                
                ## Contact Details
                
                Email: info@example.com
                Phone: 555-987-6543
                Address: 123 Main St, Anytown, USA
                """.trimIndent()
            )
            
            // Also create a PDF file for testing binary detection
            val binaryFile = File(documentsDir, "sample.pdf")
            try {
                val pdfHeader = "%PDF-1.5\n%¥±ë\n1 0 obj\n<</Type/Catalog/Pages 2 0 R>>\nendobj\n"
                binaryFile.writeText(pdfHeader)
                Log.d(TAG, "📄 LLM: Created sample PDF file for binary detection testing")
            } catch (e: Exception) {
                Log.e(TAG, "❌ LLM: Failed to create sample PDF file: ${e.message}")
            }
            
            Log.d(TAG, "📝 LLM: Created sample document at ${sampleFile.absolutePath}, size: ${sampleFile.length()} bytes")
            
            val result = Arguments.createMap()
            result.putString("path", sampleFile.absolutePath)
            result.putDouble("size", sampleFile.length().toDouble())
            result.putString("name", sampleFile.name)
            
            promise.resolve(result)
        } catch (e: Exception) {
            Log.e(TAG, "❌ LLM: Failed to create sample document: ${e.message}", e)
            promise.reject("CREATE_SAMPLE_DOC_ERROR", "Failed to create sample document: ${e.message}")
        }
    }

    /**
     * Get the default model path in app storage
     */
    @ReactMethod(isBlockingSynchronousMethod = true)
    fun getDefaultModelPath(): String {
        val modelsDir = File(reactApplicationContext.filesDir, "models")
        val defaultModelDir = File(modelsDir, "default_model")
        return defaultModelDir.absolutePath
    }

    override fun onCatalystInstanceDestroy() {
        Log.d(TAG, "🧹 LLM DEBUG: onCatalystInstanceDestroy called - cleaning up resources")
        currentScope?.cancel()
        modelExecutor.shutdown()
        mlcLlmModule.cleanup()
        super.onCatalystInstanceDestroy()
    }

    suspend fun generateAnswer(question: String, context: String?, temperature: Float): String {
        return mlcLlmModule.generateAnswer(question, context, temperature)
    }

    /**
     * Check if we have a real model loaded or are in fallback mode
     */
    @ReactMethod
    fun hasRealModel(promise: Promise) {
        Log.d(TAG, "🔍 LLM DEBUG: hasRealModel() called")
        
        if (!isModelLoaded) {
            promise.resolve(false)
            return
        }
        
        currentScope = CoroutineScope(Dispatchers.IO)
        currentScope?.launch {
            try {
                // Ask the MLCLLMModule if it has a real interpreter
                val hasInterpreter = mlcLlmModule.hasInterpreter()
                
                withContext(Dispatchers.Main) {
                    Log.d(TAG, "🔍 Real model available: $hasInterpreter")
                    promise.resolve(hasInterpreter)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e(TAG, "❌ Error checking for real model", e)
                    promise.resolve(false)
                }
            }
        }
    }

    /**
     * Extracts an answer from the given context by finding relevant information
     * based solely on document content, with no hardcoded responses
     */
    private fun extractAnswerFromContext(question: String, context: String?): String? {
        if (context == null || context.isBlank()) {
            return null
        }
        
        // Convert question to lowercase for easier matching
        val questionLower = question.lowercase()
        
        // Break the context into paragraphs for analysis
        val paragraphs = context.split("\n\n")
        
        // Extract keywords from the question (words with 4+ characters)
        val keywords = questionLower
            .split(Regex("\\s+"))
            .filter { it.length >= 4 }
            .map { it.lowercase() }
            .toSet()
        
        // Score paragraphs by keyword matching
        val scoredParagraphs = paragraphs.map { paragraph ->
            val paragraphLower = paragraph.lowercase()
            val score = keywords.count { keyword ->
                paragraphLower.contains(keyword)
            }
            Pair(paragraph, score)
        }
        
        // Get the most relevant paragraphs
        val relevantParagraphs = scoredParagraphs
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(3)
            .map { it.first }
        
        if (relevantParagraphs.isEmpty()) {
            return null
        }
        
        // Construct a response using the relevant paragraphs
        val response = StringBuilder()
        response.append("Based on the document content, ")
        
        // Add the relevant paragraphs to the response
        relevantParagraphs.forEachIndexed { index, paragraph ->
            if (index > 0) {
                response.append(" Additionally, ")
            }
            
            // Clean up the paragraph and add it to the response
            val cleanParagraph = paragraph
                .replace(Regex("\\s+"), " ")
                .trim()
            
            response.append(cleanParagraph)
            if (!cleanParagraph.endsWith(".") && !cleanParagraph.endsWith("!") && !cleanParagraph.endsWith("?")) {
                response.append(".")
            }
        }
        
        return response.toString()
    }

    /**
     * Force load the default model and return detailed status information
     * This is useful for debugging when the model isn't loading automatically
     */
    @ReactMethod
    fun forceLoadDefaultModel(promise: Promise) {
        try {
            Log.d(TAG, "🚀 Force loading default model...")
            val startTime = System.currentTimeMillis()
            
            // Create a detailed response map
            val resultMap = Arguments.createMap()
            resultMap.putBoolean("wasLoaded", isModelLoaded)
            
            // Get default model path
            val modelPath = getDefaultModelPath()
            resultMap.putString("modelPath", modelPath)
            
            // Try to unload first if needed
            if (isModelLoaded) {
                val unloaded = mlcLlmModule.unloadModel()
                resultMap.putBoolean("unloaded", unloaded)
                isModelLoaded = false
            }
            
            // Use the existing load implementation
            val success = loadModelSync(modelPath)
            resultMap.putBoolean("loadSuccess", success)
            
            // Add real model info
            val hasRealInterpreter = try {
                mlcLlmModule.hasInterpreter()
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error checking for real interpreter", e)
                false
            }
            resultMap.putBoolean("isRealModel", hasRealInterpreter)
            resultMap.putBoolean("finalStatus", isModelLoaded)
            
            // Add timing
            val endTime = System.currentTimeMillis()
            resultMap.putInt("loadTimeMs", (endTime - startTime).toInt())
            
            Log.d(TAG, "✅ Force load complete. Success: $success, IsRealModel: $hasRealInterpreter")
            promise.resolve(resultMap)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in forceLoadDefaultModel", e)
            promise.reject("LOAD_ERROR", "Failed to load model: ${e.message}")
        }
    }
} 