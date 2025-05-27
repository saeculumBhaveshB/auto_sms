package com.auto_sms.docparser

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import com.facebook.react.bridge.*
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.extractor.WordExtractor
import org.apache.poi.xwpf.extractor.XWPFWordExtractor
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class DocParserModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {
    
    private val TAG = "DocParserModule"

    override fun getName(): String {
        return "DocParserModule"
    }

    @ReactMethod
    fun parseDocument(uri: String, promise: Promise) {
        try {
            Log.d(TAG, "Parsing document from URI: $uri")
            
            val contentResolver = reactApplicationContext.contentResolver
            val documentUri = Uri.parse(uri)
            
            // Check if content provider exists
            if (documentUri.scheme == "content") {
                val mimeType = contentResolver.getType(documentUri)
                Log.d(TAG, "Document MIME type: $mimeType")
                
                if (mimeType == null) {
                    promise.reject("MIME_TYPE_ERROR", "Cannot determine document MIME type")
                    return
                }
            }
            
            val text = extractTextFromDocument(contentResolver, documentUri)
            
            if (text.isEmpty()) {
                promise.reject("PARSE_ERROR", "Could not extract text from document")
                return
            }
            
            Log.d(TAG, "Successfully parsed document text: ${text.substring(0, Math.min(100, text.length))}...")
            promise.resolve(text)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing document: ${e.message}", e)
            e.printStackTrace()
            promise.reject("PARSE_ERROR", "Failed to parse document: ${e.message}", e)
        }
    }
    
    /**
     * Extract text from document file (.doc or .docx)
     */
    private fun extractTextFromDocument(contentResolver: ContentResolver, uri: Uri): String {
        var inputStream: InputStream? = null
        try {
            inputStream = contentResolver.openInputStream(uri)
            
            if (inputStream == null) {
                Log.e(TAG, "Could not open input stream for URI: $uri")
                return ""
            }
            
            // Wrap in BufferedInputStream to support mark/reset
            val bufferedInput = BufferedInputStream(inputStream)
            bufferedInput.mark(1024 * 1024) // Mark with large readlimit
            
            val mimeType = contentResolver.getType(uri)
            Log.d(TAG, "Document mime type: $mimeType")
            
            // Check if it's a DOC, DOCX, or PDF file
            return when {
                mimeType?.contains("application/msword") == true -> {
                    // DOC file
                    val doc = HWPFDocument(bufferedInput)
                    val extractor = WordExtractor(doc)
                    val text = extractor.text
                    extractor.close()
                    text
                }
                mimeType?.contains("application/vnd.openxmlformats-officedocument.wordprocessingml.document") == true -> {
                    // DOCX file
                    val docx = XWPFDocument(bufferedInput)
                    val extractor = XWPFWordExtractor(docx)
                    val text = extractor.text
                    extractor.close()
                    text
                }
                mimeType?.contains("application/pdf") == true -> {
                    // PDF file - use a direct approach without PDFBox to avoid AWT dependencies
                    try {
                        Log.d(TAG, "Processing PDF document using direct byte extraction")
                        
                        // Reset to beginning of stream
                        bufferedInput.reset()
                        
                        // Read PDF bytes
                        val bytes = bufferedInput.readBytes()
                        val pdfString = String(bytes)
                        
                        // Use a simple text extraction approach that doesn't rely on AWT
                        val extractedText = extractTextFromPdfBytes(pdfString)
                        
                        if (extractedText.isNotEmpty()) {
                            return extractedText
                        } else {
                            Log.w(TAG, "Could not extract text from PDF using direct method")
                            return "Could not extract text from this PDF document. The file may be encrypted, scanned, or in an unsupported format."
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing PDF: ${e.message}", e)
                        e.printStackTrace()
                        throw e
                    }
                }
                else -> {
                    Log.e(TAG, "Unsupported document type: $mimeType")
                    ""
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting text from document: ${e.message}")
            return ""
        } finally {
            try {
                inputStream?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing input stream: ${e.message}")
            }
        }
    }

    /**
     * Extract text from PDF bytes without using PDFBox to avoid AWT dependencies
     */
    private fun extractTextFromPdfBytes(pdfString: String): String {
        val textBuilder = StringBuilder()
        
        try {
            // Pattern 1: Look for text objects with parentheses (most common pattern)
            val textObjectPattern = "\\((.*?)\\)\\s*Tj".toRegex()
            val textMatches = textObjectPattern.findAll(pdfString)
            
            for (match in textMatches) {
                val text = match.groupValues[1]
                    .replace("\\)", ")")
                    .replace("\\(", "(")
                textBuilder.append(text)
                
                // Check if we need to add space or newline
                if (!text.endsWith(" ") && !text.endsWith("-")) {
                    textBuilder.append(" ")
                }
                
                // Add newline after certain common period patterns that likely end sentences
                if (text.matches(".*\\.[\\s]*$".toRegex())) {
                    textBuilder.append("\n")
                }
            }
            
            // Pattern 2: Look for text inside BT...ET blocks
            val btEtPattern = "BT\\s*(.*?)\\s*ET".toRegex(RegexOption.DOT_MATCHES_ALL)
            val btEtMatches = btEtPattern.findAll(pdfString)
            
            for (match in btEtMatches) {
                val content = match.groupValues[1]
                val textFragments = "\\[(.*?)\\]".toRegex().findAll(content)
                
                for (fragment in textFragments) {
                    val text = fragment.groupValues[1]
                        .replace("\\(", "(")
                        .replace("\\)", ")")
                    
                    // Skip if it looks like coordinates or formatting
                    if (!text.matches("^[\\d\\s.]+$".toRegex())) {
                        textBuilder.append(text).append(" ")
                    }
                }
            }
            
            // Pattern 3: Look for text in content streams
            val contentStreamPattern = "/T[\\w\\d]+\\s+([^\\s]+)".toRegex()
            val contentMatches = contentStreamPattern.findAll(pdfString)
            
            for (match in contentMatches) {
                val encodedText = match.groupValues[1]
                if (encodedText.startsWith("(") && encodedText.endsWith(")")) {
                    val text = encodedText.substring(1, encodedText.length - 1)
                        .replace("\\(", "(")
                        .replace("\\)", ")")
                    textBuilder.append(text).append(" ")
                }
            }
            
            // Clean up and format the text
            var result = textBuilder.toString().trim()
                // Remove non-printable characters
                .replace("[^\\x20-\\x7E\\p{L}\\n]".toRegex(), " ")
                // Remove excessive spaces
                .replace("\\s+".toRegex(), " ")
                // Fix common formatting issues
                .replace(" \\. ", ". ")
                .replace(" , ", ", ")
                .replace(" : ", ": ")
                
            // Break text into paragraphs for better readability
            result = result.replace("\\. ".toRegex(), ".\n\n")
                .replace("\\n{3,}".toRegex(), "\n\n")
            
            return result
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting text from PDF bytes: ${e.message}", e)
            return ""
        }
    }
} 