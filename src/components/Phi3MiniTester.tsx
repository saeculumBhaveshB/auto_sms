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
  Alert,
  FlatList,
} from "react-native";
import { NativeModules } from "react-native";

// Use the actual native module for real Phi-3-mini inference
const { Phi3MiniModule } = NativeModules;

interface Phi3MiniTesterProps {
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

const Phi3MiniTester: React.FC<Phi3MiniTesterProps> = () => {
  const [question, setQuestion] = useState<string>("");
  const [response, setResponse] = useState<string>("");
  const [loading, setLoading] = useState<boolean>(false);
  const [modelLoading, setModelLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [isModelLoaded, setIsModelLoaded] = useState<boolean>(false);
  const [documents, setDocuments] = useState<Document[]>([]);
  const [showDebugInfo, setShowDebugInfo] = useState<boolean>(false);
  const [debugLog, setDebugLog] = useState<string>("");
  const [retrievingContext, setRetrievingContext] = useState<boolean>(false);

  // Load model status and documents on component mount
  useEffect(() => {
    const init = async () => {
      try {
        logDebug("🚀 Initializing Phi-3-mini Tester");

        // Check if model is loaded
        try {
          const modelLoaded = await Phi3MiniModule.isAvailable();
          logDebug(`🔍 Model loaded status: ${modelLoaded}`);
          setIsModelLoaded(modelLoaded);
        } catch (e) {
          logDebug(`❌ Error checking model status: ${e}`);
          setIsModelLoaded(false);
        }

        // Get documents
        await refreshDocuments();
      } catch (err: unknown) {
        const errorMessage = err instanceof Error ? err.message : String(err);
        logDebug(`❌ Error initializing: ${errorMessage}`);
      }
    };

    init();
  }, []);

  const refreshDocuments = async () => {
    try {
      // Get available documents
      const docs = await Phi3MiniModule.getAvailableDocuments();
      setDocuments(docs);
      logDebug(`📄 Found ${docs.length} documents`);
    } catch (e) {
      logDebug(`❌ Error getting documents: ${e}`);
    }
  };

  const logDebug = (message: string) => {
    setDebugLog(
      (prev) =>
        `${prev}\n${new Date().toISOString().slice(11, 19)} - ${message}`
    );
    console.log(message);
  };

  const loadModel = async () => {
    setModelLoading(true);
    setError(null);

    try {
      logDebug("🧠 Attempting to load Phi-3-mini model");

      // First check if we need to download the model
      let modelPath;
      try {
        modelPath = await Phi3MiniModule.downloadModelIfNeeded();
        logDebug(`📁 Model path: ${modelPath}`);
      } catch (e: any) {
        logDebug(`⚠️ Model download warning: ${e.message}`);

        // We'll show an alert to the user, but continue with the flow
        // In a production app, you would handle this more gracefully
        Alert.alert(
          "Model Download Required",
          `Phi-3-mini model is not available locally. Please download it manually and place it in the app's model directory.\n\n${e.message}`,
          [{ text: "OK" }]
        );
        setModelLoading(false);
        return;
      }

      // Now load the model
      const loaded = await Phi3MiniModule.loadModel(modelPath);
      setIsModelLoaded(loaded);
      logDebug(`✅ Model loaded: ${loaded}`);
    } catch (e: any) {
      logDebug(`❌ Error loading model: ${e.message}`);
      setError(`Failed to load model: ${e.message}`);
    } finally {
      setModelLoading(false);
    }
  };

  const testPhi3 = async () => {
    if (!question.trim()) {
      setError("Please enter a question to test");
      return;
    }

    if (!isModelLoaded) {
      setError("Model is not loaded yet. Please load the model first.");
      return;
    }

    try {
      setLoading(true);
      setError(null);
      setResponse("");

      logDebug(`🔍 Testing Phi-3-mini with question: "${question}"`);

      // First, retrieve relevant document context
      setRetrievingContext(true);
      let documentContext = null;
      try {
        logDebug(`📑 Retrieving document context for query`);
        documentContext = await Phi3MiniModule.retrieveDocumentContextForQuery(
          question
        );

        if (documentContext) {
          logDebug(
            `✅ Retrieved document context: ${documentContext.substring(
              0,
              100
            )}...`
          );
        } else {
          logDebug(`⚠️ No relevant document context found`);
        }
      } catch (e: any) {
        logDebug(`⚠️ Error retrieving context: ${e.message}`);
      }
      setRetrievingContext(false);

      // Format the prompt according to Phi-3-mini's requirements
      const formattedPrompt = await Phi3MiniModule.formatPromptForPhi(
        question,
        documentContext
      );
      logDebug(
        `📝 Formatted prompt with ${
          documentContext ? "document context" : "no context"
        }`
      );

      // Generate the response
      const maxTokens = 512;
      const temperature = 0.7;
      const topP = 0.9;

      logDebug(
        `🤖 Generating response with maxTokens=${maxTokens}, temp=${temperature}, topP=${topP}`
      );
      const result = await Phi3MiniModule.generate(
        formattedPrompt,
        maxTokens,
        temperature,
        topP
      );

      setResponse(result);
      logDebug(`📝 Response received`);
    } catch (err: any) {
      logDebug(`❌ Error generating response: ${err.message}`);
      setError(`Error: ${err.message || "Unknown error"}`);
      setResponse(
        "AI: I encountered an issue while generating a response. Please try again later."
      );
    } finally {
      setLoading(false);
    }
  };

  const unloadModel = async () => {
    try {
      setModelLoading(true);
      logDebug("🧹 Unloading model");

      await Phi3MiniModule.unloadModel();
      setIsModelLoaded(false);
      logDebug("✅ Model unloaded successfully");
    } catch (e: any) {
      logDebug(`❌ Error unloading model: ${e.message}`);
      setError(`Failed to unload model: ${e.message}`);
    } finally {
      setModelLoading(false);
    }
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Phi-3-mini Tester</Text>
      <Text style={styles.description}>
        Test the Phi-3-mini LLM on your device. This powerful, compact model can
        answer questions based on your documents.
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

      <View style={styles.buttonRow}>
        <TouchableOpacity
          style={[
            styles.button,
            isModelLoaded ? styles.unloadButton : styles.loadButton,
            { flex: 1, marginRight: 4 },
          ]}
          onPress={isModelLoaded ? unloadModel : loadModel}
          disabled={modelLoading}
        >
          {modelLoading ? (
            <ActivityIndicator color="#ffffff" size="small" />
          ) : (
            <Text style={styles.buttonText}>
              {isModelLoaded ? "Unload Model" : "Load Model"}
            </Text>
          )}
        </TouchableOpacity>

        <TouchableOpacity
          style={[styles.button, styles.mainButton, { flex: 1, marginLeft: 4 }]}
          onPress={testPhi3}
          disabled={loading || !isModelLoaded}
        >
          {loading ? (
            <ActivityIndicator color="#ffffff" size="small" />
          ) : (
            <Text style={styles.buttonText}>Generate Answer</Text>
          )}
        </TouchableOpacity>
      </View>

      {retrievingContext && (
        <View style={styles.contextRetrievalIndicator}>
          <ActivityIndicator
            color="#2196f3"
            size="small"
            style={{ marginRight: 8 }}
          />
          <Text>Retrieving document context...</Text>
        </View>
      )}

      {error ? <Text style={styles.errorText}>{error}</Text> : null}

      {response ? (
        <View style={styles.responseContainer}>
          <Text style={styles.responseTitle}>Phi-3-mini Response:</Text>
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
  },
  loadButton: {
    backgroundColor: "#4caf50",
  },
  unloadButton: {
    backgroundColor: "#f44336",
  },
  buttonText: {
    color: "white",
    fontWeight: "600",
    fontSize: 16,
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
  contextRetrievalIndicator: {
    flexDirection: "row",
    alignItems: "center",
    marginBottom: 12,
    backgroundColor: "#e3f2fd",
    padding: 8,
    borderRadius: 4,
  },
});

export default Phi3MiniTester;
