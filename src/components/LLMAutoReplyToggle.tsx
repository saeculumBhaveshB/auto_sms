import React, { useState, useEffect } from "react";
import {
  View,
  Text,
  Switch,
  StyleSheet,
  Alert,
  TouchableOpacity,
} from "react-native";
import AutoReplyService from "../services/AutoReplyService";
import LocalLLMService from "../services/LocalLLMService";

const LLMAutoReplyToggle: React.FC = () => {
  const [isEnabled, setIsEnabled] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [isModelLoaded, setIsModelLoaded] = useState(false);
  const [documentsCount, setDocumentsCount] = useState(0);

  // Load initial state
  useEffect(() => {
    const loadState = async () => {
      try {
        setIsLoading(true);

        // Check if the feature is enabled
        const enabled = await AutoReplyService.isLLMAutoReplyEnabled();
        setIsEnabled(enabled);

        // Check if the model is loaded
        const modelLoaded = await LocalLLMService.isModelLoaded();
        setIsModelLoaded(modelLoaded);

        // Get document count
        const documents = await LocalLLMService.listDocuments();
        setDocumentsCount(documents.length);
      } catch (error) {
        console.error("Error loading LLM auto-reply state:", error);
      } finally {
        setIsLoading(false);
      }
    };

    loadState();
  }, []);

  // Handle toggle change
  const handleToggle = async (value: boolean) => {
    try {
      if (value) {
        // Verify requirements before enabling
        if (!isModelLoaded) {
          const selectedModel = await LocalLLMService.getSelectedModel();

          if (!selectedModel) {
            Alert.alert(
              "Model Required",
              "Please select and load a LLM model first in the LLM Setup screen."
            );
            return;
          }

          Alert.alert(
            "Loading Model",
            "Loading the LLM model for auto-replies. This may take a moment.",
            [
              {
                text: "Continue",
                onPress: async () => {
                  try {
                    const loaded = await LocalLLMService.loadModel(
                      selectedModel
                    );
                    if (!loaded) {
                      Alert.alert(
                        "Error",
                        "Failed to load the LLM model. Please try again."
                      );
                      return;
                    }
                    setIsModelLoaded(true);
                    continueEnabling();
                  } catch (error) {
                    console.error("Error loading model:", error);
                    Alert.alert(
                      "Error",
                      "Failed to load the LLM model. Please try again."
                    );
                  }
                },
              },
              {
                text: "Cancel",
                style: "cancel",
              },
            ]
          );
          return;
        }

        // Check documents
        if (documentsCount === 0) {
          Alert.alert(
            "Documents Required",
            "Please upload at least one document in the LLM Setup screen for the auto-reply feature to use."
          );
          return;
        }

        continueEnabling();
      } else {
        // Simply disable
        const result = await AutoReplyService.setLLMAutoReplyEnabled(false);
        setIsEnabled(false);
      }
    } catch (error) {
      console.error("Error toggling LLM auto-reply:", error);
      Alert.alert(
        "Error",
        "Failed to toggle LLM auto-reply. Please try again."
      );
    }
  };

  // Continue enabling after validation
  const continueEnabling = async () => {
    try {
      // Ensure model is loaded
      if (!isModelLoaded) {
        const selectedModel = await LocalLLMService.getSelectedModel();
        if (selectedModel) {
          await LocalLLMService.loadModel(selectedModel);
        } else {
          Alert.alert(
            "Model Required",
            "Please select a model in the LLM Setup screen first."
          );
          return;
        }
      }

      // Check if documents exist
      if (documentsCount === 0) {
        // Alert user that they need to upload documents
        Alert.alert(
          "Documents Required",
          "Please upload at least one document in the LLM Setup screen first.",
          [
            {
              text: "OK",
              onPress: handleDocumentSetup,
            },
          ]
        );
        return;
      }

      // Enable the feature
      const result = await AutoReplyService.setLLMAutoReplyEnabled(true);
      if (result) {
        setIsEnabled(true);
        Alert.alert(
          "LLM Auto-Reply Enabled",
          "The LLM auto-reply feature is now active. It will respond to messages from missed call numbers using your documents for context."
        );
      } else {
        Alert.alert(
          "Error",
          "Failed to enable LLM auto-reply. Check that you have all required components (model loaded, documents uploaded)."
        );
      }
    } catch (error) {
      console.error("Error enabling LLM auto-reply:", error);
      Alert.alert(
        "Error",
        "Failed to enable LLM auto-reply. Please try again."
      );
    }
  };

  // Handle document setup navigation
  const handleDocumentSetup = () => {
    // Navigation to document setup screen would be handled here
    // For now, just show an alert
    Alert.alert(
      "Document Setup",
      "Please go to the LLM Setup screen to upload and manage documents."
    );
  };

  // Render loading state
  if (isLoading) {
    return (
      <View style={styles.container}>
        <Text style={styles.loadingText}>Loading...</Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>Document-Based LLM Auto-Reply</Text>
        <Switch
          trackColor={{ false: "#767577", true: "#81b0ff" }}
          thumbColor={isEnabled ? "#2196f3" : "#f4f3f4"}
          onValueChange={handleToggle}
          value={isEnabled}
        />
      </View>

      <Text style={styles.description}>
        When enabled, replies to missed call messages will be generated using a
        local LLM based on your uploaded documents.
      </Text>

      <View style={styles.statusContainer}>
        <Text style={styles.statusLabel}>Model Status:</Text>
        <Text
          style={[
            styles.statusValue,
            { color: isModelLoaded ? "#4caf50" : "#f44336" },
          ]}
        >
          {isModelLoaded ? "Loaded" : "Not Loaded"}
        </Text>
      </View>

      <View style={styles.statusContainer}>
        <Text style={styles.statusLabel}>Documents:</Text>
        <Text
          style={[
            styles.statusValue,
            { color: documentsCount > 0 ? "#4caf50" : "#f44336" },
          ]}
        >
          {documentsCount} available
        </Text>
      </View>

      <TouchableOpacity
        style={styles.setupButton}
        onPress={handleDocumentSetup}
      >
        <Text style={styles.setupButtonText}>Manage Documents</Text>
      </TouchableOpacity>
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
  },
  header: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: 12,
  },
  title: {
    fontSize: 18,
    fontWeight: "600",
    color: "#333",
  },
  description: {
    fontSize: 14,
    color: "#666",
    marginBottom: 16,
    lineHeight: 20,
  },
  loadingText: {
    fontSize: 16,
    color: "#333",
    textAlign: "center",
    padding: 20,
  },
  statusContainer: {
    flexDirection: "row",
    alignItems: "center",
    marginBottom: 8,
  },
  statusLabel: {
    fontSize: 14,
    color: "#333",
    width: 100,
  },
  statusValue: {
    fontSize: 14,
    fontWeight: "600",
  },
  setupButton: {
    backgroundColor: "#2196f3",
    paddingVertical: 8,
    paddingHorizontal: 12,
    borderRadius: 4,
    alignSelf: "flex-start",
    marginTop: 8,
  },
  setupButtonText: {
    color: "white",
    fontWeight: "600",
    fontSize: 14,
  },
});

export default LLMAutoReplyToggle;
