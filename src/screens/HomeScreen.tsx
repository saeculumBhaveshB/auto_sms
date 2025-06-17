import React from "react";
import { TouchableOpacity, Text, StyleSheet, Alert } from "react-native";
import { AutoReplyService, LocalLLMService } from "../services";
import * as FileSystem from "expo-file-system";

const HomeScreen = () => {
  const enableLlmAutoReply = async () => {
    try {
      // Check if documents exist
      const documents = await LocalLLMService.listDocuments();
      console.log("📝 Documents count: ", documents.length);

      if (documents.length === 0) {
        Alert.alert(
          "No Documents",
          "Please upload at least one document in the LLM Setup screen before enabling LLM auto-reply."
        );
        return;
      }

      // Then load the model if not loaded
      const isModelLoaded = await LocalLLMService.isModelLoaded();
      console.log("🔍 Is model loaded: ", isModelLoaded);

      if (!isModelLoaded) {
        console.log("🧠 Loading model");
        // Create a model directory and fake model file for testing
        const modelPath = `${FileSystem.documentDirectory}models/default_model.bin`;
        const modelDir = modelPath.substring(0, modelPath.lastIndexOf("/"));

        try {
          const dirExists = await FileSystem.getInfoAsync(modelDir);
          if (!dirExists.exists) {
            await FileSystem.makeDirectoryAsync(modelDir, {
              intermediates: true,
            });
            console.log("📁 Created model directory");
          }
        } catch (e) {
          console.error("❌ Error creating model directory: ", e);
        }

        // Now load the model
        await LocalLLMService.saveSelectedModel(modelPath);
        const loadResult = await LocalLLMService.loadModel(modelPath);
        console.log("🧠 Model load result: ", loadResult);
      }

      // Enable LLM auto-reply
      console.log("🤖 Enabling LLM auto-reply");
      const result = await AutoReplyService.setLLMAutoReplyEnabled(true, false); // false to prevent sample document creation

      if (result) {
        Alert.alert("Success", "LLM auto-reply enabled successfully!");
      } else {
        Alert.alert("Error", "Failed to enable LLM auto-reply");
      }
    } catch (e) {
      console.error("❌ Error enabling LLM auto-reply: ", e);
      Alert.alert("Error", "Failed to enable LLM auto-reply: " + e.toString());
    }
  };

  return (
    <>
      <TouchableOpacity style={styles.debugButton} onPress={enableLlmAutoReply}>
        <Text style={styles.buttonText}>Enable LLM Auto-Reply</Text>
      </TouchableOpacity>
    </>
  );
};

const styles = StyleSheet.create({
  debugButton: {
    backgroundColor: "purple",
    padding: 15,
    borderRadius: 5,
    alignItems: "center",
    marginTop: 15,
  },
  buttonText: {
    color: "#ffffff",
    fontWeight: "bold",
  },
});

export default HomeScreen;
