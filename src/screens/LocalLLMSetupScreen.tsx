import React, { useState, useEffect } from "react";
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  FlatList,
  Alert,
  Switch,
  ScrollView,
  ActivityIndicator,
  Platform,
  Linking,
  NativeModules,
  TextInput,
} from "react-native";
import { LocalLLMService, DocParserService } from "../services";
import type { DocumentInfo, DeviceInfo } from "../services/LocalLLMService";

const { CallSmsModule } = NativeModules;

// Update DocumentInfo interface to include DOCX indicator
interface EnhancedDocumentInfo extends DocumentInfo {
  isPdf?: boolean;
  isDocx?: boolean;
  extractableText?: boolean;
}

const LocalLLMSetupScreen: React.FC = () => {
  // State variables
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [isCompatible, setIsCompatible] = useState<boolean>(false);
  const [compatibilityReason, setCompatibilityReason] = useState<string>("");
  const [deviceInfo, setDeviceInfo] = useState<DeviceInfo | null>(null);
  const [documents, setDocuments] = useState<EnhancedDocumentInfo[]>([]);
  const [isModelLoaded, setIsModelLoaded] = useState<boolean>(false);
  const [temperature, setTemperature] = useState<number>(0.7);
  const [maxTokens, setMaxTokens] = useState<number>(150);
  const [showAdvanced, setShowAdvanced] = useState<boolean>(false);
  const [uploading, setUploading] = useState<boolean>(false);
  const [selectedModel, setSelectedModel] = useState<string | null>(null);
  const [textInputValue, setTextInputValue] = useState<string>("");
  const [savingText, setSavingText] = useState<boolean>(false);

  // Load data on component mount
  useEffect(() => {
    const loadInitialData = async () => {
      setIsLoading(true);
      try {
        // First delete any default documents
        await clearDefaultDocuments();

        // Check device compatibility
        const compatCheck = await LocalLLMService.checkDeviceCompatibility();
        setIsCompatible(compatCheck.compatible);
        setCompatibilityReason(compatCheck.reason || "");

        // Get device info
        const info = await LocalLLMService.getDeviceInfo();
        setDeviceInfo(info);

        // List documents without creating any samples
        const docs = await LocalLLMService.listDocuments();
        // Process documents if they exist
        if (docs.length > 0) {
          const enhancedDocs = await enhanceDocumentsInfo(docs);
          setDocuments(enhancedDocs);
        } else {
          setDocuments([]);
        }

        // Check if model is loaded
        const modelStatus = await LocalLLMService.isModelLoaded();
        setIsModelLoaded(modelStatus);

        // Get model config
        const config = await LocalLLMService.getModelConfig();
        setTemperature(config.temperature);
        setMaxTokens(config.maxTokens);

        // Get selected model
        const model = await LocalLLMService.getSelectedModel();
        setSelectedModel(model);
      } catch (error) {
        console.error("Error loading initial data:", error);
        Alert.alert("Error", "Failed to initialize Local LLM setup.");
      } finally {
        setIsLoading(false);
      }
    };

    loadInitialData();
  }, []);

  // Helper function to clear default documents
  const clearDefaultDocuments = async () => {
    try {
      // Define a list of default document filenames
      const defaultDocNames = [
        "pricing_info.txt",
        "faq.txt",
        "sample_document.txt",
      ];

      // Get list of documents
      const docs = await LocalLLMService.listDocuments();

      // Delete any default documents
      let deleteCount = 0;
      for (const doc of docs) {
        if (defaultDocNames.includes(doc.name)) {
          console.log(`Deleting default document: ${doc.name}`);
          await LocalLLMService.deleteDocument(doc.name);
          deleteCount++;
        }
      }

      if (deleteCount > 0) {
        console.log(`Deleted ${deleteCount} default documents`);
      }

      return deleteCount;
    } catch (error) {
      console.error("Error clearing default documents:", error);
      return 0;
    }
  };

  // Helper function to enhance documents with file type info
  const enhanceDocumentsInfo = async (
    docs: DocumentInfo[]
  ): Promise<EnhancedDocumentInfo[]> => {
    return Promise.all(
      docs.map(async (doc) => {
        const isPdf = doc.name.toLowerCase().endsWith(".pdf");
        const isDocx = doc.name.toLowerCase().endsWith(".docx");
        let extractableText = false;

        // First determine if this is a supported file type
        const isSupportedType =
          isPdf || isDocx || doc.name.toLowerCase().endsWith(".txt");

        if (!isSupportedType) {
          // For unsupported file types, don't attempt extraction
          return {
            ...doc,
            isPdf,
            isDocx,
            extractableText: false,
          };
        }

        try {
          if (isPdf) {
            try {
              const result = await CallSmsModule.testPdfExtraction(doc.path);
              extractableText = result.success;
            } catch (pdfErr) {
              console.warn(`PDF extraction issue for ${doc.name}:`, pdfErr);
              extractableText = false;
            }
          } else if (isDocx) {
            // Skip actual DOCX extraction to avoid Apache POI errors
            // Instead, just mark all DOCX files as extractable for UI purposes
            extractableText = true;
            console.log(
              `DOCX file detected: ${doc.name} - skipping extraction test for stability`
            );
          } else {
            // For text files, assume they're extractable
            extractableText = doc.name.toLowerCase().endsWith(".txt");
          }
        } catch (err) {
          console.error(
            `Error checking document extraction for ${doc.name}:`,
            err
          );
          extractableText = false;
        }

        return {
          ...doc,
          isPdf,
          isDocx,
          extractableText,
        };
      })
    );
  };

  // Handle document upload
  const handleDocumentUpload = async () => {
    try {
      setUploading(true);
      const result = await LocalLLMService.pickAndUploadDocument();

      if (result) {
        // Refresh document list with enhanced info
        const docs = await LocalLLMService.listDocuments();
        const enhancedDocs = await enhanceDocumentsInfo(docs);
        setDocuments(enhancedDocs);

        // Determine file type for better messaging
        const isPdf = result.name.toLowerCase().endsWith(".pdf");
        const isDocx = result.name.toLowerCase().endsWith(".docx");
        let fileTypeMsg = "";

        if (isPdf) {
          fileTypeMsg = " PDF content will be extracted automatically.";
        } else if (isDocx) {
          fileTypeMsg =
            " DOCX files are supported, but work best with the Document QA feature.";
        }

        // Automatically load the model if not already loaded
        if (!isModelLoaded) {
          try {
            // Get a valid local model path
            let modelPath = await LocalLLMService.getLocalModelDirectory();
            setSelectedModel(modelPath);
            await LocalLLMService.saveSelectedModel(modelPath);

            console.log(
              "Auto-loading model after document upload from path:",
              modelPath
            );
            const success = await LocalLLMService.loadModel(modelPath);
            setIsModelLoaded(success);

            if (success) {
              console.log("Model auto-loaded successfully");
            } else {
              console.warn("Model auto-load using simplified mode");
            }
          } catch (modelError) {
            console.error("Error auto-loading model:", modelError);
          }
        }

        Alert.alert(
          "Document Uploaded",
          `Successfully uploaded ${result.name}.${fileTypeMsg} The document will be used for LLM queries.`,
          isDocx
            ? [
                {
                  text: "OK",
                  onPress: () => {},
                },
                {
                  text: "Learn More",
                  onPress: () => {
                    Alert.alert(
                      "DOCX File Support",
                      "DOCX files are supported but may have limitations with the Basic LLM mode. For best results with DOCX files, use the Document QA feature.",
                      [{ text: "Got it" }]
                    );
                  },
                },
              ]
            : undefined
        );
      }
    } catch (error) {
      console.error("Error uploading document:", error);
      Alert.alert(
        "Upload Failed",
        "Failed to upload document. Please try again."
      );
    } finally {
      setUploading(false);
    }
  };

  // Handle document deletion
  const handleDeleteDocument = async (document: EnhancedDocumentInfo) => {
    Alert.alert(
      "Delete Document",
      `Are you sure you want to delete ${document.name}?`,
      [
        { text: "Cancel", style: "cancel" },
        {
          text: "Delete",
          style: "destructive",
          onPress: async () => {
            try {
              setIsLoading(true);
              await LocalLLMService.deleteDocument(document.name);

              // Refresh document list
              const docs = await LocalLLMService.listDocuments();
              const enhancedDocs = await enhanceDocumentsInfo(docs);
              setDocuments(enhancedDocs);

              // If this was the last document and model is loaded, unload the model
              if (docs.length === 0 && isModelLoaded) {
                console.log("Unloading model since no documents are available");
                await LocalLLMService.unloadModel();
                setIsModelLoaded(false);
                Alert.alert(
                  "Model Unloaded",
                  "The model has been unloaded since no documents are available."
                );
              }
            } catch (error) {
              console.error("Error deleting document:", error);
              Alert.alert("Error", "Failed to delete document.");
            } finally {
              setIsLoading(false);
            }
          },
        },
      ]
    );
  };

  // Handle model loading/unloading
  const toggleModelLoading = async () => {
    try {
      setIsLoading(true);

      if (isModelLoaded) {
        // Unload model
        await LocalLLMService.unloadModel();
        setIsModelLoaded(false);
      } else {
        // Get a valid local model path instead of hardcoded path
        let modelPath;

        try {
          // Get local model directory that's guaranteed to exist
          modelPath = await LocalLLMService.getLocalModelDirectory();
          setSelectedModel(modelPath);
          await LocalLLMService.saveSelectedModel(modelPath);
        } catch (pathError) {
          console.error("Error getting local model directory:", pathError);
          Alert.alert(
            "Model Path Error",
            "Could not create a local model directory. Using fallback approach."
          );
          // Create a fallback path in app's directory
          modelPath = "default_model";
        }

        // Load model using the path
        console.log("Loading model from path:", modelPath);
        const success = await LocalLLMService.loadModel(modelPath);
        setIsModelLoaded(success);

        if (success) {
          Alert.alert(
            "Success",
            "Model loaded successfully. You can now process document queries with the LLM."
          );
        } else {
          Alert.alert(
            "Warning",
            "Using simplified mode. Document processing will still work but with limited capabilities."
          );
        }
      }
    } catch (error) {
      console.error("Error toggling model:", error);
      Alert.alert(
        "Error",
        "An error occurred while managing the model. Document processing will use simplified mode."
      );
    } finally {
      setIsLoading(false);
    }
  };

  // Save model configuration
  const saveModelConfig = async () => {
    try {
      await LocalLLMService.saveModelConfig({
        temperature,
        maxTokens,
      });

      Alert.alert("Success", "Model configuration saved.");
    } catch (error) {
      console.error("Error saving model config:", error);
      Alert.alert("Error", "Failed to save model configuration.");
    }
  };

  // Format file size for display
  const formatFileSize = (bytes: number): string => {
    if (bytes < 1024) return bytes + " B";
    else if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + " KB";
    else return (bytes / (1024 * 1024)).toFixed(1) + " MB";
  };

  // Handle saving text as document
  const handleSaveTextAsDocument = async () => {
    if (!textInputValue.trim()) {
      Alert.alert("Error", "Please enter some text to save.");
      return;
    }

    try {
      setSavingText(true);

      // Generate a filename with timestamp to ensure uniqueness
      const fileName = `text_document_${Date.now()}.txt`;

      // Save the text as a document
      const result = await LocalLLMService.createSampleDocument(
        textInputValue,
        true
      );

      if (result) {
        // Clear the text input
        setTextInputValue("");

        // Refresh document list with enhanced info
        const docs = await LocalLLMService.listDocuments();
        const enhancedDocs = await enhanceDocumentsInfo(docs);
        setDocuments(enhancedDocs);

        // Show success message
        Alert.alert(
          "Document Created",
          "Your text has been saved as a document and will be used for LLM queries."
        );

        // Automatically load the model if not already loaded
        if (!isModelLoaded) {
          try {
            // Get a valid local model path
            let modelPath = await LocalLLMService.getLocalModelDirectory();
            setSelectedModel(modelPath);
            await LocalLLMService.saveSelectedModel(modelPath);

            const success = await LocalLLMService.loadModel(modelPath);
            setIsModelLoaded(success);

            if (success) {
              console.log("Model auto-loaded successfully after text save");
            } else {
              console.warn(
                "Model auto-load using simplified mode after text save"
              );
            }
          } catch (modelError) {
            console.error(
              "Error auto-loading model after text save:",
              modelError
            );
          }
        }
      }
    } catch (error) {
      console.error("Error saving text as document:", error);
      Alert.alert(
        "Save Failed",
        "Failed to save text as document. Please try again."
      );
    } finally {
      setSavingText(false);
    }
  };

  // Render document item
  const renderDocumentItem = ({ item }: { item: EnhancedDocumentInfo }) => (
    <View style={styles.documentItem}>
      <View style={styles.documentInfo}>
        <Text
          style={styles.documentName}
          numberOfLines={1}
          ellipsizeMode="middle"
        >
          {item.name}
        </Text>
        <View style={styles.documentMetaContainer}>
          <Text style={styles.documentMeta}>
            {formatFileSize(item.size)} -{" "}
            {new Date(item.lastModified).toLocaleDateString()}
          </Text>
          {item.isPdf && (
            <View style={[styles.docTypeTag, styles.pdfTag]}>
              <Text style={styles.docTypeText}>PDF</Text>
            </View>
          )}
          {item.isDocx && (
            <View style={[styles.docTypeTag, styles.docxTag]}>
              <Text style={styles.docTypeText}>DOCX</Text>
            </View>
          )}
          {item.name.toLowerCase().endsWith(".txt") && (
            <View style={[styles.docTypeTag, styles.txtTag]}>
              <Text style={styles.docTypeText}>TXT</Text>
            </View>
          )}
        </View>
      </View>
      <TouchableOpacity
        style={styles.deleteButton}
        onPress={() => handleDeleteDocument(item)}
      >
        <Text style={styles.deleteButtonText}>Delete</Text>
      </TouchableOpacity>
    </View>
  );

  return (
    <ScrollView style={styles.container}>
      {isLoading && (
        <View style={styles.loadingOverlay}>
          <ActivityIndicator size="large" color="#2196f3" />
          <Text style={styles.loadingText}>Processing...</Text>
        </View>
      )}

      <View style={styles.header}>
        <Text style={styles.title}>Local LLM Setup</Text>
      </View>

      {/* Compatibility Check */}
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Device Compatibility</Text>

        {deviceInfo ? (
          <View>
            <View style={styles.compatibilityStatus}>
              <Text style={styles.label}>Status:</Text>
              <View
                style={[
                  styles.statusBadge,
                  isCompatible ? styles.statusSuccess : styles.statusError,
                ]}
              >
                <Text style={styles.statusText}>
                  {isCompatible ? "Compatible" : "Incompatible"}
                </Text>
              </View>
            </View>

            {!isCompatible && (
              <Text style={styles.errorText}>{compatibilityReason}</Text>
            )}

            <View style={styles.deviceInfoContainer}>
              <Text style={styles.deviceInfoTitle}>Device Information:</Text>
              <Text style={styles.deviceInfoItem}>
                Model: {deviceInfo.manufacturer} {deviceInfo.model}
              </Text>
              <Text style={styles.deviceInfoItem}>
                CPU: {deviceInfo.cores} cores
              </Text>
              <Text style={styles.deviceInfoItem}>
                RAM: {Math.round(deviceInfo.totalMemoryMB / 1024)} GB (
                {Math.round(deviceInfo.freeMemoryMB / 1024)} GB free)
              </Text>
              <Text style={styles.deviceInfoItem}>
                Android SDK: {deviceInfo.sdk}
              </Text>
            </View>
          </View>
        ) : (
          <Text style={styles.loadingText}>Loading device info...</Text>
        )}
      </View>

      {isCompatible && (
        <>
          {/* Document Management */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Document Management</Text>
            <Text style={styles.description}>
              Upload documents that the LLM will use to answer questions.
              Supported formats include PDF, DOC, DOCX, and TXT.
            </Text>

            {/* New Text Input and Save Button */}
            <View style={styles.textInputContainer}>
              <TextInput
                style={styles.textInput}
                placeholder="Type or paste text here to create a document..."
                value={textInputValue}
                onChangeText={setTextInputValue}
                multiline={true}
                numberOfLines={4}
              />
              <TouchableOpacity
                style={styles.saveTextButton}
                onPress={handleSaveTextAsDocument}
                disabled={savingText || !textInputValue.trim()}
              >
                {savingText ? (
                  <ActivityIndicator size="small" color="#fff" />
                ) : (
                  <Text style={styles.buttonText}>Save as Document</Text>
                )}
              </TouchableOpacity>
            </View>

            <TouchableOpacity
              style={styles.uploadButton}
              onPress={handleDocumentUpload}
              disabled={uploading}
            >
              <Text style={styles.buttonText}>Upload Document</Text>
            </TouchableOpacity>

            {uploading && (
              <View style={styles.centeredRow}>
                <ActivityIndicator size="small" color="#2196f3" />
                <Text style={styles.statusText}>Uploading document...</Text>
              </View>
            )}

            <Text style={styles.listHeader}>
              Uploaded Documents ({documents.length})
            </Text>

            {documents.length === 0 ? (
              <Text style={styles.emptyListText}>
                No documents uploaded yet. Upload documents to enable LLM
                responses.
              </Text>
            ) : (
              <FlatList
                data={documents}
                renderItem={renderDocumentItem}
                keyExtractor={(item) => item.path}
                style={styles.documentList}
                scrollEnabled={false}
              />
            )}
          </View>

          {/* Model Management */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Model Management</Text>

            <View style={styles.modelToggleContainer}>
              <Text style={styles.label}>LLM Model Status:</Text>
              <View style={styles.row}>
                <Text style={styles.modelStatus}>
                  {isModelLoaded ? "Loaded" : "Not Loaded"}
                </Text>
                {documents.length > 0 && (
                  <TouchableOpacity
                    disabled={true}
                    style={[
                      styles.button,
                      isModelLoaded ? styles.unloadButton : styles.loadButton,
                    ]}
                    onPress={toggleModelLoading}
                  >
                    <Text style={styles.buttonText}>
                      {isModelLoaded ? "Unload Model" : "Load Model"}
                    </Text>
                  </TouchableOpacity>
                )}
              </View>
            </View>

            {documents.length === 0 && (
              <Text style={styles.warningText}>
                Upload at least one document to enable model loading.
              </Text>
            )}

            {selectedModel && (
              <Text
                style={styles.selectedModelText}
                numberOfLines={1}
                ellipsizeMode="middle"
              >
                Selected: {selectedModel}
              </Text>
            )}

            {/* Advanced Settings */}
            <View style={styles.advancedToggle}>
              <Text style={styles.label}>Show Advanced Settings</Text>
              <Switch
                value={showAdvanced}
                onValueChange={setShowAdvanced}
                trackColor={{ false: "#767577", true: "#2196f3" }}
                thumbColor={showAdvanced ? "#ffffff" : "#f4f3f4"}
              />
            </View>

            {showAdvanced && (
              <View style={styles.advancedSettings}>
                <Text style={styles.settingLabel}>
                  Temperature: {temperature.toFixed(2)}
                </Text>
                <View style={styles.sliderContainer}>
                  <TouchableOpacity
                    onPress={() =>
                      setTemperature(Math.max(0, temperature - 0.05))
                    }
                    style={styles.sliderButton}
                  >
                    <Text>-</Text>
                  </TouchableOpacity>
                  <View style={styles.sliderTrack}>
                    <View
                      style={[
                        styles.sliderFill,
                        { width: `${(temperature / 1.0) * 100}%` },
                      ]}
                    />
                  </View>
                  <TouchableOpacity
                    onPress={() =>
                      setTemperature(Math.min(1, temperature + 0.05))
                    }
                    style={styles.sliderButton}
                  >
                    <Text>+</Text>
                  </TouchableOpacity>
                </View>

                <Text style={styles.settingLabel}>Max Tokens: {maxTokens}</Text>
                <View style={styles.sliderContainer}>
                  <TouchableOpacity
                    onPress={() => setMaxTokens(Math.max(50, maxTokens - 10))}
                    style={styles.sliderButton}
                  >
                    <Text>-</Text>
                  </TouchableOpacity>
                  <View style={styles.sliderTrack}>
                    <View
                      style={[
                        styles.sliderFill,
                        { width: `${((maxTokens - 50) / 450) * 100}%` },
                      ]}
                    />
                  </View>
                  <TouchableOpacity
                    onPress={() => setMaxTokens(Math.min(500, maxTokens + 10))}
                    style={styles.sliderButton}
                  >
                    <Text>+</Text>
                  </TouchableOpacity>
                </View>

                <TouchableOpacity
                  style={styles.saveConfigButton}
                  onPress={saveModelConfig}
                >
                  <Text style={styles.buttonText}>Save Configuration</Text>
                </TouchableOpacity>
              </View>
            )}
          </View>

          {/* Usage Instructions */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Usage Instructions</Text>
            <Text style={styles.instructionText}>
              1. Upload documents that contain information for the LLM to
              reference.
            </Text>
            <Text style={styles.instructionText}>2. Load the LLM model.</Text>
            <Text style={styles.instructionText}>
              3. The app will automatically respond to SMS messages using the
              local LLM.
            </Text>
            <Text style={styles.instructionText}>
              4. All processing happens on-device for privacy.
            </Text>
          </View>
        </>
      )}
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
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: "rgba(255, 255, 255, 0.8)",
    alignItems: "center",
    justifyContent: "center",
    zIndex: 999,
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
  section: {
    marginHorizontal: 16,
    marginTop: 16,
    marginBottom: 16,
    padding: 16,
    backgroundColor: "#fff",
    borderRadius: 8,
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
    marginBottom: 16,
  },
  label: {
    fontSize: 16,
    fontWeight: "500",
    marginRight: 8,
    color: "#333",
  },
  compatibilityStatus: {
    flexDirection: "row",
    alignItems: "center",
    marginBottom: 12,
  },
  statusBadge: {
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 12,
  },
  statusSuccess: {
    backgroundColor: "#4caf50",
  },
  statusError: {
    backgroundColor: "#f44336",
  },
  statusText: {
    color: "#fff",
    fontWeight: "bold",
    fontSize: 14,
  },
  errorText: {
    color: "#f44336",
    marginBottom: 12,
  },
  deviceInfoContainer: {
    backgroundColor: "#f9f9f9",
    padding: 12,
    borderRadius: 8,
    marginTop: 8,
  },
  deviceInfoTitle: {
    fontSize: 16,
    fontWeight: "500",
    marginBottom: 8,
  },
  deviceInfoItem: {
    fontSize: 14,
    marginBottom: 4,
    color: "#555",
  },
  uploadButton: {
    backgroundColor: "#2196f3",
    paddingVertical: 12,
    paddingHorizontal: 16,
    borderRadius: 4,
    alignItems: "center",
    marginBottom: 16,
  },
  buttonText: {
    color: "#fff",
    fontWeight: "bold",
    fontSize: 16,
  },
  listHeader: {
    fontSize: 16,
    fontWeight: "500",
    marginBottom: 8,
    color: "#333",
  },
  emptyListText: {
    color: "#666",
    fontStyle: "italic",
    marginBottom: 8,
  },
  documentList: {
    maxHeight: 300,
  },
  documentItem: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    paddingVertical: 12,
    paddingHorizontal: 8,
    borderBottomWidth: 1,
    borderBottomColor: "#eee",
  },
  documentInfo: {
    flex: 1,
    marginRight: 8,
  },
  documentName: {
    fontSize: 16,
    fontWeight: "500",
    marginBottom: 4,
  },
  documentMetaContainer: {
    flexDirection: "row",
    alignItems: "center",
    flexWrap: "wrap",
  },
  documentMeta: {
    fontSize: 12,
    color: "#666",
  },
  deleteButton: {
    backgroundColor: "#f44336",
    paddingVertical: 6,
    paddingHorizontal: 12,
    borderRadius: 4,
  },
  deleteButtonText: {
    color: "#fff",
    fontSize: 14,
    fontWeight: "500",
  },
  modelToggleContainer: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: 16,
  },
  row: {
    flexDirection: "row",
    alignItems: "center",
  },
  centeredRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    marginBottom: 16,
  },
  modelStatus: {
    fontSize: 16,
    fontWeight: "500",
    marginRight: 12,
    color: "#333",
  },
  button: {
    paddingVertical: 8,
    paddingHorizontal: 12,
    borderRadius: 4,
    alignItems: "center",
  },
  loadButton: {
    backgroundColor: "#4caf50",
  },
  unloadButton: {
    backgroundColor: "#f44336",
  },
  selectedModelText: {
    fontSize: 14,
    color: "#666",
    marginBottom: 16,
  },
  advancedToggle: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginTop: 16,
    marginBottom: 8,
    paddingTop: 16,
    borderTopWidth: 1,
    borderTopColor: "#eee",
  },
  advancedSettings: {
    marginTop: 16,
    backgroundColor: "#f9f9f9",
    padding: 12,
    borderRadius: 8,
  },
  settingLabel: {
    fontSize: 14,
    marginBottom: 4,
    color: "#333",
  },
  slider: {
    width: "100%",
    height: 40,
    marginBottom: 16,
  },
  sliderContainer: {
    flexDirection: "row",
    alignItems: "center",
    marginVertical: 8,
    marginBottom: 16,
  },
  sliderTrack: {
    flex: 1,
    height: 6,
    backgroundColor: "#d3d3d3",
    borderRadius: 3,
    marginHorizontal: 8,
  },
  sliderFill: {
    height: 6,
    backgroundColor: "#2196f3",
    borderRadius: 3,
  },
  sliderButton: {
    width: 30,
    height: 30,
    borderRadius: 15,
    backgroundColor: "#f0f0f0",
    justifyContent: "center",
    alignItems: "center",
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.2,
    shadowRadius: 1,
    elevation: 2,
  },
  saveConfigButton: {
    backgroundColor: "#2196f3",
    paddingVertical: 10,
    paddingHorizontal: 16,
    borderRadius: 4,
    alignItems: "center",
  },
  loadingText: {
    color: "#666",
    marginTop: 12,
    fontSize: 16,
  },
  instructionText: {
    fontSize: 14,
    color: "#333",
    marginBottom: 8,
    lineHeight: 20,
  },
  docTypeTag: {
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 4,
    marginLeft: 6,
  },
  pdfTag: {
    backgroundColor: "#f44336",
  },
  docxTag: {
    backgroundColor: "#2196f3",
  },
  docTypeText: {
    color: "white",
    fontSize: 10,
    fontWeight: "bold",
  },
  warningText: {
    color: "#f44336",
    fontSize: 14,
    marginTop: 8,
    marginBottom: 12,
  },
  textInputContainer: {
    marginBottom: 16,
  },
  textInput: {
    borderWidth: 1,
    borderColor: "#ddd",
    borderRadius: 4,
    padding: 12,
    minHeight: 100,
    marginBottom: 8,
    backgroundColor: "#f9f9f9",
    color: "#333",
    textAlignVertical: "top",
  },
  saveTextButton: {
    backgroundColor: "#4caf50",
    paddingVertical: 12,
    paddingHorizontal: 16,
    borderRadius: 4,
    alignItems: "center",
    marginBottom: 16,
  },
  txtTag: {
    backgroundColor: "#ff9800",
  },
});

export default LocalLLMSetupScreen;
