import React, { useState, useEffect } from "react";
import {
  View,
  Text,
  Switch,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  Alert,
  ActivityIndicator,
} from "react-native";
import { useNavigation } from "@react-navigation/native";
import { AutoReplyService, LocalLLMService } from "../services";

const AutoReplySettingsScreen: React.FC = () => {
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [autoReplyEnabled, setAutoReplyEnabled] = useState<boolean>(false);
  const [llmAutoReplyEnabled, setLlmAutoReplyEnabled] =
    useState<boolean>(false);
  const [useAI, setUseAI] = useState<boolean>(false);
  const [documentCount, setDocumentCount] = useState<number>(0);
  const [isModelLoaded, setIsModelLoaded] = useState<boolean>(false);
  const navigation = useNavigation();

  useEffect(() => {
    loadSettings();
    checkDocuments();
    checkModel();
  }, []);

  const loadSettings = async () => {
    try {
      setIsLoading(true);
      const autoReplyStatus = await AutoReplyService.isAutoReplyEnabled();
      const llmStatus = await AutoReplyService.isLLMAutoReplyEnabled();
      const aiStatus = await AutoReplyService.isAIEnabled();

      setAutoReplyEnabled(autoReplyStatus);
      setLlmAutoReplyEnabled(llmStatus);
      setUseAI(aiStatus);

      setIsLoading(false);
    } catch (error) {
      console.error("Error loading auto-reply settings:", error);
      setIsLoading(false);
    }
  };

  const checkDocuments = async () => {
    try {
      const documents = await LocalLLMService.listDocuments();
      setDocumentCount(documents.length);
    } catch (error) {
      console.error("Error checking documents:", error);
    }
  };

  const checkModel = async () => {
    try {
      const status = await LocalLLMService.isModelLoaded();
      setIsModelLoaded(status);
    } catch (error) {
      console.error("Error checking model status:", error);
    }
  };

  const handleAutoReplyToggle = async (value: boolean) => {
    try {
      setAutoReplyEnabled(value);
      await AutoReplyService.setAutoReplyEnabled(value);
    } catch (error) {
      console.error("Error toggling auto-reply:", error);
      setAutoReplyEnabled(!value); // Revert on error
      Alert.alert(
        "Error",
        "Failed to toggle auto-reply. Please check permissions."
      );
    }
  };

  const handleLlmAutoReplyToggle = async (value: boolean) => {
    try {
      if (value && documentCount === 0) {
        Alert.alert(
          "No Documents",
          "You need to upload at least one document for LLM auto-reply to work. Would you like to go to LLM Setup?",
          [
            {
              text: "Cancel",
              style: "cancel",
            },
            {
              text: "Go to Setup",
              onPress: () => {
                navigation.navigate("LocalLLMSetup" as never);
              },
            },
          ]
        );
        return;
      }

      if (value && !isModelLoaded) {
        Alert.alert(
          "Model Not Loaded",
          "The LLM model is not loaded. Would you like to go to LLM Setup to load it?",
          [
            {
              text: "Cancel",
              style: "cancel",
            },
            {
              text: "Go to Setup",
              onPress: () => {
                navigation.navigate("LocalLLMSetup" as never);
              },
            },
          ]
        );
        return;
      }

      setLlmAutoReplyEnabled(value);
      await AutoReplyService.setLLMAutoReplyEnabled(value);

      // If enabling LLM auto-reply, disable AI auto-reply
      if (value && useAI) {
        setUseAI(false);
        await AutoReplyService.setAIEnabled(false);
      }
    } catch (error) {
      console.error("Error toggling LLM auto-reply:", error);
      setLlmAutoReplyEnabled(!value); // Revert on error
      Alert.alert("Error", "Failed to toggle LLM auto-reply.");
    }
  };

  const handleAIToggle = async (value: boolean) => {
    try {
      setUseAI(value);
      await AutoReplyService.setAIEnabled(value);

      // If enabling AI auto-reply, disable LLM auto-reply
      if (value && llmAutoReplyEnabled) {
        setLlmAutoReplyEnabled(false);
        await AutoReplyService.setLLMAutoReplyEnabled(false);
      }
    } catch (error) {
      console.error("Error toggling AI usage:", error);
      setUseAI(!value); // Revert on error
      Alert.alert("Error", "Failed to toggle AI usage.");
    }
  };

  const runDiagnostics = async () => {
    try {
      setIsLoading(true);
      const results = await AutoReplyService.runLLMDiagnostics();
      setIsLoading(false);

      Alert.alert(
        "Diagnostics Results",
        `LLM Module Available: ${results.llmModuleAvailable ? "Yes" : "No"}\n` +
          `Model Loaded: ${results.modelLoaded ? "Yes" : "No"}\n` +
          `Documents Count: ${results.documentCount}\n` +
          `Simple Auto-Reply: ${
            results.simpleAutoReplyEnabled ? "Enabled" : "Disabled"
          }\n` +
          `LLM Auto-Reply: ${
            results.llmAutoReplyEnabled ? "Enabled" : "Disabled"
          }`
      );
    } catch (error) {
      setIsLoading(false);
      console.error("Error running diagnostics:", error);
      Alert.alert("Error", "Failed to run diagnostics.");
    }
  };

  return (
    <ScrollView style={styles.container}>
      {isLoading && (
        <View style={styles.loadingContainer}>
          <ActivityIndicator size="large" color="#2196f3" />
        </View>
      )}

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Automatic SMS Reply</Text>
        <Text style={styles.description}>
          Enable this feature to automatically reply to SMS messages received
          after missed calls.
        </Text>

        <View style={styles.switchContainer}>
          <Text style={styles.switchLabel}>Simple Auto-Reply</Text>
          <Switch
            value={autoReplyEnabled}
            onValueChange={handleAutoReplyToggle}
            trackColor={{ false: "#767577", true: "#2196f3" }}
            thumbColor={autoReplyEnabled ? "#ffffff" : "#f4f3f4"}
            disabled={isLoading}
          />
        </View>

        {autoReplyEnabled && (
          <Text style={styles.infoText}>
            When enabled, the app will send "Yes I am" in response to SMS
            messages received after a missed call.
          </Text>
        )}
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>LLM-powered Auto-Reply</Text>
        <Text style={styles.description}>
          Enable to use a local LLM (Large Language Model) to generate
          context-aware responses based on your documents.
        </Text>

        <View style={styles.switchContainer}>
          <Text style={styles.switchLabel}>Use Local LLM</Text>
          <Switch
            value={llmAutoReplyEnabled}
            onValueChange={handleLlmAutoReplyToggle}
            trackColor={{ false: "#767577", true: "#4caf50" }}
            thumbColor={llmAutoReplyEnabled ? "#ffffff" : "#f4f3f4"}
            disabled={isLoading}
          />
        </View>

        {llmAutoReplyEnabled && (
          <Text style={styles.infoText}>
            When enabled, the app will generate responses using an on-device LLM
            with access to your documents.
          </Text>
        )}

        <View style={styles.statusContainer}>
          <Text style={styles.statusLabel}>Documents:</Text>
          <Text
            style={[
              styles.statusValue,
              documentCount === 0 ? styles.warning : {},
            ]}
          >
            {documentCount} {documentCount === 1 ? "document" : "documents"}{" "}
            available
          </Text>
        </View>

        <View style={styles.statusContainer}>
          <Text style={styles.statusLabel}>Model Status:</Text>
          <Text
            style={[styles.statusValue, !isModelLoaded ? styles.warning : {}]}
          >
            {isModelLoaded ? "Loaded" : "Not Loaded"}
          </Text>
        </View>

        <TouchableOpacity
          style={styles.button}
          onPress={() => navigation.navigate("LocalLLMSetup" as never)}
        >
          <Text style={styles.buttonText}>LLM Setup</Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={styles.diagnosticButton}
          onPress={runDiagnostics}
        >
          <Text style={styles.buttonText}>Run Diagnostics</Text>
        </TouchableOpacity>
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>API-based AI Auto-Reply</Text>
        <Text style={styles.description}>
          Enable to use OpenAI's API for generating responses (requires API key
          and internet connection).
        </Text>

        <View style={styles.switchContainer}>
          <Text style={styles.switchLabel}>Use OpenAI API</Text>
          <Switch
            value={useAI}
            onValueChange={handleAIToggle}
            trackColor={{ false: "#767577", true: "#9c27b0" }}
            thumbColor={useAI ? "#ffffff" : "#f4f3f4"}
            disabled={isLoading}
          />
        </View>

        {useAI && (
          <Text style={styles.infoText}>
            When enabled, the app will send AI-generated responses to SMS
            messages. Requires API key setup.
          </Text>
        )}

        <TouchableOpacity
          style={[styles.button, { backgroundColor: "#9c27b0" }]}
          onPress={() => navigation.navigate("AISettings" as never)}
        >
          <Text style={styles.buttonText}>API Settings</Text>
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
  loadingContainer: {
    position: "absolute",
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    justifyContent: "center",
    alignItems: "center",
    backgroundColor: "rgba(255, 255, 255, 0.7)",
    zIndex: 1,
  },
  section: {
    backgroundColor: "white",
    margin: 16,
    padding: 16,
    borderRadius: 8,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: "bold",
    marginBottom: 8,
    color: "#333",
  },
  description: {
    fontSize: 14,
    color: "#666",
    marginBottom: 16,
    lineHeight: 20,
  },
  switchContainer: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: 12,
  },
  switchLabel: {
    fontSize: 16,
    fontWeight: "500",
    color: "#333",
    flex: 1,
  },
  infoText: {
    fontSize: 13,
    color: "#666",
    fontStyle: "italic",
    marginBottom: 8,
  },
  statusContainer: {
    flexDirection: "row",
    marginBottom: 8,
    alignItems: "center",
  },
  statusLabel: {
    fontSize: 14,
    fontWeight: "500",
    color: "#333",
    marginRight: 8,
  },
  statusValue: {
    fontSize: 14,
    color: "#4caf50",
  },
  warning: {
    color: "#f44336",
  },
  button: {
    backgroundColor: "#2196f3",
    padding: 12,
    borderRadius: 4,
    alignItems: "center",
    marginTop: 12,
  },
  diagnosticButton: {
    backgroundColor: "#ff9800",
    padding: 12,
    borderRadius: 4,
    alignItems: "center",
    marginTop: 12,
  },
  buttonText: {
    color: "white",
    fontWeight: "bold",
  },
});

export default AutoReplySettingsScreen;
