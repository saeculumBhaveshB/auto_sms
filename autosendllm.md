# Implementation Document: React Native + Native Android Integration with MLC LLM

## Objective

Enable dynamic, on-device Q\&A based on user-uploaded DOCX/PDF files using MLC LLM. The system will extract relevant text, perform retrieval-augmented generation, and produce contextually accurate answers prefixed with "AI:"—with no static responses.

---

## Architecture Overview

1. **React Native Layer**

   - **UI Components**: File uploader, text input for questions, response display area.
   - **Bridge Module**: JS-to-native interface to invoke document management, retrieval, LLM inference, and SMS services.

2. **Native Android Layer**

   - **Document Manager**: Stores and provides access to uploaded DOCX/PDF files.
   - **Text Extraction Module**: Parses documents on demand to extract raw text segments.
   - **Retrieval Controller**: Scores and selects the most relevant passages per query.
   - **MLC LLM Engine**: Loads quantized model, runs inference on provided context and user query.
   - **SMS Handler** (optional): Listens for SMS queries and sends back LLM-generated replies.

---

## Component Responsibilities

### 1. Document Manager

- **Receive** multiple DOCX/PDF uploads from React Native.
- **Securely store** files in app-specific storage.
- **Expose** file URIs to downstream modules via the native bridge.

### 2. Text Extraction Module

- **Lazily parse** files using a native library (e.g., PdfBox-Android for PDFs, Apache POI for DOCX).
- **Extract and cache** text segments (e.g., by paragraph or page) only when needed.

### 3. Retrieval Controller

- **Accept** user query and list of extracted text segments.
- **Score** each segment by relevance (keyword frequency or lightweight embedding similarity).
- **Select** top-N segments to form the inference context.

### 4. MLC LLM Engine

- **Include** MLC LLM native libraries and quantized model files in your Gradle configuration.
- **Initialize** the model when auto-reply or Q\&A is enabled.
- **Expose** a method `generateAnswer(question, context)` returning generated text.
- **Run inference** on a background thread to avoid UI blocking.

### 5. Integration Workflow

1. **User uploads** DOCX/PDF files → Document Manager stores them.
2. **User enters** a question → React Native calls native Q\&A API.
3. **Native Q\&A API** invokes:
   a. Text Extraction → Retrieval → LLM inference.
   b. Receives dynamic answer from MLC LLM.
4. **Answer returned** to React Native and displayed, prefixed with "AI:".

---

## Implementation Steps

1. **Set Up File Upload UI**

   - Implement multi-file selector in React Native.
   - Pass selected file URIs to native Document Manager bridge.

2. **Develop Document Manager**

   - Store files in internal storage.
   - Return list of stored file URIs on request.

3. **Implement Text Extraction**

   - Integrate PDF and DOCX parsing libraries.
   - Provide an API to extract text segments given a file URI.

4. **Build Retrieval Controller**

   - Define a simple scoring function.
   - Return top relevant segments for a given question.

5. **Configure MLC LLM Runtime**

   - Add MLC LLM AAR to Gradle.
   - Include quantized model weights in assets or download.

6. **Implement Inference Pipeline**

   - Create `generateAnswer` method: accept question + context, call MLC LLM, return result.
   - Ensure inference runs asynchronously.

7. **Connect React Native Bridge**

   - Expose `askQuestion(question, fileUris)` to JS.
   - Under the hood: extract, retrieve, infer, and callback with answer.

8. **Ensure Dynamic Responses Only**

   - Validate that no static strings exist.
   - All replies must originate from the LLM's output.

9. **Testing & Validation**

   - Use sample DOCX/PDF with known content.
   - Ask diverse questions and ensure dynamic, context-based answers.
   - Verify fallback for out-of-scope queries:

     > "AI: I'm sorry, I couldn't find an answer in your documents."

---

## Non-Functional Considerations

- **Performance**: Quantize to 4-bit/8-bit models; run on background threads.
- **Memory**: Unload the model when idle.
- **Privacy & Security**: All processing stays on-device; no external calls.
- **Extensibility**: Retrieval logic can plug into future vector DBs or advanced search.

## Additional Implementation

### Rank document passages by relevance to the query

```kotlin
/**
 * Rank document passages by relevance to the query
 */
private fun rankPassagesByRelevance(passages: List<String>, query: String): List<String> {
    // Simple implementation - count term frequency
    return passages.sortedByDescending { passage ->
        query.toLowerCase().split(" ").filter { it.length > 3 }.sumOf { term ->
            passage.toLowerCase().split(" ").count { it == term }
        }
    }
}
```
