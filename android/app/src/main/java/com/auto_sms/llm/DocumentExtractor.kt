package com.auto_sms.llm

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.itextpdf.text.pdf.PdfReader
import com.itextpdf.text.pdf.parser.PdfTextExtractor
import org.apache.poi.xwpf.extractor.XWPFWordExtractor
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Utility class for extracting text from different document formats
 */
class DocumentExtractor(private val context: Context) {
    private val TAG = "DocumentExtractor"
    
    /**
     * Extract text from a document based on its file extension
     */
    fun extractText(uri: Uri): String {
        try {
            val mimeType = context.contentResolver.getType(uri)
            val fileName = getFileName(context.contentResolver, uri)
            
            Log.d(TAG, "Extracting text from document: $fileName (MIME: $mimeType)")
            
            return when {
                // PDF
                mimeType == "application/pdf" || fileName.endsWith(".pdf", ignoreCase = true) -> 
                    extractPdfText(context.contentResolver, uri)
                
                // DOCX
                mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" || 
                fileName.endsWith(".docx", ignoreCase = true) -> 
                    extractDocxText(context.contentResolver, uri)
                
                // DOC
                mimeType == "application/msword" || fileName.endsWith(".doc", ignoreCase = true) -> 
                    "Microsoft Word DOC format is not directly supported. Please convert to DOCX or PDF."
                
                // Plain text
                mimeType == "text/plain" || fileName.endsWith(".txt", ignoreCase = true) ->
                    extractPlainText(context.contentResolver, uri)
                
                // Unsupported format
                else -> {
                    Log.w(TAG, "Unsupported document format: $fileName ($mimeType)")
                    "Unsupported document format: ${fileName.substringAfterLast('.', "unknown")}"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting document text: ${e.message}", e)
            return "Error extracting document: ${e.message}"
        }
    }
    
    /**
     * Extract text from a PDF file
     */
    private fun extractPdfText(contentResolver: ContentResolver, uri: Uri): String {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val reader = PdfReader(inputStream)
            val pages = reader.numberOfPages
            val stringBuilder = StringBuilder()
            
            for (i in 1..pages) {
                stringBuilder.append(PdfTextExtractor.getTextFromPage(reader, i))
                stringBuilder.append("\n")
            }
            
            reader.close()
            inputStream?.close()
            
            val result = stringBuilder.toString().trim()
            Log.d(TAG, "Successfully extracted ${result.length} chars from PDF")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting text from PDF: ${e.message}", e)
            "Error extracting PDF text: ${e.message}"
        }
    }
    
    /**
     * Extract text from a DOCX file
     */
    private fun extractDocxText(contentResolver: ContentResolver, uri: Uri): String {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val document = XWPFDocument(inputStream)
            val extractor = XWPFWordExtractor(document)
            val text = extractor.text
            
            extractor.close()
            document.close()
            inputStream?.close()
            
            val result = text.trim()
            Log.d(TAG, "Successfully extracted ${result.length} chars from DOCX")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting text from DOCX: ${e.message}", e)
            "Error extracting DOCX text: ${e.message}"
        }
    }
    
    /**
     * Extract text from a plain text file
     */
    private fun extractPlainText(contentResolver: ContentResolver, uri: Uri): String {
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream))
                val stringBuilder = StringBuilder()
                var line: String?
                
                while (reader.readLine().also { line = it } != null) {
                    stringBuilder.append(line).append("\n")
                }
                
                val result = stringBuilder.toString().trim()
                Log.d(TAG, "Successfully extracted ${result.length} chars from text file")
                result
            } ?: run {
                Log.e(TAG, "Error reading text file: InputStream is null")
                "Error reading text file: InputStream is null"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading text file: ${e.message}", e)
            "Error reading text file: ${e.message}"
        }
    }
    
    /**
     * Get the filename from a content URI
     */
    private fun getFileName(contentResolver: ContentResolver, uri: Uri): String {
        var fileName = "unknown_file"
        
        // Try to get the display name from the ContentResolver
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                fileName = cursor.getString(nameIndex)
            }
        }
        
        return fileName
    }
} 