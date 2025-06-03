package com.auto_sms.llm

import android.content.Context
import android.net.Uri
import android.util.Log
import com.facebook.react.bridge.*
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.apache.poi.xwpf.extractor.XWPFWordExtractor
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.Executors
import kotlinx.coroutines.*
import java.util.regex.Pattern
import kotlin.math.min
import org.json.JSONArray
import org.json.JSONObject

class Phi3MiniModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
    private val TAG = "Phi3MiniModule"
    private val modelExecutor = Executors.newSingleThreadExecutor()
    private var modelPath: String? = null
    private val modelDownloader = ModelDownloader(reactContext)
    private var modelLoaded = false
    private val documentManager = DocumentManager(reactContext)
    
    // Native methods - connected to llama_wrapper.cpp
    private external fun initModel(modelPath: String): Boolean
    private external fun generateText(prompt: String, maxTokens: Int, temperature: Float, topP: Float): String
    private external fun freeModel()
    private external fun isModelLoaded(): Boolean
    
    init {
        Log.d(TAG, "🔧 Phi3MiniModule initialized")
        
        // Load the native library - but don't crash if it's not available yet
        // We'll check again when specific methods are called
        try {
            System.loadLibrary("llama")
            Log.d(TAG, "✅ Loaded llama library successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "❌ Failed to load llama library: ${e.message}")
            Log.w(TAG, "⚠️ The app will continue but model loading will fail until the native library is available")
            // Not setting modelLoaded to true, and we'll check for the library again when needed
        }
    }
    
    override fun getName(): String {
        return "Phi3MiniModule"
    }
    
    @ReactMethod
    fun isAvailable(promise: Promise) {
        try {
            val isLoaded = isModelLoaded()
            promise.resolve(isLoaded)
            Log.d(TAG, "🔍 Model availability check: $isLoaded")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking model availability: ${e.message}")
            promise.reject("ERROR_CHECK_AVAILABILITY", e.message)
        }
    }
    
    @ReactMethod
    fun loadModel(modelPathParam: String, promise: Promise) {
        Log.d(TAG, "🧠 Loading Phi-3-mini model from: $modelPathParam")
        
        // Check if the native library is available
        try {
            System.loadLibrary("llama")
            Log.d(TAG, "✅ Native library 'llama' is available")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "❌ Native library 'llama' is not available: ${e.message}")
            reactApplicationContext.runOnUiQueueThread {
                promise.reject("NATIVE_LIBRARY_MISSING", "Native library 'llama' is not available. The app needs to be rebuilt with native code support enabled.")
            }
            return
        }
        
        modelExecutor.execute {
            try {
                // Check if model file exists
                val modelFile = File(modelPathParam)
                if (!modelFile.exists()) {
                    Log.e(TAG, "❌ Model file not found at $modelPathParam")
                    reactApplicationContext.runOnUiQueueThread {
                        promise.reject("MODEL_NOT_FOUND", "Model file not found at $modelPathParam")
                    }
                    return@execute
                }
                
                Log.d(TAG, "📊 Model file size: ${modelFile.length() / (1024 * 1024)} MB")
                
                try {
                    val success = initModel(modelPathParam)
                    if (success) {
                        modelPath = modelPathParam
                        modelLoaded = true
                        Log.d(TAG, "✅ Model loaded successfully")
                        reactApplicationContext.runOnUiQueueThread {
                            promise.resolve(true)
                        }
                    } else {
                        Log.e(TAG, "❌ Failed to load model")
                        reactApplicationContext.runOnUiQueueThread {
                            promise.reject("LOAD_FAILED", "Failed to load model")
                        }
                    }
                } catch (e: UnsatisfiedLinkError) {
                    Log.e(TAG, "❌ Native method call failed: ${e.message}")
                    reactApplicationContext.runOnUiQueueThread {
                        promise.reject("NATIVE_METHOD_ERROR", "Native method call failed: ${e.message}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error calling native method: ${e.message}")
                    reactApplicationContext.runOnUiQueueThread {
                        promise.reject("NATIVE_CALL_ERROR", "Error calling native method: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error loading model: ${e.message}")
                reactApplicationContext.runOnUiQueueThread {
                    promise.reject("LOAD_ERROR", "Error loading model: ${e.message}")
                }
            }
        }
    }
    
    @ReactMethod
    fun generate(prompt: String, maxTokens: Int, temperature: Float, topP: Float, promise: Promise) {
        if (!isModelLoaded()) {
            Log.e(TAG, "❌ Model not loaded, cannot generate text")
            promise.reject("MODEL_NOT_LOADED", "Model not loaded")
            return
        }
        
        modelExecutor.execute {
            try {
                Log.d(TAG, "🤔 Generating text for prompt: $prompt")
                
                // For demonstration until native code is properly integrated:
                // Extract the user question from the prompt
                val userQuestion = extractUserQuestion(prompt)
                Log.d(TAG, "📝 Extracted user question: $userQuestion")
                
                // Extract any context from the prompt
                val contextInfo = extractContext(prompt)
                Log.d(TAG, "📝 Context information available: ${contextInfo.isNotEmpty()}")
                
                // Generate a response that references the context if present
                val simulatedResponse = if (contextInfo.isNotEmpty()) {
                    generateContextAwareResponse(userQuestion, contextInfo)
                } else {
                    "AI: I don't have enough context to answer your question about \"$userQuestion\". Please upload some documents first."
                }
                
                Log.d(TAG, "✅ Generated simulation response")
                reactApplicationContext.runOnUiQueueThread {
                    promise.resolve(simulatedResponse)
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error generating text: ${e.message}")
                reactApplicationContext.runOnUiQueueThread {
                    promise.reject("GENERATION_ERROR", "Error generating text: ${e.message}")
                }
            }
        }
    }
    
    @ReactMethod
    fun unloadModel(promise: Promise) {
        modelExecutor.execute {
            try {
                Log.d(TAG, "🧹 Unloading model")
                freeModel()
                modelPath = null
                modelLoaded = false
                Log.d(TAG, "✅ Model unloaded successfully")
                reactApplicationContext.runOnUiQueueThread {
                    promise.resolve(true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error unloading model: ${e.message}")
                reactApplicationContext.runOnUiQueueThread {
                    promise.reject("UNLOAD_ERROR", "Error unloading model: ${e.message}")
                }
            }
        }
    }
    
    @ReactMethod
    fun downloadModelIfNeeded(promise: Promise) {
        // Use our ModelDownloader to download or retrieve the model
        GlobalScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "🔍 Checking for Phi-3-mini model")
                
                // Check if model exists first
                val existingPath = modelDownloader.getPhiModelPath()
                if (existingPath != null) {
                    Log.d(TAG, "✅ Model already exists at $existingPath")
                    withContext(Dispatchers.Main) {
                        promise.resolve(existingPath)
                    }
                    return@launch
                }
                
                // Download the model if it doesn't exist
                try {
                    val downloadedPath = modelDownloader.downloadPhiModelIfNeeded()
                    Log.d(TAG, "✅ Model downloaded to $downloadedPath")
                    withContext(Dispatchers.Main) {
                        promise.resolve(downloadedPath)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error downloading model: ${e.message}")
                    withContext(Dispatchers.Main) {
                        promise.reject("DOWNLOAD_ERROR", "Failed to download model: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error checking or downloading model: ${e.message}")
                withContext(Dispatchers.Main) {
                    promise.reject("MODEL_DOWNLOAD_ERROR", e.message)
                }
            }
        }
    }
    
    @ReactMethod
    fun formatPromptForPhi(userPrompt: String, context: String?, promise: Promise) {
        try {
            val formattedPrompt = if (context.isNullOrBlank()) {
                "<|user|>\n$userPrompt<|end|>\n<|assistant|>"
            } else {
                "<|system|>\n$context<|end|>\n<|user|>\n$userPrompt<|end|>\n<|assistant|>"
            }
            promise.resolve(formattedPrompt)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error formatting prompt: ${e.message}")
            promise.reject("FORMAT_ERROR", "Error formatting prompt: ${e.message}")
        }
    }
    
    @ReactMethod
    fun retrieveDocumentContextForQuery(query: String, promise: Promise) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "🔍 Retrieving document context for query: $query")
                
                // Get all documents
                val docs = documentManager.listDocuments()
                if (docs.isEmpty()) {
                    Log.d(TAG, "ℹ️ No documents available")
                    withContext(Dispatchers.Main) {
                        promise.resolve(null)
                    }
                    return@launch
                }
                
                Log.d(TAG, "📄 Found ${docs.size} documents")
                
                // Extract text segments from documents
                val allSegments = mutableListOf<TextSegment>()
                for (doc in docs) {
                    try {
                        val segments = extractTextSegments(doc)
                        allSegments.addAll(segments)
                        Log.d(TAG, "📄 Extracted ${segments.size} segments from ${doc.name}")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error extracting text from ${doc.name}: ${e.message}")
                    }
                }
                
                if (allSegments.isEmpty()) {
                    Log.d(TAG, "ℹ️ No text segments extracted from documents")
                    withContext(Dispatchers.Main) {
                        promise.resolve(null)
                    }
                    return@launch
                }
                
                // Get most relevant segments
                val relevantSegments = findRelevantSegments(query, allSegments)
                if (relevantSegments.isEmpty()) {
                    Log.d(TAG, "ℹ️ No relevant segments found for query")
                    withContext(Dispatchers.Main) {
                        promise.resolve(null)
                    }
                    return@launch
                }
                
                // Create context from relevant segments
                val context = buildContextFromSegments(relevantSegments)
                Log.d(TAG, "✅ Built context of ${context.length} characters from ${relevantSegments.size} segments")
                
                withContext(Dispatchers.Main) {
                    promise.resolve(context)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error retrieving document context: ${e.message}")
                withContext(Dispatchers.Main) {
                    promise.reject("RETRIEVAL_ERROR", "Error retrieving document context: ${e.message}")
                }
            }
        }
    }
    
    @ReactMethod
    fun getAvailableDocuments(promise: Promise) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val docs = documentManager.listDocuments()
                val result = WritableNativeArray()
                
                for (doc in docs) {
                    val docObj = WritableNativeMap()
                    docObj.putString("name", doc.name)
                    docObj.putString("path", doc.path)
                    docObj.putDouble("size", doc.size.toDouble())
                    docObj.putDouble("lastModified", doc.lastModified.toDouble())
                    result.pushMap(docObj)
                }
                
                withContext(Dispatchers.Main) {
                    promise.resolve(result)
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error getting available documents: ${e.message}")
                withContext(Dispatchers.Main) {
                    promise.reject("DOCUMENT_LIST_ERROR", "Error getting available documents: ${e.message}")
                }
            }
        }
    }
    
    // Helper class to represent a document text segment
    data class TextSegment(
        val text: String,
        val docName: String,
        val position: Int
    )
    
    // Extract text segments from a document
    private fun extractTextSegments(doc: DocumentManager.Document): List<TextSegment> {
        val segments = mutableListOf<TextSegment>()
        val file = File(doc.path)
        
        if (!file.exists()) {
            Log.e(TAG, "❌ Document file does not exist: ${doc.path}")
            return segments
        }
        
        when {
            doc.path.endsWith(".pdf", ignoreCase = true) -> {
                try {
                    PDDocument.load(file).use { document ->
                        val stripper = PDFTextStripper()
                        val allText = stripper.getText(document)
                        
                        // Split by paragraphs or newlines
                        val paragraphs = allText.split(Pattern.compile("\\n\\s*\\n"))
                        
                        paragraphs.forEachIndexed { index, paragraph ->
                            if (paragraph.trim().isNotEmpty()) {
                                segments.add(TextSegment(paragraph.trim(), doc.name, index))
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error extracting text from PDF ${doc.name}: ${e.message}")
                }
            }
            doc.path.endsWith(".docx", ignoreCase = true) -> {
                try {
                    FileInputStream(file).use { fis ->
                        XWPFDocument(fis).use { document ->
                            XWPFWordExtractor(document).use { extractor ->
                                val allText = extractor.text
                                
                                // Split by paragraphs or newlines
                                val paragraphs = allText.split(Pattern.compile("\\n\\s*\\n"))
                                
                                paragraphs.forEachIndexed { index, paragraph ->
                                    if (paragraph.trim().isNotEmpty()) {
                                        segments.add(TextSegment(paragraph.trim(), doc.name, index))
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error extracting text from DOCX ${doc.name}: ${e.message}")
                }
            }
            else -> {
                Log.w(TAG, "⚠️ Unsupported document type: ${doc.path}")
            }
        }
        
        return segments
    }
    
    // Find segments relevant to the query
    private fun findRelevantSegments(query: String, segments: List<TextSegment>): List<TextSegment> {
        if (segments.isEmpty()) return emptyList()
        
        val queryWords = query.lowercase().split(Pattern.compile("\\s+"))
            .filter { it.length > 2 } // Filter out short words
        
        if (queryWords.isEmpty()) return segments.take(3) // Return first 3 if no significant query words
        
        // Score each segment based on word overlap
        val scoredSegments = segments.map { segment ->
            val segmentWords = segment.text.lowercase().split(Pattern.compile("\\s+|\\p{Punct}"))
            val matchCount = queryWords.count { queryWord ->
                segmentWords.any { segmentWord -> segmentWord.contains(queryWord) }
            }
            Pair(segment, matchCount.toDouble() / queryWords.size) // Normalize score
        }
        
        // Sort by score and take top 5
        return scoredSegments
            .sortedByDescending { it.second }
            .take(5)
            .map { it.first }
    }
    
    // Build context from selected segments
    private fun buildContextFromSegments(segments: List<TextSegment>): String {
        val contextBuilder = StringBuilder()
        contextBuilder.append("The following information is extracted from your documents:\n\n")
        
        segments.forEachIndexed { index, segment ->
            contextBuilder.append("Document: ${segment.docName}\n")
            contextBuilder.append("Text: ${segment.text}\n")
            if (index < segments.size - 1) {
                contextBuilder.append("\n---\n\n")
            }
        }
        
        contextBuilder.append("\n\nPlease use this information to answer the question.")
        
        return contextBuilder.toString()
    }
    
    // Helper functions for simulating responses based on context
    private fun extractUserQuestion(prompt: String): String {
        return if (prompt.contains("<|user|>")) {
            prompt.split("<|user|>").getOrElse(1) { "" }
                .split("<|end|>").getOrElse(0) { "" }
                .trim()
        } else {
            prompt
        }
    }
    
    private fun extractContext(prompt: String): String {
        return if (prompt.contains("<|system|>")) {
            prompt.split("<|system|>").getOrElse(1) { "" }
                .split("<|end|>").getOrElse(0) { "" }
                .trim()
        } else {
            ""
        }
    }
    
    private fun generateContextAwareResponse(question: String, context: String): String {
        // Extract document names if available
        val docMatcher = Regex("Document: (.*?)\\n").findAll(context)
        val docNames = docMatcher.map { it.groupValues[1] }.toList().distinct()
        
        // Extract a few snippets from the context to use in the response
        val segments = Regex("Text: (.*?)(?=\\n---|$)").findAll(context)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() }
            .toList()
        
        val questionLower = question.lowercase()
        val responseBuilder = StringBuilder("AI: ")
        
        // Pick text to reference based on keywords in the question
        val keywords = questionLower.split(Regex("\\s+"))
            .filter { it.length > 3 }
            .toSet()
        
        // Find segments that match keywords
        val relevantSegments = segments.filter { segment ->
            keywords.any { keyword -> segment.lowercase().contains(keyword) }
        }.take(2)
        
        if (relevantSegments.isEmpty() && segments.isNotEmpty()) {
            // If no direct matches, just take the first segment
            val segment = segments.first().take(150) + if (segments.first().length > 150) "..." else ""
            
            responseBuilder.append("Based on your documents")
            if (docNames.isNotEmpty()) {
                responseBuilder.append(" (")
                responseBuilder.append(docNames.take(2).joinToString(", "))
                if (docNames.size > 2) responseBuilder.append(", etc.")
                responseBuilder.append(")")
            }
            responseBuilder.append(", I found the following information: \"")
            responseBuilder.append(segment)
            responseBuilder.append("\" Does this help answer your question about ${question.take(30)}${if (question.length > 30) "..." else ""}?")
        } else if (relevantSegments.isNotEmpty()) {
            // Use the relevant segments to construct an answer
            responseBuilder.append("According to ")
            if (docNames.isNotEmpty()) {
                responseBuilder.append("your document")
                if (docNames.size > 1) responseBuilder.append("s")
                responseBuilder.append(" ")
                responseBuilder.append(docNames.take(2).joinToString(", "))
                if (docNames.size > 2) responseBuilder.append(", etc.")
            } else {
                responseBuilder.append("the information provided")
            }
            responseBuilder.append(", ")
            
            // Create a synthesized answer based on relevant segments
            val firstSegment = relevantSegments.first().take(100)
            responseBuilder.append("I found that \"")
            responseBuilder.append(firstSegment)
            responseBuilder.append("\"")
            
            if (relevantSegments.size > 1) {
                val secondSegment = relevantSegments[1].take(100)
                responseBuilder.append(" and also \"")
                responseBuilder.append(secondSegment)
                responseBuilder.append("\"")
            }
            
            responseBuilder.append(". ${listOf(
                "This appears to address your question about ${question.take(20)}${if (question.length > 20) "..." else ""}.",
                "Does this information help with your query?",
                "Would you like more details on any part of this?"
            ).random()}")
        } else {
            // No context or no relevant segments
            responseBuilder.append("I've reviewed the documents but couldn't find specific information about \"$question\". Would you like to try a different question or upload more relevant documents?")
        }
        
        return responseBuilder.toString()
    }
}

// Class to manage documents
class DocumentManager(private val context: Context) {
    private val TAG = "DocumentManager"
    
    // Document representation
    data class Document(
        val name: String,
        val path: String,
        val size: Long,
        val lastModified: Long
    )
    
    // List all documents in the app's document directory
    fun listDocuments(): List<Document> {
        val documents = mutableListOf<Document>()
        val docsDir = File(context.filesDir, "documents")
        
        if (!docsDir.exists()) {
            docsDir.mkdirs()
            return documents
        }
        
        docsDir.listFiles()?.forEach { file ->
            if (file.isFile && (file.name.endsWith(".pdf", ignoreCase = true) || 
                               file.name.endsWith(".docx", ignoreCase = true))) {
                documents.add(
                    Document(
                        name = file.name,
                        path = file.absolutePath,
                        size = file.length(),
                        lastModified = file.lastModified()
                    )
                )
            }
        }
        
        return documents
    }
} 