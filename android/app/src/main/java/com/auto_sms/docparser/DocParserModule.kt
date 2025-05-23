package com.auto_sms.docparser

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import com.facebook.react.bridge.*
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.extractor.WordExtractor
import org.apache.poi.xwpf.extractor.XWPFWordExtractor
import org.apache.poi.xwpf.usermodel.XWPFDocument
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
            val text = extractTextFromDocument(contentResolver, documentUri)
            
            if (text.isEmpty()) {
                promise.reject("PARSE_ERROR", "Could not extract text from document")
                return
            }
            
            Log.d(TAG, "Successfully parsed document text: ${text.substring(0, Math.min(100, text.length))}...")
            promise.resolve(text)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing document: ${e.message}")
            promise.reject("PARSE_ERROR", "Failed to parse document: ${e.message}")
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
            
            val mimeType = contentResolver.getType(uri)
            Log.d(TAG, "Document mime type: $mimeType")
            
            // Check if it's a DOC or DOCX file
            return when {
                mimeType?.contains("application/msword") == true -> {
                    // DOC file
                    val doc = HWPFDocument(inputStream)
                    val extractor = WordExtractor(doc)
                    val text = extractor.text
                    extractor.close()
                    text
                }
                mimeType?.contains("application/vnd.openxmlformats-officedocument.wordprocessingml.document") == true -> {
                    // DOCX file
                    val docx = XWPFDocument(inputStream)
                    val extractor = XWPFWordExtractor(docx)
                    val text = extractor.text
                    extractor.close()
                    text
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
} 