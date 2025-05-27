import React, { useState, useEffect } from "react";
import {
  View,
  Text,
  StyleSheet,
  TextInput,
  TouchableOpacity,
  ScrollView,
  Alert,
  ActivityIndicator,
  Platform,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import LocalLLMService from "../services/LocalLLMService";

const TestAutoReplyScreen: React.FC = () => {
  const [phoneNumber, setPhoneNumber] = useState<string>("+1234567890");
  const [message, setMessage] = useState<string>(
    "What information can you provide about this product?"
  );
  const [testQuestion, setTestQuestion] = useState<string>(
    "How does this work?"
  );
  const [loading, setLoading] = useState<boolean>(false);
  const [documentLoading, setDocumentLoading] = useState<boolean>(false);
  const [testResult, setTestResult] = useState<string>("");
  const [documentResult, setDocumentResult] = useState<string>("");
  const [deviceInfo, setDeviceInfo] = useState<any>(null);

  useEffect(() => {
    // Get device info on load
    const fetchDeviceInfo = async () => {
      try {
        const info = await LocalLLMService.getDeviceInfo();
        setDeviceInfo(info);
      } catch (error) {
        console.error("Error fetching device info:", error);
      }
    };

    fetchDeviceInfo();
  }, []);

  const handleTestAutoReply = async () => {
    if (!phoneNumber || !message) {
      Alert.alert(
        "Missing Information",
        "Please enter a phone number and message."
      );
      return;
    }

    setLoading(true);
    try {
      const result = await LocalLLMService.testAutoReply(phoneNumber, message);

      if (result.success) {
        setTestResult(`Success! Response: ${result.response}`);
      } else {
        setTestResult(`Error: ${result.error}`);
      }
    } catch (error) {
      setTestResult(`Exception: ${error}`);
    } finally {
      setLoading(false);
    }
  };

  const handleTestDocuments = async () => {
    if (!testQuestion) {
      Alert.alert("Missing Question", "Please enter a test question.");
      return;
    }

    setDocumentLoading(true);
    try {
      const result = await LocalLLMService.testLlmWithDocuments(testQuestion);

      if (result.success) {
        setDocumentResult(
          `Model loaded: ${result.modelLoaded ? "Yes" : "No"}\n` +
            `Documents loaded: ${result.documentCount || 0}\n\n` +
            `Response: ${result.response}`
        );
      } else {
        setDocumentResult(`Error: ${result.error}`);
      }
    } catch (error) {
      setDocumentResult(`Exception: ${error}`);
    } finally {
      setDocumentLoading(false);
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <Text style={styles.title}>Test Auto-Reply Functionality</Text>

        {deviceInfo && (
          <View style={styles.infoBox}>
            <Text style={styles.infoTitle}>Device Information</Text>
            <Text style={styles.infoText}>Model: {deviceInfo.model}</Text>
            <Text style={styles.infoText}>
              Total RAM: {Math.round(deviceInfo.totalMemoryMB)} MB
            </Text>
            <Text style={styles.infoText}>
              Free RAM: {Math.round(deviceInfo.freeMemoryMB)} MB
            </Text>
            <Text style={styles.infoText}>CPU cores: {deviceInfo.cores}</Text>
          </View>
        )}

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Test SMS Auto-Reply</Text>

          <Text style={styles.label}>Phone Number</Text>
          <TextInput
            style={styles.input}
            value={phoneNumber}
            onChangeText={setPhoneNumber}
            placeholder="Enter phone number"
            keyboardType="phone-pad"
          />

          <Text style={styles.label}>Test Message</Text>
          <TextInput
            style={[styles.input, styles.textArea]}
            value={message}
            onChangeText={setMessage}
            placeholder="Enter a test message"
            multiline
          />

          <TouchableOpacity
            style={styles.button}
            onPress={handleTestAutoReply}
            disabled={loading}
          >
            {loading ? (
              <ActivityIndicator color="#FFFFFF" />
            ) : (
              <Text style={styles.buttonText}>Test Auto-Reply</Text>
            )}
          </TouchableOpacity>

          {testResult ? (
            <View style={styles.resultBox}>
              <Text style={styles.resultTitle}>Result:</Text>
              <Text style={styles.resultText}>{testResult}</Text>
            </View>
          ) : null}
        </View>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Test Document Processing</Text>

          <Text style={styles.label}>Test Question</Text>
          <TextInput
            style={[styles.input, styles.textArea]}
            value={testQuestion}
            onChangeText={setTestQuestion}
            placeholder="Enter a test question"
            multiline
          />

          <TouchableOpacity
            style={styles.button}
            onPress={handleTestDocuments}
            disabled={documentLoading}
          >
            {documentLoading ? (
              <ActivityIndicator color="#FFFFFF" />
            ) : (
              <Text style={styles.buttonText}>Test Documents</Text>
            )}
          </TouchableOpacity>

          {documentResult ? (
            <View style={styles.resultBox}>
              <Text style={styles.resultTitle}>Result:</Text>
              <Text style={styles.resultText}>{documentResult}</Text>
            </View>
          ) : null}
        </View>
      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#F5F5F5",
  },
  scrollContent: {
    padding: 16,
  },
  title: {
    fontSize: 24,
    fontWeight: "bold",
    marginBottom: 16,
    color: "#333",
  },
  section: {
    backgroundColor: "#FFF",
    borderRadius: 8,
    padding: 16,
    marginBottom: 16,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: "bold",
    marginBottom: 16,
    color: "#333",
  },
  label: {
    fontSize: 16,
    marginBottom: 8,
    color: "#555",
  },
  input: {
    backgroundColor: "#F9F9F9",
    borderWidth: 1,
    borderColor: "#DDD",
    borderRadius: 6,
    padding: 12,
    marginBottom: 16,
    fontSize: 16,
  },
  textArea: {
    minHeight: 80,
    textAlignVertical: "top",
  },
  button: {
    backgroundColor: "#007BFF",
    borderRadius: 6,
    padding: 16,
    alignItems: "center",
    justifyContent: "center",
  },
  buttonText: {
    color: "#FFF",
    fontSize: 16,
    fontWeight: "600",
  },
  resultBox: {
    marginTop: 16,
    padding: 12,
    backgroundColor: "#F0F7FF",
    borderRadius: 6,
    borderWidth: 1,
    borderColor: "#D0E3FF",
  },
  resultTitle: {
    fontSize: 16,
    fontWeight: "600",
    marginBottom: 8,
    color: "#0056B3",
  },
  resultText: {
    fontSize: 14,
    color: "#333",
  },
  infoBox: {
    backgroundColor: "#FFF",
    borderRadius: 8,
    padding: 16,
    marginBottom: 16,
    borderWidth: 1,
    borderColor: "#DDD",
  },
  infoTitle: {
    fontSize: 16,
    fontWeight: "bold",
    marginBottom: 8,
    color: "#555",
  },
  infoText: {
    fontSize: 14,
    color: "#666",
    marginBottom: 4,
  },
});

export default TestAutoReplyScreen;
