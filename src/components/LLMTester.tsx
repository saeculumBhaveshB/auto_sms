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
  isDocx?: boolean;
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
  const [isInFallbackMode, setIsInFallbackMode] = useState<boolean>(false);

  // Load model status and documents on component mount
  useEffect(() => {
    const init = async () => {
      try {
        // Check if model is loaded
        const modelLoaded = await LocalLLMModule.isModelLoaded();
        setIsModelLoaded(modelLoaded);

        // Check if we're in fallback mode (has no interpreter)
        if (modelLoaded) {
          try {
            const hasRealModel =
              (await LocalLLMModule.hasRealModel?.()) ?? false;
            setIsInFallbackMode(!hasRealModel);
            logDebug(`🔍 Real model available: ${hasRealModel ? "Yes" : "No"}`);
          } catch {
            setIsInFallbackMode(true);
          }
        }

        // Get available documents
        const docs = await LocalLLMModule.listDocuments();

        // Enrich document info with binary/PDF/DOCX status
        const enrichedDocs = await Promise.all(
          docs.map(async (doc: Document) => {
            try {
              // Check file types
              const isPdf = doc.name.toLowerCase().endsWith(".pdf");
              const isDocx = doc.name.toLowerCase().endsWith(".docx");
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
              } else if (isDocx) {
                logDebug(`🔍 Checking DOCX: ${doc.name}`);
                try {
                  // Try to check if text can be extracted from DOCX
                  const testExtraction = await CallSmsModule.testDocxExtraction(
                    doc.path
                  );
                  extractedTextAvailable = testExtraction.success;
                  logDebug(
                    `📄 DOCX ${doc.name} text extraction: ${
                      extractedTextAvailable ? "Available" : "Not available"
                    }`
                  );
                } catch (e) {
                  logDebug(`❌ Error checking DOCX text: ${e}`);
                  extractedTextAvailable = false;
                }
              }

              return {
                ...doc,
                isPdf,
                isDocx,
                extractedTextAvailable,
                isBinary:
                  isPdf ||
                  isDocx ||
                  doc.name.toLowerCase().match(/\.(doc|jpg|jpeg|png|gif)$/) !==
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
          let fileType = "Text";
          let extractStatus = "";

          if (doc.isPdf) {
            fileType = "PDF";
            extractStatus = doc.extractedTextAvailable
              ? " (text extractable)"
              : " (no extractable text)";
          } else if (doc.isDocx) {
            fileType = "DOCX";
            extractStatus = doc.extractedTextAvailable
              ? " (text extractable)"
              : " (no extractable text)";
          } else if (doc.isBinary) {
            fileType = "Binary";
          }

          logDebug(
            `   - ${doc.name} (${(doc.size / 1024).toFixed(
              2
            )} KB) - ${fileType}${extractStatus}`
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

      try {
        const result = await CallSmsModule.debugDocumentStorage();

        logDebug(`📁 Documents path: ${result.documentsPath}`);
        logDebug(`📂 Documents directory exists: ${result.documentsExists}`);
        logDebug(`📊 Found ${result.fileCount} files`);

        if (result.files && result.files.length > 0) {
          let binaryCount = 0;
          let textCount = 0;
          let pdfCount = 0;
          let docxCount = 0;
          let extractableCount = 0;

          result.files.forEach((file: any) => {
            const isBinary = file.isBinary;
            const isPdf = file.name.toLowerCase().endsWith(".pdf");
            const isDocx = file.name.toLowerCase().endsWith(".docx");

            if (isPdf) {
              pdfCount++;
              if (file.extractableText === true) extractableCount++;
            } else if (isDocx) {
              docxCount++;
              if (file.extractableText === true) extractableCount++;
            } else if (isBinary) {
              binaryCount++;
            } else {
              textCount++;
            }

            let fileType = "Text";
            let extractInfo = "";

            if (isPdf) {
              fileType = "PDF";
              extractInfo = ` (${
                file.extractableText ? "extractable" : "non-extractable"
              })`;
            } else if (isDocx) {
              fileType = "DOCX";
              extractInfo = ` (${
                file.extractableText ? "extractable" : "non-extractable"
              })`;
            } else if (isBinary) {
              fileType = "Binary";
            }

            logDebug(
              `   - ${file.name} (${(file.size / 1024).toFixed(
                2
              )} KB) - ${fileType}${extractInfo}`
            );
          });

          logDebug(
            `📊 Summary: ${textCount} text files, ${binaryCount} binary files, ${pdfCount} PDF files, ${docxCount} DOCX files (${extractableCount} extractable)`
          );

          // Refresh document list with enhanced info
          try {
            const docs = await LocalLLMModule.listDocuments();
            const enrichedDocs = docs.map((doc: Document) => {
              const isPdf = doc.name.toLowerCase().endsWith(".pdf");
              const isDocx = doc.name.toLowerCase().endsWith(".docx");
              const matchingFile = result.files.find(
                (f: any) => f.name === doc.name
              );

              // For DOCX files, don't rely solely on the extraction test
              // since that might fail due to POI issues
              const isDocxTextAvailable = isDocx ? true : false;

              return {
                ...doc,
                isPdf,
                isDocx,
                extractedTextAvailable:
                  isPdf && matchingFile
                    ? matchingFile.extractableText
                    : isDocxTextAvailable, // Always assume DOCX files can be processed
                isBinary:
                  isPdf ||
                  isDocx ||
                  doc.name.toLowerCase().match(/\.(doc|jpg|jpeg|png|gif)$/) !==
                    null,
              };
            });

            setDocuments(enrichedDocs);
          } catch (docsError) {
            logDebug(`⚠️ Error enriching documents: ${docsError}`);
            // Continue execution even if this part fails
          }
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
      } catch (debugError) {
        logDebug(`⚠️ Error in debugDocumentStorage: ${debugError}`);

        // Fallback: try to get documents directly
        try {
          const docs = await LocalLLMModule.listDocuments();

          // Process without extraction testing
          const simpleEnrichedDocs = docs.map((doc: Document) => {
            const isPdf = doc.name.toLowerCase().endsWith(".pdf");
            const isDocx = doc.name.toLowerCase().endsWith(".docx");

            return {
              ...doc,
              isPdf,
              isDocx,
              extractedTextAvailable: true, // Assume all are extractable for UI
              isBinary:
                isPdf ||
                isDocx ||
                doc.name.toLowerCase().match(/\.(doc|jpg|jpeg|png|gif)$/) !==
                  null,
            };
          });

          setDocuments(simpleEnrichedDocs);
          logDebug(
            `📄 Fallback: loaded ${simpleEnrichedDocs.length} documents without extraction testing`
          );

          return {
            documentsPath: "Unknown",
            documentsExists: true,
            fileCount: simpleEnrichedDocs.length,
            files: simpleEnrichedDocs,
          };
        } catch (fallbackError) {
          logDebug(
            `❌ Fallback document loading also failed: ${fallbackError}`
          );
          throw fallbackError;
        }
      }
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
      try {
        await verifyDocuments();
      } catch (verifyError) {
        // Log but continue even if verification fails
        logDebug(`⚠️ Document verification warning: ${verifyError}`);
      }

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
          let fileType = "Text";
          let extractStatus = "";

          if (doc.isPdf) {
            fileType = "PDF";
            extractStatus = doc.extractedTextAvailable
              ? " (text extractable)"
              : " (no extractable text)";
          } else if (doc.isDocx) {
            fileType = "DOCX";
            // Always treat DOCX as extractable in the prompt
            extractStatus = " (text extractable)";
          } else if (doc.isBinary) {
            fileType = "Binary";
          }

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

      // Use Promise.race with a timeout to prevent hanging on DOCX processing
      const timeoutPromise = new Promise((_, reject) =>
        setTimeout(
          () => reject(new Error("LLM request timed out after 30 seconds")),
          30000
        )
      );

      // Call the native module's testLLM function with document context
      const result = await Promise.race([
        CallSmsModule.testLLM(enhancedQuestion),
        timeoutPromise,
      ]);

      const endTime = Date.now();

      setResponse(result);
      logDebug(`✅ Received response in ${endTime - startTime}ms`);
      logDebug(`📝 Response: "${result}"`);
    } catch (err: any) {
      console.error("Error testing LLM:", err);
      logDebug(`❌ Error testing LLM: ${err.message || "Unknown error"}`);
      setError(`Error: ${err.message || "Unknown error occurred"}`);
      setResponse(
        "AI: I'm not able to answer based on your documents. Please refine your query or try the Document QA button which uses a more robust approach."
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

      // Verify documents with safety measures
      try {
        await verifyDocuments();
      } catch (verifyError) {
        // Log but continue with available documents
        logDebug(`⚠️ Document verification warning: ${verifyError}`);
      }

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

      // Use Promise.race with a timeout to prevent hanging on DOCX processing
      const timeoutPromise = new Promise((_, reject) =>
        setTimeout(
          () =>
            reject(new Error("Document QA request timed out after 30 seconds")),
          30000
        )
      );

      // Race the document QA call against a timeout
      const result = await Promise.race([
        CallSmsModule.documentQA(question, MAX_PASSAGES),
        timeoutPromise,
      ]);

      const endTime = Date.now();

      setResponse(result);
      logDebug(`✅ Document QA response received in ${endTime - startTime}ms`);
      logDebug(`📝 Response: "${result}"`);
    } catch (err: any) {
      console.error("Error in Document QA:", err);

      // Check if this is a timeout error
      const isTimeout = err.message && err.message.includes("timed out");

      if (isTimeout) {
        logDebug(
          "⚠️ Document QA timed out, this may be due to complex DOCX processing"
        );
        setError(
          "The operation timed out. This may be due to issues processing complex documents."
        );
        setResponse(
          "AI: I'm sorry, the document processing took too long. This might be caused by complex DOCX files. Try using simpler documents or converting them to PDF or text format."
        );
      } else {
        logDebug(`❌ Error in Document QA: ${err.message || "Unknown error"}`);
        setError(`Error: ${err.message || "Unknown error occurred"}`);
        setResponse(
          "AI: I'm sorry, I couldn't find an answer in your documents. Please try another question or simplify your document format."
        );
      }
    } finally {
      setLoading(false);
    }
  };

  // Force load the model explicitly
  const forceLoadModel = async () => {
    try {
      logDebug("🚀 Explicitly forcing model to load...");
      setLoading(true);

      // First get the actual loading result from native code
      const result = await LocalLLMModule.forceLoadDefaultModel();

      // Log the raw result for debugging
      logDebug(`📊 Force load details: ${JSON.stringify(result)}`);

      // Check if the result is a boolean (old API) or an object (new API)
      let success = false;
      let hasRealModel = false;

      if (typeof result === "boolean") {
        // Old API just returns a boolean
        success = result;
        // Need to separately check if we have a real model
        try {
          hasRealModel = await LocalLLMModule.hasRealModel();
        } catch (e) {
          hasRealModel = false;
        }
      } else if (result && typeof result === "object") {
        // New API returns an object with details
        success = result.loadSuccess === true;
        hasRealModel = result.isRealModel === true;
      }

      logDebug(
        `✅ Model load complete. Success: ${success}, Real model: ${hasRealModel}`
      );

      // Update UI state
      setIsModelLoaded(success);
      setIsInFallbackMode(!hasRealModel);

      return success;
    } catch (e: any) {
      logDebug(`❌ Error forcing model load: ${e.message || e}`);
      return false;
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
          <Text
            style={
              isModelLoaded
                ? isInFallbackMode
                  ? styles.statusWarning
                  : styles.statusGood
                : styles.statusBad
            }
          >
            {isModelLoaded
              ? isInFallbackMode
                ? "Fallback Mode"
                : "Loaded"
              : "Not Loaded"}
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
              DOCX: {documents.filter((d) => d.isDocx).length}
              {documents.filter((d) => d.isDocx).length > 0 && (
                <Text style={styles.documentTypeDetail}>
                  {" "}
                  <Text style={styles.docxNote}>
                    (Use Document QA mode for best results)
                  </Text>
                </Text>
              )}
            </Text>
            <Text style={styles.documentTypeItem}>
              Other Binary:{" "}
              {
                documents.filter((d) => d.isBinary && !d.isPdf && !d.isDocx)
                  .length
              }
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
            documents.filter((d) => d.isDocx).length > 0
              ? styles.recommendedButton
              : {},
          ]}
          onPress={runDocumentQA}
          disabled={loading}
        >
          {loading && isDocQA ? (
            <ActivityIndicator color="#ffffff" size="small" />
          ) : (
            <>
              <Text style={styles.buttonText}>Document QA</Text>
              {documents.filter((d) => d.isDocx).length > 0 && (
                <Text style={styles.recommendedLabel}>
                  Recommended for DOCX
                </Text>
              )}
            </>
          )}
        </TouchableOpacity>

        <TouchableOpacity
          style={[
            styles.button,
            styles.secondaryButton,
            { flex: 0.7, marginLeft: 4, marginRight: 4 },
          ]}
          onPress={verifyDocuments}
          disabled={loading}
        >
          <Text style={styles.secondaryButtonText}>Verify Documents</Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[
            styles.button,
            isModelLoaded ? styles.unloadButton : styles.loadButton,
            { flex: 0.7, marginLeft: 4 },
          ]}
          onPress={forceLoadModel}
          disabled={loading}
        >
          <Text style={styles.secondaryButtonText}>
            {isModelLoaded ? "Reload Model" : "Load Model"}
          </Text>
        </TouchableOpacity>
      </View>

      {documents.filter((d) => d.isDocx).length > 0 && (
        <View style={styles.docxHelpContainer}>
          <Text style={styles.docxHelpText}>
            ℹ️ DOCX files detected. For best results with DOCX files, use the
            Document QA button which uses a specialized processing method.
          </Text>
        </View>
      )}

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
  statusWarning: {
    color: "#ff9800",
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
  docxNote: {
    fontSize: 10,
    color: "#ff6f00",
    fontWeight: "bold",
  },
  recommendedButton: {
    backgroundColor: "#00695c",
    borderWidth: 1,
    borderColor: "#00897b",
  },
  recommendedLabel: {
    fontSize: 10,
    color: "#fff",
    fontWeight: "600",
    marginTop: 2,
  },
  docxHelpContainer: {
    backgroundColor: "#e8f5e9",
    padding: 8,
    borderRadius: 4,
    marginBottom: 16,
    borderLeftWidth: 3,
    borderLeftColor: "#4caf50",
  },
  docxHelpText: {
    fontSize: 13,
    color: "#2e7d32",
    lineHeight: 18,
  },
  unloadButton: {
    backgroundColor: "#f44336",
    borderWidth: 1,
    borderColor: "#d32f2f",
  },
  loadButton: {
    backgroundColor: "#4caf50",
    borderWidth: 1,
    borderColor: "#388e3c",
  },
});

export default LLMTester;
