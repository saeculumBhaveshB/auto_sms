import React, { useState, useEffect } from "react";
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  ScrollView,
  ActivityIndicator,
  TextInput,
  Alert,
  Switch,
  NativeModules,
} from "react-native";
import { LocalLLMService, AutoReplyService } from "../services";
import RNFS from "react-native-fs";

const { CallSmsModule } = NativeModules;

const LLMDebugScreen: React.FC = () => {
  const [loading, setLoading] = useState<boolean>(false);
  const [modelLoaded, setModelLoaded] = useState<boolean>(false);
  const [documents, setDocuments] = useState<{ name: string; size: number }[]>(
    []
  );
  const [testQuestion, setTestQuestion] = useState<string>(
    "Hello, are you working?"
  );
  const [response, setResponse] = useState<string>("");
  const [llmAutoReplyEnabled, setLlmAutoReplyEnabled] =
    useState<boolean>(false);
  const [logs, setLogs] = useState<string[]>([]);
  const [autoReplyEnabled, setAutoReplyEnabled] = useState<boolean>(false);
  const [testPhoneNumber, setTestPhoneNumber] = useState<string>("");
  const [testSmsMessage, setTestSmsMessage] = useState<string>(
    "Hello, this is a test message"
  );

  useEffect(() => {
    loadInitialState();
  }, []);

  const addLog = (message: string) => {
    setLogs((prev) => [
      `[${new Date().toLocaleTimeString()}] ${message}`,
      ...prev.slice(0, 19),
    ]);
  };

  const loadInitialState = async () => {
    setLoading(true);
    addLog("Loading initial state...");

    try {
      // Check model status
      const isLoaded = await LocalLLMService.isModelLoaded();
      setModelLoaded(isLoaded);
      addLog(`Model loaded: ${isLoaded}`);

      // Check documents
      const docs = await LocalLLMService.listDocuments();
      setDocuments(docs);
      addLog(`Documents: ${docs.length}`);

      // Check LLM auto-reply status
      const llmEnabled = await AutoReplyService.isLLMAutoReplyEnabled();
      setLlmAutoReplyEnabled(llmEnabled);
      addLog(`LLM auto-reply enabled: ${llmEnabled}`);

      // Check auto-reply status
      const isEnabled = await CallSmsModule.isAutoReplyEnabled();
      setAutoReplyEnabled(isEnabled);
      addLog(`Auto-reply enabled: ${isEnabled}`);
    } catch (error) {
      console.error("Error loading initial state:", error);
      addLog(`ERROR: ${error}`);
    } finally {
      setLoading(false);
    }
  };

  const handleToggleLlmAutoReply = async (value: boolean) => {
    setLoading(true);
    addLog(`Setting LLM auto-reply to ${value}`);

    try {
      // Make sure we have a model and documents before enabling
      if (value) {
        if (!modelLoaded) {
          await loadModel();
        }

        if (documents.length === 0) {
          await createSampleDocument();
        }
      }

      const result = await AutoReplyService.setLLMAutoReplyEnabled(value);
      setLlmAutoReplyEnabled(value);
      addLog(
        `LLM auto-reply set to ${value}: ${result ? "success" : "failed"}`
      );
    } catch (error) {
      console.error("Error toggling LLM auto-reply:", error);
      addLog(`ERROR: ${error}`);
      Alert.alert("Error", `Failed to toggle LLM auto-reply: ${error}`);
    } finally {
      setLoading(false);
    }
  };

  const handleToggleAutoReply = async (value: boolean) => {
    setLoading(true);
    addLog(`Setting auto-reply to ${value}`);

    try {
      const result = await CallSmsModule.setAutoReplyEnabled(value);
      setAutoReplyEnabled(value);
      addLog(`Auto-reply set to ${value}: ${result ? "success" : "failed"}`);
    } catch (error) {
      console.error("Error toggling auto-reply:", error);
      addLog(`ERROR: ${error}`);
      Alert.alert("Error", `Failed to toggle auto-reply: ${error}`);
    } finally {
      setLoading(false);
    }
  };

  const loadModel = async () => {
    setLoading(true);
    addLog("Loading model...");

    try {
      // Create a model directory and fake model file for testing
      const modelPath = `${RNFS.DocumentDirectoryPath}/models/default_model.bin`;
      const modelDir = modelPath.substring(0, modelPath.lastIndexOf("/"));

      // Create directories if they don't exist
      const dirExists = await RNFS.exists(modelDir);
      if (!dirExists) {
        await RNFS.mkdir(modelDir);
        addLog(`Created model directory: ${modelDir}`);
      }

      // Create an empty model file if it doesn't exist
      const fileExists = await RNFS.exists(modelPath);
      if (!fileExists) {
        await RNFS.writeFile(
          modelPath,
          "This is a placeholder model file",
          "utf8"
        );
        addLog(`Created placeholder model at: ${modelPath}`);
      }

      // Save the selected model
      await LocalLLMService.saveSelectedModel(modelPath);

      // Load the model
      const result = await LocalLLMService.loadModel(modelPath);
      setModelLoaded(result);
      addLog(`Model load result: ${result}`);

      if (result) {
        Alert.alert("Success", "Model loaded successfully");
      } else {
        Alert.alert("Warning", "Failed to load model");
      }
    } catch (error) {
      console.error("Error loading model:", error);
      addLog(`ERROR loading model: ${error}`);
      Alert.alert("Error", `Failed to load model: ${error}`);
    } finally {
      setLoading(false);
    }
  };

  const unloadModel = async () => {
    setLoading(true);
    addLog("Unloading model...");

    try {
      const result = await LocalLLMService.unloadModel();
      setModelLoaded(false);
      addLog(`Model unload result: ${result}`);

      if (result) {
        Alert.alert("Success", "Model unloaded successfully");
      } else {
        Alert.alert("Warning", "Model was not loaded");
      }
    } catch (error) {
      console.error("Error unloading model:", error);
      addLog(`ERROR: ${error}`);
      Alert.alert("Error", `Failed to unload model: ${error}`);
    } finally {
      setLoading(false);
    }
  };

  const createSampleDocument = async () => {
    setLoading(true);
    addLog("Creating sample document...");

    try {
      const content = `
# Sample Document for Auto-Reply

## Company Information
Our company provides excellent customer service 24/7.
You can reach our support team at support@example.com.

## Product Information
Our product is a mobile app that helps users with automatic SMS replies.

## FAQ
Q: When will my order arrive?
A: Orders typically arrive within 3-5 business days.

Q: How do I contact support?
A: You can email support@example.com or call us at 555-123-4567.

Q: What's your refund policy?
A: We offer full refunds within 30 days of purchase.

Q: How does the auto-reply feature work?
A: When you miss a call, the app sends an automatic SMS. When they reply, our local LLM provides an intelligent response based on your uploaded documents.
      `;

      const result = await LocalLLMService.createSampleDocument(content);
      addLog(`Sample document created: ${result ? "success" : "failed"}`);

      // Refresh document list
      const docs = await LocalLLMService.listDocuments();
      setDocuments(docs);
      addLog(`Updated document count: ${docs.length}`);

      Alert.alert("Success", "Sample document created successfully");
    } catch (error) {
      console.error("Error creating sample document:", error);
      addLog(`ERROR: ${error}`);
      Alert.alert("Error", `Failed to create sample document: ${error}`);
    } finally {
      setLoading(false);
    }
  };

  const testGeneration = async () => {
    setLoading(true);
    setResponse("");
    addLog(`Testing generation with: "${testQuestion}"`);

    try {
      if (!modelLoaded) {
        addLog("Loading model first...");
        await loadModel();
      }

      const answer = await LocalLLMService.generateAnswer(testQuestion);
      setResponse(answer);
      addLog(`Response: ${answer}`);
    } catch (error) {
      console.error("Error generating response:", error);
      addLog(`ERROR: ${error}`);
      Alert.alert("Error", `Failed to generate response: ${error}`);
    } finally {
      setLoading(false);
    }
  };

  const runDiagnostics = async () => {
    setLoading(true);
    addLog("Running diagnostics...");

    try {
      const results = await AutoReplyService.runLLMDiagnostics();
      addLog(`Diagnostics complete: ${JSON.stringify(results)}`);

      // Display diagnostic results
      Alert.alert(
        "Diagnostics Results",
        `LLM Module Available: ${results.llmModuleAvailable ? "Yes" : "No"}\n` +
          `Model Loaded: ${results.modelLoaded ? "Yes" : "No"}\n` +
          `Documents Count: ${results.documentCount || 0}\n` +
          `Simple Auto-Reply: ${
            results.simpleAutoReplyEnabled ? "Enabled" : "Disabled"
          }\n` +
          `LLM Auto-Reply: ${
            results.llmAutoReplyEnabled ? "Enabled" : "Disabled"
          }`
      );
    } catch (error) {
      console.error("Error running diagnostics:", error);
      addLog(`ERROR: ${error}`);
      Alert.alert("Error", `Failed to run diagnostics: ${error}`);
    } finally {
      setLoading(false);
    }
  };

  const addTestNumber = async () => {
    if (!testPhoneNumber) {
      Alert.alert("Error", "Please enter a phone number");
      return;
    }

    setLoading(true);
    addLog(`Adding test phone number: ${testPhoneNumber}`);

    try {
      const result = await CallSmsModule.addTestPhoneNumber(testPhoneNumber);
      addLog(`Test number added: ${result ? "success" : "failed"}`);
      Alert.alert(
        "Success",
        `Added ${testPhoneNumber} to missed call numbers list`
      );
    } catch (error) {
      console.error("Error adding test number:", error);
      addLog(`ERROR: ${error}`);
      Alert.alert("Error", `Failed to add test number: ${error}`);
    } finally {
      setLoading(false);
    }
  };

  const sendTestSms = async () => {
    if (!testPhoneNumber) {
      Alert.alert("Error", "Please enter a phone number");
      return;
    }

    setLoading(true);
    addLog(`Sending test SMS to ${testPhoneNumber}: "${testSmsMessage}"`);

    try {
      const result = await CallSmsModule.sendTestSms(
        testPhoneNumber,
        testSmsMessage
      );
      addLog(`Test SMS sent: ${result ? "success" : "failed"}`);
      Alert.alert("Success", `Test SMS sent to ${testPhoneNumber}`);
    } catch (error) {
      console.error("Error sending test SMS:", error);
      addLog(`ERROR: ${error}`);
      Alert.alert("Error", `Failed to send test SMS: ${error}`);
    } finally {
      setLoading(false);
    }
  };

  return (
    <ScrollView style={styles.container}>
      {loading && (
        <View style={styles.loadingOverlay}>
          <ActivityIndicator size="large" color="#2196F3" />
          <Text style={styles.loadingText}>Processing...</Text>
        </View>
      )}

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>LLM Auto-Reply Status</Text>
        <View style={styles.row}>
          <Text style={styles.label}>Enable LLM Auto-Reply:</Text>
          <Switch
            value={llmAutoReplyEnabled}
            onValueChange={handleToggleLlmAutoReply}
          />
        </View>

        <View style={styles.statusContainer}>
          <Text style={styles.label}>Model Status:</Text>
          <Text
            style={[styles.value, modelLoaded ? styles.success : styles.error]}
          >
            {modelLoaded ? "Loaded" : "Not Loaded"}
          </Text>
        </View>

        <View style={styles.statusContainer}>
          <Text style={styles.label}>Documents:</Text>
          <Text
            style={[
              styles.value,
              documents.length > 0 ? styles.success : styles.error,
            ]}
          >
            {documents.length} document(s)
          </Text>
        </View>

        <View style={styles.buttonsRow}>
          <TouchableOpacity style={styles.button} onPress={loadModel}>
            <Text style={styles.buttonText}>Load Model</Text>
          </TouchableOpacity>
          <TouchableOpacity style={styles.button} onPress={unloadModel}>
            <Text style={styles.buttonText}>Unload Model</Text>
          </TouchableOpacity>
        </View>

        <TouchableOpacity style={styles.button} onPress={createSampleDocument}>
          <Text style={styles.buttonText}>Create Sample Document</Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={styles.diagnosticButton}
          onPress={runDiagnostics}
        >
          <Text style={styles.buttonText}>Run Diagnostics</Text>
        </TouchableOpacity>
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Test Generation</Text>
        <TextInput
          style={styles.input}
          value={testQuestion}
          onChangeText={setTestQuestion}
          placeholder="Enter test question"
          multiline
        />

        <TouchableOpacity style={styles.submitButton} onPress={testGeneration}>
          <Text style={styles.buttonText}>Generate Response</Text>
        </TouchableOpacity>

        {response ? (
          <View style={styles.responseContainer}>
            <Text style={styles.responseLabel}>Response:</Text>
            <Text style={styles.responseText}>{response}</Text>
          </View>
        ) : null}
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>SMS Auto-Reply Testing</Text>
        <View style={styles.row}>
          <Text style={styles.label}>Enable Auto-Reply:</Text>
          <Switch
            value={autoReplyEnabled}
            onValueChange={handleToggleAutoReply}
          />
        </View>

        <Text style={styles.label}>Test Phone Number:</Text>
        <TextInput
          style={styles.input}
          value={testPhoneNumber}
          onChangeText={setTestPhoneNumber}
          placeholder="Enter phone number"
          keyboardType="phone-pad"
        />

        <TouchableOpacity style={styles.button} onPress={addTestNumber}>
          <Text style={styles.buttonText}>Add as Missed Call Number</Text>
        </TouchableOpacity>

        <Text style={styles.label}>Test Message:</Text>
        <TextInput
          style={styles.input}
          value={testSmsMessage}
          onChangeText={setTestSmsMessage}
          placeholder="Enter test message"
          multiline
        />

        <TouchableOpacity
          style={[styles.button, { backgroundColor: "#FF9800" }]}
          onPress={sendTestSms}
        >
          <Text style={styles.buttonText}>Send Test SMS</Text>
        </TouchableOpacity>
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Debug Logs</Text>
        <ScrollView style={styles.logsContainer}>
          {logs.map((log, index) => (
            <Text key={index} style={styles.logText}>
              {log}
            </Text>
          ))}
        </ScrollView>
        <TouchableOpacity
          style={styles.clearButton}
          onPress={() => setLogs([])}
        >
          <Text style={styles.buttonText}>Clear Logs</Text>
        </TouchableOpacity>
      </View>
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#f5f5f5",
  },
  loadingOverlay: {
    position: "absolute",
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: "rgba(0, 0, 0, 0.4)",
    zIndex: 1000,
  },
  loadingText: {
    color: "white",
    marginTop: 10,
  },
  section: {
    backgroundColor: "white",
    margin: 10,
    padding: 15,
    borderRadius: 10,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 3,
    elevation: 3,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: "bold",
    marginBottom: 15,
    color: "#333",
  },
  row: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: 10,
  },
  label: {
    fontSize: 16,
    color: "#333",
  },
  value: {
    fontSize: 16,
    fontWeight: "500",
  },
  success: {
    color: "#4caf50",
  },
  error: {
    color: "#f44336",
  },
  statusContainer: {
    flexDirection: "row",
    justifyContent: "space-between",
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderBottomColor: "#eee",
  },
  buttonsRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    marginTop: 15,
    marginBottom: 10,
  },
  button: {
    backgroundColor: "#2196F3",
    paddingVertical: 10,
    paddingHorizontal: 15,
    borderRadius: 5,
    flex: 1,
    marginHorizontal: 5,
    alignItems: "center",
  },
  diagnosticButton: {
    backgroundColor: "#FF9800",
    paddingVertical: 10,
    paddingHorizontal: 15,
    borderRadius: 5,
    marginTop: 10,
    alignItems: "center",
  },
  submitButton: {
    backgroundColor: "#4CAF50",
    paddingVertical: 10,
    paddingHorizontal: 15,
    borderRadius: 5,
    marginTop: 10,
    alignItems: "center",
  },
  clearButton: {
    backgroundColor: "#f44336",
    paddingVertical: 10,
    paddingHorizontal: 15,
    borderRadius: 5,
    marginTop: 10,
    alignItems: "center",
  },
  buttonText: {
    color: "white",
    fontWeight: "500",
  },
  input: {
    borderWidth: 1,
    borderColor: "#ccc",
    borderRadius: 5,
    padding: 10,
    minHeight: 80,
    textAlignVertical: "top",
  },
  responseContainer: {
    marginTop: 15,
    padding: 10,
    backgroundColor: "#f0f0f0",
    borderRadius: 5,
  },
  responseLabel: {
    fontSize: 16,
    fontWeight: "bold",
    color: "#333",
    marginBottom: 5,
  },
  responseText: {
    fontSize: 16,
    color: "#333",
  },
  logsContainer: {
    maxHeight: 200,
    backgroundColor: "#333",
    borderRadius: 5,
    padding: 10,
  },
  logText: {
    color: "#fff",
    fontFamily: "monospace",
    fontSize: 12,
    marginBottom: 5,
  },
});

export default LLMDebugScreen;
