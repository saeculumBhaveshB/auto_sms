import React, { useState, useEffect } from "react";
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  ActivityIndicator,
  ScrollView,
  Switch,
} from "react-native";
import { NativeModules } from "react-native";

const { CallSmsModule, LocalLLMModule } = NativeModules;

interface LLMTesterProps {
  // No required props
}

interface Document {
  name: string;
  path: string;
  size: number;
  lastModified: number;
  isBinary?: boolean;
  isPdf?: boolean;
  extractedTextAvailable?: boolean;
}

const LLMTester: React.FC<LLMTesterProps> = () => {
  const [question, setQuestion] = useState<string>(
    "What are the diagnostic criteria for hypertension?"
  );
  const [response, setResponse] = useState<string>("");
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [isModelLoaded, setIsModelLoaded] = useState<boolean>(false);
  const [documents, setDocuments] = useState<Document[]>([]);
  const [showDebugInfo, setShowDebugInfo] = useState<boolean>(false);
  const [debugLog, setDebugLog] = useState<string>("");
  const [isDocQA, setIsDocQA] = useState<boolean>(false);

  // Load model status and documents on component mount
  useEffect(() => {
    const init = async () => {
      try {
        // Check if model is loaded
        const modelLoaded = await LocalLLMModule.isModelLoaded();
        setIsModelLoaded(modelLoaded);

        // Get available documents
        const docs = await LocalLLMModule.listDocuments();

        // Enrich document info with binary/PDF status
        const enrichedDocs = await Promise.all(
          docs.map(async (doc: Document) => {
            try {
              // Check if it's a PDF
              const isPdf = doc.name.toLowerCase().endsWith(".pdf");
              let extractedTextAvailable = false;

              if (isPdf) {
                logDebug(`🔍 Checking PDF: ${doc.name}`);
                try {
                  // Try to check if text can be extracted
                  const testExtraction = await CallSmsModule.testPdfExtraction(
                    doc.path
                  );
                  extractedTextAvailable = testExtraction.success;
                  logDebug(
                    `📄 PDF ${doc.name} text extraction: ${
                      extractedTextAvailable ? "Available" : "Not available"
                    }`
                  );
                } catch (e) {
                  logDebug(`❌ Error checking PDF text: ${e}`);
                  extractedTextAvailable = false;
                }
              }

              return {
                ...doc,
                isPdf,
                extractedTextAvailable,
                isBinary:
                  isPdf ||
                  doc.name.toLowerCase().match(/\.(docx|jpg|jpeg|png|gif)$/) !==
                    null,
              };
            } catch (e) {
              logDebug(`❌ Error processing document ${doc.name}: ${e}`);
              return doc;
            }
          })
        );

        setDocuments(enrichedDocs);

        logDebug(`📄 Found ${enrichedDocs.length} documents`);
        enrichedDocs.forEach((doc: Document) => {
          const fileType = doc.isPdf ? "PDF" : doc.isBinary ? "Binary" : "Text";
          const pdfStatus = doc.isPdf
            ? doc.extractedTextAvailable
              ? " (text extractable)"
              : " (no extractable text)"
            : "";
          logDebug(
            `   - ${doc.name} (${(doc.size / 1024).toFixed(
              2
            )} KB) - ${fileType}${pdfStatus}`
          );
        });

        logDebug(`🧠 Model loaded status: ${modelLoaded}`);

        // Run document verification to debug issues
        await verifyDocuments();
      } catch (err: unknown) {
        console.error("Error initializing LLM Tester:", err);
        const errorMessage = err instanceof Error ? err.message : String(err);
        logDebug(`❌ Error initializing: ${errorMessage}`);
      }
    };

    init();
  }, []);

  const logDebug = (message: string) => {
    setDebugLog(
      (prev) =>
        `${prev}\n${new Date().toISOString().slice(11, 19)} - ${message}`
    );
    console.log(message);
  };

  const verifyDocuments = async () => {
    try {
      logDebug("🔍 Verifying document storage...");
      const result = await CallSmsModule.debugDocumentStorage();

      logDebug(`📁 Documents path: ${result.documentsPath}`);
      logDebug(`📂 Documents directory exists: ${result.documentsExists}`);
      logDebug(`📊 Found ${result.fileCount} files`);

      if (result.files && result.files.length > 0) {
        let binaryCount = 0;
        let textCount = 0;
        let pdfCount = 0;
        let extractableCount = 0;

        result.files.forEach((file: any) => {
          const isBinary = file.isBinary;
          const isPdf = file.name.toLowerCase().endsWith(".pdf");

          if (isPdf) {
            pdfCount++;
            if (file.extractableText === true) extractableCount++;
          } else if (isBinary) binaryCount++;
          else textCount++;

          const fileType = isPdf ? "PDF" : isBinary ? "Binary" : "Text";
          const extractInfo = isPdf
            ? ` (${file.extractableText ? "extractable" : "non-extractable"})`
            : "";

          logDebug(
            `   - ${file.name} (${(file.size / 1024).toFixed(
              2
            )} KB) - ${fileType}${extractInfo}`
          );
        });

        logDebug(
          `📊 Summary: ${textCount} text files, ${binaryCount} binary files, ${pdfCount} PDF files (${extractableCount} extractable)`
        );

        // Refresh document list with enhanced info
        const docs = await LocalLLMModule.listDocuments();
        const enrichedDocs = docs.map((doc: Document) => {
          const isPdf = doc.name.toLowerCase().endsWith(".pdf");
          const matchingFile = result.files.find(
            (f: any) => f.name === doc.name
          );

          return {
            ...doc,
            isPdf,
            extractedTextAvailable:
              isPdf && matchingFile ? matchingFile.extractableText : false,
            isBinary:
              isPdf ||
              doc.name.toLowerCase().match(/\.(docx|jpg|jpeg|png|gif)$/) !==
                null,
          };
        });

        setDocuments(enrichedDocs);
      } else {
        logDebug("⚠️ No files found in documents directory");

        // Create a test document if none exist
        logDebug("📄 Creating a sample text document for testing");
        try {
          await LocalLLMModule.createSampleDocument();
          logDebug("✅ Created sample document");

          // Refresh document list
          const refreshedDocs = await LocalLLMModule.listDocuments();
          setDocuments(refreshedDocs);
          logDebug(`📄 Now have ${refreshedDocs.length} documents`);
        } catch (e) {
          logDebug(`❌ Failed to create sample document: ${e}`);
        }
      }

      return result;
    } catch (err: unknown) {
      const errorMessage = err instanceof Error ? err.message : String(err);
      logDebug(`❌ Error verifying documents: ${errorMessage}`);
      return null;
    }
  };

  const testLLM = async () => {
    if (!question.trim()) {
      setError("Please enter a question to test");
      return;
    }

    try {
      setLoading(true);
      setIsDocQA(false);
      setError(null);

      // Start logging
      logDebug(`🔍 Testing LLM with question: "${question}"`);
      logDebug(`📄 Using ${documents.length} documents for context`);

      // Verify documents first to ensure they're available
      await verifyDocuments();

      const startTime = Date.now();

      // Check if model is loaded first
      if (!isModelLoaded) {
        logDebug("⚠️ Model not loaded, attempting to load default model");
        // Try to get selected model path or use a default
        let modelPath = "";
        try {
          // This would come from your settings or local storage
          // For now, let's use a direct call to simulate
          const result = await CallSmsModule.testLLMModelPath();
          modelPath = result || "";
          logDebug(`📁 Using model path: ${modelPath}`);
        } catch (e: unknown) {
          const errorMessage = e instanceof Error ? e.message : String(e);
          logDebug(`❌ Error getting model path: ${errorMessage}`);
        }

        if (modelPath) {
          try {
            logDebug("🔄 Loading model...");
            const loaded = await LocalLLMModule.loadModel(modelPath);
            setIsModelLoaded(loaded);
            logDebug(`✅ Model loaded: ${loaded}`);
          } catch (e: unknown) {
            const errorMessage = e instanceof Error ? e.message : String(e);
            logDebug(`❌ Error loading model: ${errorMessage}`);
          }
        }
      }

      // Build document reference string for the LLM prompt with better info
      const docContext = documents
        .map((doc) => {
          const fileType = doc.isPdf ? "PDF" : doc.isBinary ? "Binary" : "Text";
          const extractStatus = doc.isPdf
            ? doc.extractedTextAvailable
              ? " (text extractable)"
              : " (no extractable text)"
            : "";
          return `Document: ${doc.name} (${(doc.size / 1024).toFixed(
            2
          )} KB) - ${fileType}${extractStatus}`;
        })
        .join("\n");

      // Add documents to the prompt for better context
      const enhancedQuestion =
        documents.length > 0
          ? `Based on these documents:\n${docContext}\n\nPlease answer the question: ${question}`
          : question;

      logDebug("🧠 Requesting LLM response...");

      // Call the native module's testLLM function with document context
      const result = await CallSmsModule.testLLM(enhancedQuestion);
      const endTime = Date.now();

      setResponse(result);
      logDebug(`✅ Received response in ${endTime - startTime}ms`);
      logDebug(`📝 Response: "${result}"`);
    } catch (err: any) {
      console.error("Error testing LLM:", err);
      logDebug(`❌ Error testing LLM: ${err.message || "Unknown error"}`);
      setError(`Error: ${err.message || "Unknown error occurred"}`);
      setResponse(
        "AI: I'm not able to answer based on your documents. Please refine your query."
      );
    } finally {
      setLoading(false);
    }
  };

  // New function to use the improved document QA capability
  const runDocumentQA = async () => {
    if (!question.trim()) {
      setError("Please enter a question to test");
      return;
    }

    try {
      setLoading(true);
      setIsDocQA(true);
      setError(null);

      // Start logging
      logDebug(`🔍 Starting Document QA for: "${question}"`);
      logDebug(`📄 Available documents: ${documents.length}`);

      // Verify documents first to ensure they're available
      await verifyDocuments();

      if (documents.length === 0) {
        setError("No documents available. Please add documents first.");
        logDebug(`❌ No documents available for QA`);
        setResponse(
          "AI: I don't have any documents to work with. Please upload some documents first."
        );
        setLoading(false);
        return;
      }

      const startTime = Date.now();

      // Call the new document QA function
      logDebug("🧠 Calling document QA with context retrieval...");
      const MAX_PASSAGES = 5; // Retrieve up to 5 most relevant passages

      const result = await CallSmsModule.documentQA(question, MAX_PASSAGES);
      const endTime = Date.now();

      setResponse(result);
      logDebug(`✅ Document QA response received in ${endTime - startTime}ms`);
      logDebug(`📝 Response: "${result}"`);
    } catch (err: any) {
      console.error("Error in Document QA:", err);
      logDebug(`❌ Error in Document QA: ${err.message || "Unknown error"}`);
      setError(`Error: ${err.message || "Unknown error occurred"}`);
      setResponse(
        "AI: I'm sorry, I couldn't find an answer in your documents. Please try another question."
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>LLM Tester</Text>
      <Text style={styles.description}>
        Test the LLM by entering a question below. The LLM will process your
        question based on your uploaded documents.
      </Text>

      <TextInput
        style={styles.input}
        placeholder="Enter your question here..."
        value={question}
        onChangeText={setQuestion}
        multiline
        numberOfLines={3}
      />

      <View style={styles.statusContainer}>
        <Text style={styles.statusText}>
          <Text style={styles.statusLabel}>Model:</Text>{" "}
          <Text style={isModelLoaded ? styles.statusGood : styles.statusBad}>
            {isModelLoaded ? "Loaded" : "Not Loaded"}
          </Text>
        </Text>

        <Text style={styles.statusText}>
          <Text style={styles.statusLabel}>Documents:</Text>{" "}
          <Text
            style={documents.length > 0 ? styles.statusGood : styles.statusBad}
          >
            {documents.length}
          </Text>
        </Text>
      </View>

      {documents.length > 0 && (
        <View style={styles.documentSummary}>
          <Text style={styles.documentSummaryTitle}>Document Types:</Text>
          <View style={styles.documentTypes}>
            <Text style={styles.documentTypeItem}>
              Text: {documents.filter((d) => !d.isBinary).length}
            </Text>
            <Text style={styles.documentTypeItem}>
              PDF: {documents.filter((d) => d.isPdf).length}
              {documents.filter((d) => d.isPdf).length > 0 && (
                <Text style={styles.documentTypeDetail}>
                  {" "}
                  (
                  {
                    documents.filter((d) => d.isPdf && d.extractedTextAvailable)
                      .length
                  }{" "}
                  extractable)
                </Text>
              )}
            </Text>
            <Text style={styles.documentTypeItem}>
              Other Binary:{" "}
              {documents.filter((d) => d.isBinary && !d.isPdf).length}
            </Text>
          </View>
        </View>
      )}

      <View style={styles.buttonRow}>
        <TouchableOpacity
          style={[
            styles.button,
            styles.mainButton,
            { flex: 0.5, marginRight: 4 },
          ]}
          onPress={testLLM}
          disabled={loading}
        >
          {loading && !isDocQA ? (
            <ActivityIndicator color="#ffffff" size="small" />
          ) : (
            <Text style={styles.buttonText}>Basic LLM</Text>
          )}
        </TouchableOpacity>

        <TouchableOpacity
          style={[
            styles.button,
            styles.qaButton,
            { flex: 0.5, marginHorizontal: 4 },
          ]}
          onPress={runDocumentQA}
          disabled={loading}
        >
          {loading && isDocQA ? (
            <ActivityIndicator color="#ffffff" size="small" />
          ) : (
            <Text style={styles.buttonText}>Document QA</Text>
          )}
        </TouchableOpacity>

        <TouchableOpacity
          style={[
            styles.button,
            styles.secondaryButton,
            { flex: 1, marginLeft: 4 },
          ]}
          onPress={verifyDocuments}
          disabled={loading}
        >
          <Text style={styles.secondaryButtonText}>Verify Documents</Text>
        </TouchableOpacity>
      </View>

      {error ? <Text style={styles.errorText}>{error}</Text> : null}

      {response ? (
        <View style={styles.responseContainer}>
          <Text style={styles.responseTitle}>LLM Response:</Text>
          <ScrollView style={styles.responseScrollView}>
            <Text style={styles.responseText}>{response}</Text>
          </ScrollView>
        </View>
      ) : null}

      <View style={styles.debugContainer}>
        <View style={styles.debugHeader}>
          <Text style={styles.debugTitle}>Debug Information</Text>
          <Switch
            value={showDebugInfo}
            onValueChange={setShowDebugInfo}
            trackColor={{ false: "#767577", true: "#81d4fa" }}
            thumbColor={showDebugInfo ? "#03a9f4" : "#f4f3f4"}
          />
        </View>

        {showDebugInfo && (
          <ScrollView style={styles.debugLogContainer}>
            <Text style={styles.debugLogText}>{debugLog}</Text>
          </ScrollView>
        )}
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    backgroundColor: "white",
    padding: 16,
    borderRadius: 8,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 2,
    marginBottom: 16,
  },
  title: {
    fontSize: 18,
    fontWeight: "600",
    color: "#333",
    marginBottom: 8,
  },
  description: {
    fontSize: 14,
    color: "#666",
    marginBottom: 16,
    lineHeight: 20,
  },
  statusContainer: {
    flexDirection: "row",
    justifyContent: "space-between",
    marginBottom: 12,
  },
  statusText: {
    fontSize: 14,
  },
  statusLabel: {
    fontWeight: "600",
    color: "#333",
  },
  statusGood: {
    color: "#4caf50",
    fontWeight: "500",
  },
  statusBad: {
    color: "#f44336",
    fontWeight: "500",
  },
  input: {
    borderWidth: 1,
    borderColor: "#ddd",
    borderRadius: 4,
    padding: 12,
    fontSize: 16,
    backgroundColor: "#f9f9f9",
    marginBottom: 16,
    textAlignVertical: "top",
  },
  documentSummary: {
    marginBottom: 16,
    backgroundColor: "#f5f5f5",
    padding: 8,
    borderRadius: 4,
  },
  documentSummaryTitle: {
    fontSize: 14,
    fontWeight: "600",
    color: "#333",
    marginBottom: 4,
  },
  documentTypes: {
    flexDirection: "row",
    flexWrap: "wrap",
  },
  documentTypeItem: {
    fontSize: 13,
    color: "#555",
    marginRight: 12,
  },
  documentTypeDetail: {
    fontSize: 12,
    color: "#777",
  },
  buttonRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    marginBottom: 16,
  },
  button: {
    padding: 12,
    borderRadius: 4,
    alignItems: "center",
    justifyContent: "center",
  },
  mainButton: {
    backgroundColor: "#2196f3",
    flex: 1,
    marginRight: 8,
  },
  qaButton: {
    backgroundColor: "#4caf50",
    flex: 1,
    marginRight: 8,
  },
  secondaryButton: {
    backgroundColor: "#f5f5f5",
    borderWidth: 1,
    borderColor: "#ddd",
    flex: 1,
    marginLeft: 8,
  },
  buttonText: {
    color: "white",
    fontWeight: "600",
    fontSize: 16,
  },
  secondaryButtonText: {
    color: "#666",
    fontWeight: "500",
    fontSize: 14,
  },
  errorText: {
    color: "#f44336",
    marginBottom: 16,
  },
  responseContainer: {
    borderTopWidth: 1,
    borderTopColor: "#eee",
    paddingTop: 16,
    marginTop: 8,
  },
  responseTitle: {
    fontSize: 16,
    fontWeight: "600",
    color: "#333",
    marginBottom: 8,
  },
  responseScrollView: {
    maxHeight: 150,
  },
  responseText: {
    fontSize: 16,
    color: "#333",
    lineHeight: 22,
  },
  debugContainer: {
    marginTop: 16,
    borderTopWidth: 1,
    borderTopColor: "#eee",
    paddingTop: 8,
  },
  debugHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: 8,
  },
  debugTitle: {
    fontSize: 14,
    fontWeight: "600",
    color: "#666",
  },
  debugLogContainer: {
    backgroundColor: "#f5f5f5",
    padding: 8,
    borderRadius: 4,
    maxHeight: 150,
  },
  debugLogText: {
    fontSize: 12,
    fontFamily: "monospace",
    color: "#333",
  },
});

export default LLMTester;
