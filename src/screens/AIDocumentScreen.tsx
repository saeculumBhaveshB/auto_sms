import React, { useState, useEffect } from "react";
import {
  View,
  Text,
  StyleSheet,
  TextInput,
  TouchableOpacity,
  ScrollView,
  ActivityIndicator,
  Alert,
  KeyboardAvoidingView,
  Platform,
} from "react-native";
import { AIService, DocParserService } from "../services";

const AIDocumentScreen: React.FC = () => {
  const [documentText, setDocumentText] = useState<string>("");
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [isTraining, setIsTraining] = useState<boolean>(false);
  const [apiKey, setApiKey] = useState<string>("");

  /**
   * Load saved document text and API key on mount
   */
  useEffect(() => {
    const loadData = async () => {
      try {
        const savedText = await AIService.getDocumentText();
        if (savedText) {
          setDocumentText(savedText);
        }

        const savedApiKey = await AIService.getApiKey();
        if (savedApiKey) {
          setApiKey(savedApiKey);
        }
      } catch (error) {
        console.error("Error loading saved data:", error);
      }
    };

    loadData();
  }, []);

  /**
   * Handle document picker
   */
  const handlePickDocument = async () => {
    try {
      setIsLoading(true);

      // Pick document
      const document = await AIService.pickDocument();
      if (!document) {
        setIsLoading(false);
        return;
      }

      // Show a message when processing PDF files
      if (document.type && document.type.includes("pdf")) {
        Alert.alert(
          "Processing PDF",
          "PDF extraction is in progress. The results may need review and editing once complete."
        );
      }

      // Process document
      const text = await DocParserService.parseDocument(document.uri);
      setDocumentText(text);

      // Save document text
      await AIService.saveDocumentText(text);

      setIsLoading(false);

      // Show success message with reminder to review if it's a PDF
      if (document.type && document.type.includes("pdf")) {
        Alert.alert(
          "PDF Processed",
          "Please review and correct any mistakes in the extracted text before training the AI."
        );
      }
    } catch (error: any) {
      console.error("Error picking document:", error);

      // Show more specific error messages for different error types
      if (error.message && error.message.includes("PDDocument")) {
        Alert.alert(
          "PDF Error",
          "There was an issue processing this PDF file. The file might be corrupted, password-protected, or uses an unsupported format."
        );
      } else if (error.message && error.message.includes("java/awt/Point")) {
        Alert.alert(
          "PDF Format Error",
          "This PDF format is not compatible. Please try another PDF file or manually enter the text."
        );
      } else {
        Alert.alert("Error", "Failed to process document. Please try again.");
      }

      setIsLoading(false);
    }
  };

  /**
   * Handle AI training
   */
  const handleTrainAI = async () => {
    if (!documentText.trim()) {
      Alert.alert("Error", "Please upload or enter document text first.");
      return;
    }

    if (!apiKey.trim()) {
      Alert.alert("Error", "Please enter your OpenAI API key.");
      return;
    }

    try {
      setIsTraining(true);

      // Save API key
      await AIService.saveApiKey(apiKey);

      // Train AI
      const success = await AIService.trainAI(documentText);

      setIsTraining(false);

      if (success) {
        Alert.alert(
          "Success",
          "AI has been successfully trained with your document."
        );
      } else {
        Alert.alert(
          "Error",
          "Failed to train AI. Please check your API key and try again."
        );
      }
    } catch (error) {
      console.error("Error training AI:", error);
      Alert.alert("Error", "Failed to train AI. Please try again.");
      setIsTraining(false);
    }
  };

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === "ios" ? "padding" : undefined}
      keyboardVerticalOffset={100}
    >
      <View style={styles.header}>
        <Text style={styles.title}>Document Text Editor</Text>
      </View>

      <ScrollView
        style={styles.content}
        contentContainerStyle={styles.contentContainer}
      >
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>1. Upload Document</Text>
          <TouchableOpacity
            style={styles.button}
            onPress={handlePickDocument}
            disabled={isLoading}
          >
            <Text style={styles.buttonText}>Upload Document (DOC/PDF)</Text>
          </TouchableOpacity>

          {isLoading && (
            <View style={styles.loadingContainer}>
              <ActivityIndicator size="small" color="#2196f3" />
              <Text style={styles.loadingText}>Processing document...</Text>
            </View>
          )}
        </View>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>2. Edit Document Text</Text>
          <Text style={styles.description}>
            Review and edit the text below. For PDF files, you may need to
            correct formatting and extraction errors:
          </Text>
          <TextInput
            style={[
              styles.textInput,
              documentText.length > 1000 ? { height: 300 } : {},
            ]}
            multiline
            value={documentText}
            onChangeText={setDocumentText}
            placeholder="Document text will appear here after upload. You can also type or paste text directly."
            textAlignVertical="top"
          />
          {documentText.length > 0 && (
            <View style={styles.textInfoContainer}>
              <Text style={styles.textInfoText}>
                {documentText.length} characters |{" "}
                {documentText.split(/\s+/).length} words
              </Text>
              <TouchableOpacity
                onPress={() => setDocumentText("")}
                style={styles.clearButton}
              >
                <Text style={styles.clearButtonText}>Clear</Text>
              </TouchableOpacity>
            </View>
          )}
        </View>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>3. OpenAI API Key</Text>
          <Text style={styles.description}>
            Enter your OpenAI API key to enable AI responses:
          </Text>
          <TextInput
            style={styles.apiKeyInput}
            value={apiKey}
            onChangeText={setApiKey}
            placeholder="Enter your OpenAI API key"
            secureTextEntry
          />
        </View>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>4. Train AI</Text>
          <TouchableOpacity
            style={[styles.button, styles.trainButton]}
            onPress={handleTrainAI}
            disabled={isTraining || !documentText.trim()}
          >
            <Text style={styles.buttonText}>Read AI</Text>
          </TouchableOpacity>

          {isTraining && (
            <View style={styles.loadingContainer}>
              <ActivityIndicator size="small" color="#4caf50" />
              <Text style={styles.loadingText}>Training AI...</Text>
            </View>
          )}
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#f5f5f5",
  },
  header: {
    padding: 16,
    backgroundColor: "#2196f3",
  },
  title: {
    fontSize: 20,
    fontWeight: "bold",
    color: "#fff",
  },
  content: {
    flex: 1,
  },
  contentContainer: {
    padding: 16,
  },
  section: {
    marginBottom: 24,
    backgroundColor: "#fff",
    borderRadius: 8,
    padding: 16,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.2,
    shadowRadius: 1.41,
    elevation: 2,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: "bold",
    marginBottom: 12,
    color: "#333",
  },
  description: {
    fontSize: 14,
    color: "#666",
    marginBottom: 12,
  },
  button: {
    backgroundColor: "#2196f3",
    paddingVertical: 12,
    paddingHorizontal: 16,
    borderRadius: 4,
    alignItems: "center",
  },
  buttonText: {
    color: "#fff",
    fontWeight: "bold",
    fontSize: 16,
  },
  trainButton: {
    backgroundColor: "#4caf50",
  },
  textInput: {
    backgroundColor: "#f9f9f9",
    borderWidth: 1,
    borderColor: "#ddd",
    borderRadius: 4,
    padding: 12,
    height: 200,
    fontSize: 16,
    color: "#333",
  },
  apiKeyInput: {
    backgroundColor: "#f9f9f9",
    borderWidth: 1,
    borderColor: "#ddd",
    borderRadius: 4,
    padding: 12,
    fontSize: 16,
    color: "#333",
  },
  loadingContainer: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    marginTop: 12,
  },
  loadingText: {
    marginLeft: 8,
    fontSize: 14,
    color: "#666",
  },
  textInfoContainer: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    marginTop: 8,
  },
  textInfoText: {
    fontSize: 14,
    color: "#666",
  },
  clearButton: {
    backgroundColor: "#2196f3",
    padding: 8,
    borderRadius: 4,
  },
  clearButtonText: {
    color: "#fff",
    fontWeight: "bold",
    fontSize: 16,
  },
});

export default AIDocumentScreen;
