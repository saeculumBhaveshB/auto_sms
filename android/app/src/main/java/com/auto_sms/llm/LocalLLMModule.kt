package com.auto_sms.llm

import android.app.ActivityManager
import android.content.Context
import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import com.facebook.react.bridge.*
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.system.measureTimeMillis
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.FileReader
import java.io.InputStreamReader

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
    private val llamaCpp = LlamaCppWrapper()
    private val documentExtractor by lazy { DocumentExtractor(reactApplicationContext) }
    
    // Store document content for LLM context
    private val documentsContent = mutableMapOf<String, String>()

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
        // This is a placeholder. In a real implementation, you might download a model dynamically
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
                // Load model using llama.cpp wrapper
                val loaded = llamaCpp.loadModel(reactApplicationContext, modelPath)
                
                if (loaded) {
                    this@LocalLLMModule.modelPath = modelPath
                    isModelLoaded = true
                    
                    // Let's preload documents too
                    loadDocuments()
                    
                    withContext(Dispatchers.Main) {
                        Log.d(TAG, "Model loaded successfully")
                        promise.resolve(true)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Log.e(TAG, "Failed to load model")
                        promise.reject("MODEL_LOAD_ERROR", "Failed to load model")
                    }
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
            llamaCpp.unloadModel()
            isModelLoaded = false
            modelPath = null
            
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
                
                // Check if we have documents to provide context
                if (documentsContent.isEmpty()) {
                    loadDocuments()
                }
                
                // Find relevant context from documents
                val relevantContext = findRelevantContext(question)
                
                // Build prompt with context
                val prompt = buildPromptWithContext(question, relevantContext)
                
                // Run inference with the model
                val response = llamaCpp.generate(prompt, temperature, maxTokens)
                
                // Format response with AI prefix if needed
                val formattedResponse = if (response.startsWith("AI:")) {
                    response
                } else {
                    "AI: $response"
                }
                
                withContext(Dispatchers.Main) {
                    Log.d(TAG, "Generated answer: $formattedResponse")
                    promise.resolve(formattedResponse)
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
            
            // Check if we have documents to provide context
            if (documentsContent.isEmpty()) {
                loadDocuments()
            }
            
            // Find relevant context from documents
            val relevantContext = findRelevantContext(question)
            
            // Build prompt with context
            val prompt = buildPromptWithContext(question, relevantContext)
            
            // Run inference with the model
            val response = llamaCpp.generateSync(prompt, temperature, maxTokens)
            
            // Format response with AI prefix if needed
            val formattedResponse = if (response.startsWith("AI:")) {
                response
            } else {
                "AI: $response"
            }
            
            Log.d(TAG, "Generated answer synchronously: $formattedResponse")
            return formattedResponse
            
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
            
            val inputStream = reactApplicationContext.contentResolver.openInputStream(Uri.parse(sourceUri))
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
            
            // Extract document text and store for LLM
            val uri = Uri.parse(sourceUri)
            val content = documentExtractor.extractText(uri)
            documentsContent[fileName] = content
            
            Log.d(TAG, "Document uploaded and extracted: $fileName (${content.length} chars)")
            
            // Save URI to shared preferences for later loading
            saveDocumentUri(sourceUri, fileName)
            
            val result = Arguments.createMap().apply {
                putString("path", destinationFile.absolutePath)
                putString("name", fileName)
                putDouble("size", destinationFile.length().toDouble())
                putInt("contentLength", content.length)
            }
            
            promise.resolve(result)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading document", e)
            promise.reject("UPLOAD_ERROR", "Failed to upload document: ${e.message}")
        }
    }
    
    /**
     * Save document URI to SharedPreferences for persistence
     */
    private fun saveDocumentUri(uriString: String, fileName: String) {
        try {
            val sharedPrefs = reactApplicationContext.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
            val documentUrisJson = sharedPrefs.getString("@AutoSMS:DocumentUris", "{}") ?: "{}"
            
            // Parse existing JSON
            val jsonObject = JSONObject(documentUrisJson)
            
            // Add or update this URI with filename as key
            jsonObject.put(fileName, uriString)
            
            // Save back to SharedPreferences
            sharedPrefs.edit().putString("@AutoSMS:DocumentUris", jsonObject.toString()).apply()
            Log.d(TAG, "Saved document URI: $fileName -> $uriString")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving document URI: ${e.message}", e)
        }
    }
    
    @ReactMethod
    fun deleteDocument(fileName: String, promise: Promise) {
        try {
            // Delete the file
            val documentsDir = File(reactApplicationContext.filesDir, "documents")
            val file = File(documentsDir, fileName)
            
            var deleted = false
            if (file.exists()) {
                deleted = file.delete()
            }
            
            // Remove from in-memory content
            documentsContent.remove(fileName)
            
            // Remove from SharedPreferences
            val sharedPrefs = reactApplicationContext.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
            val documentUrisJson = sharedPrefs.getString("@AutoSMS:DocumentUris", "{}") ?: "{}"
            val jsonObject = JSONObject(documentUrisJson)
            
            if (jsonObject.has(fileName)) {
                jsonObject.remove(fileName)
                sharedPrefs.edit().putString("@AutoSMS:DocumentUris", jsonObject.toString()).apply()
            }
            
            Log.d(TAG, "Document deleted: $fileName, success: $deleted")
            promise.resolve(deleted)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting document", e)
            promise.reject("DELETE_ERROR", "Failed to delete document: ${e.message}")
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
                val inMemory = documentsContent.containsKey(file.name)
                val contentSize = documentsContent[file.name]?.length ?: 0
                
                val document = Arguments.createMap().apply {
                    putString("name", file.name)
                    putString("path", file.absolutePath)
                    putDouble("size", file.length().toDouble())
                    putDouble("lastModified", file.lastModified().toDouble())
                    putBoolean("inMemory", inMemory)
                    putInt("contentSize", contentSize)
                }
                documents.pushMap(document)
            }
            
            promise.resolve(documents)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error listing documents", e)
            promise.reject("LIST_ERROR", "Failed to list documents: ${e.message}")
        }
    }
    
    /**
     * Load documents from storage into memory for LLM processing
     */
    private fun loadDocuments() {
        try {
            Log.d(TAG, "Loading documents for LLM processing")
            
            // Get document URIs from SharedPreferences
            val sharedPrefs = reactApplicationContext.getSharedPreferences("AutoSmsPrefs", Context.MODE_PRIVATE)
            val documentUrisJson = sharedPrefs.getString("@AutoSMS:DocumentUris", "{}") ?: "{}"
            
            // Parse JSON Object of document URIs (filename -> uri)
            val jsonObject = JSONObject(documentUrisJson)
            documentsContent.clear()
            
            // Process each document
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val fileName = keys.next()
                try {
                    val uriString = jsonObject.getString(fileName)
                    val uri = Uri.parse(uriString)
                    
                    // Extract text content from document
                    val content = documentExtractor.extractText(uri)
                    
                    // Store content for LLM processing
                    if (content.isNotEmpty()) {
                        documentsContent[fileName] = content
                        Log.d(TAG, "Loaded document: $fileName (${content.length} chars)")
                    } else {
                        Log.w(TAG, "Empty content for document: $fileName")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing document $fileName: ${e.message}")
                }
            }
            
            Log.d(TAG, "Loaded ${documentsContent.size} documents for LLM processing")
            
            // Also try to load from the documents directory
            val documentsDir = File(reactApplicationContext.filesDir, "documents")
            if (documentsDir.exists() && documentsContent.isEmpty()) {
                for (file in documentsDir.listFiles() ?: emptyArray()) {
                    if (!documentsContent.containsKey(file.name)) {
                        try {
                            val fileUri = Uri.fromFile(file)
                            val content = documentExtractor.extractText(fileUri)
                            if (content.isNotEmpty()) {
                                documentsContent[file.name] = content
                                // Also save the URI for future use
                                saveDocumentUri(fileUri.toString(), file.name)
                                Log.d(TAG, "Loaded document from file: ${file.name} (${content.length} chars)")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error loading document from file ${file.name}: ${e.message}")
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading documents: ${e.message}", e)
        }
    }
    
    /**
     * Find relevant context from loaded documents based on the question
     */
    private fun findRelevantContext(question: String): String {
        try {
            Log.d(TAG, "Finding relevant context for question: $question")
            
            val contextBuilder = StringBuilder()
            
            // Simple keyword matching approach
            val keywords = question.lowercase().split(" ")
                .filter { it.length > 3 } // Filter out short words
                .toSet()
            
            // Check each document for relevant content
            documentsContent.forEach { (fileName, content) ->
                // Check for keyword matches
                val matchCount = keywords.count { keyword ->
                    content.lowercase().contains(keyword)
                }
                
                // If document contains enough keywords, include its content
                if (matchCount >= 1) { // At least 1 keyword match
                    contextBuilder.append("From document $fileName:\n")
                    contextBuilder.append(content)
                    contextBuilder.append("\n\n")
                    
                    Log.d(TAG, "Found relevant content in document: $fileName ($matchCount keyword matches)")
                }
            }
            
            // If no relevant context found, include a small sample from each document
            if (contextBuilder.isEmpty() && documentsContent.isNotEmpty()) {
                Log.d(TAG, "No specific relevant context found, including samples from all documents")
                
                documentsContent.forEach { (fileName, content) ->
                    val sample = content.take(200) + "..." // First 200 chars
                    contextBuilder.append("From document $fileName:\n")
                    contextBuilder.append(sample)
                    contextBuilder.append("\n\n")
                }
            }
            
            return contextBuilder.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error finding relevant context: ${e.message}", e)
            return ""
        }
    }
    
    /**
     * Build prompt with relevant context for the LLM
     */
    private fun buildPromptWithContext(question: String, context: String): String {
        return """
            You are an AI assistant helping answer questions based on the provided documents.
            
            Document content:
            $context
            
            User question: $question
            
            Provide a helpful answer based on the document content. If the answer cannot be found in the documents,
            respond with "Sorry, I am not capable of giving this answer. Wait for a call or try with a different question."
            
            Your response:
        """.trimIndent()
    }

    override fun onCatalystInstanceDestroy() {
        currentScope?.cancel()
        modelExecutor.shutdown()
        llamaCpp.close()
        super.onCatalystInstanceDestroy()
    }
    
    /**
     * Test method to validate LLM functionality with documents
     */
    @ReactMethod
    fun testLlmWithDocuments(question: String, promise: Promise) {
        try {
            // Check if the model is loaded
            if (!isModelLoaded) {
                Log.w(TAG, "Model not loaded when testing LLM with documents")
                promise.reject("MODEL_NOT_LOADED", "Model is not loaded")
                return
            }
            
            // Check if documents are loaded
            if (documentsContent.isEmpty()) {
                Log.w(TAG, "No documents loaded for LLM inference")
                // Try to load documents in case they weren't loaded
                loadDocuments()
            }
            
            // Log document info
            Log.d(TAG, "Testing LLM with ${documentsContent.size} documents loaded")
            documentsContent.keys.forEachIndexed { index, key ->
                Log.d(TAG, "Document $index: $key (${documentsContent[key]?.length ?: 0} chars)")
            }
            
            // Generate answer using the local LLM
            val response = generateAnswerSync(question, 0.7f, 200)
            
            // Create response data
            val resultData = Arguments.createMap().apply {
                putString("question", question)
                putString("response", response)
                putInt("documentCount", documentsContent.size)
                putBoolean("modelLoaded", isModelLoaded)
            }
            
            // Return the test results
            promise.resolve(resultData)
        } catch (e: Exception) {
            Log.e(TAG, "Error testing LLM with documents: ${e.message}", e)
            promise.reject("TEST_ERROR", "Failed to test LLM: ${e.message}")
        }
    }
} 