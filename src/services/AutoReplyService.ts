import { NativeModules, Platform } from "react-native";
import AsyncStorage from "@react-native-async-storage/async-storage";
import LocalLLMService from "./LocalLLMService";
import RNFS from "react-native-fs";

const { CallSmsModule } = NativeModules;

// Storage keys
const AUTO_REPLY_ENABLED_KEY = "@AutoSMS:AutoReplyEnabled";
const LLM_AUTO_REPLY_ENABLED_KEY = "@AutoSMS:LLMAutoReplyEnabled";
const LLM_CONTEXT_LENGTH_KEY = "@AutoSMS:LLMContextLength";

/**
 * Service to manage auto-reply features
 */
class AutoReplyService {
  /**
   * Check if simple auto-reply is enabled
   */
  async isAutoReplyEnabled(): Promise<boolean> {
    try {
      if (Platform.OS !== "android") {
        return false;
      }

      const storedValue = await AsyncStorage.getItem(AUTO_REPLY_ENABLED_KEY);
      return storedValue === "true";
    } catch (error) {
      console.error("Error checking if auto-reply is enabled:", error);
      return false;
    }
  }

  /**
   * Set simple auto-reply enabled state
   */
  async setAutoReplyEnabled(enabled: boolean): Promise<boolean> {
    try {
      if (Platform.OS !== "android") {
        return false;
      }

      // Save to SharedPreferences through native module
      await CallSmsModule.setAutoReplyEnabled(enabled);

      // Also save to AsyncStorage for easier access in React
      await AsyncStorage.setItem(AUTO_REPLY_ENABLED_KEY, String(enabled));

      return true;
    } catch (error) {
      console.error("Error setting auto-reply enabled:", error);
      return false;
    }
  }

  /**
   * Check if LLM-based document auto-reply is enabled
   */
  async isLLMAutoReplyEnabled(): Promise<boolean> {
    try {
      if (Platform.OS !== "android") {
        return false;
      }

      const storedValue = await AsyncStorage.getItem(
        LLM_AUTO_REPLY_ENABLED_KEY
      );
      return storedValue === "true";
    } catch (error) {
      console.error("Error checking if LLM auto-reply is enabled:", error);
      return false;
    }
  }

  /**
   * Set LLM-based document auto-reply enabled state
   */
  async setLLMAutoReplyEnabled(enabled: boolean): Promise<boolean> {
    try {
      if (Platform.OS !== "android") {
        return false;
      }

      console.log(`[AutoReplyService] Setting LLM auto-reply to: ${enabled}`);

      // Critical: If enabling LLM auto-reply, we should disable the AI auto-reply first
      if (enabled) {
        // Directly update the native SharedPreferences via the native module
        if (CallSmsModule.setAIEnabled) {
          try {
            await CallSmsModule.setAIEnabled(false);
            console.log(
              "[AutoReplyService] Disabled AI auto-reply successfully"
            );
          } catch (e) {
            console.warn(
              "[AutoReplyService] Error disabling AI auto-reply:",
              e
            );
          }
        }

        // Set AsyncStorage value for AI enabled to false
        await AsyncStorage.setItem("@AutoSMS:AIEnabled", "false");
      }

      // Make sure the model is loaded and documents exist (only if enabling)
      if (enabled) {
        // Try to create a sample document first, before loading model
        console.log("[AutoReplyService] Creating sample document if needed");
        await this.createSampleDocumentIfNeeded();

        // Now try to load the model
        const isModelLoaded = await LocalLLMService.isModelLoaded();
        console.log(`[AutoReplyService] Is model loaded: ${isModelLoaded}`);

        if (!isModelLoaded) {
          // Create default model path
          const modelPath = `${RNFS.DocumentDirectoryPath}/models/default_model.bin`;
          console.log(`[AutoReplyService] Using model path: ${modelPath}`);

          // Ensure model directory exists
          const modelDir = modelPath.substring(0, modelPath.lastIndexOf("/"));
          const dirExists = await RNFS.exists(modelDir);

          if (!dirExists) {
            console.log(
              `[AutoReplyService] Creating model directory: ${modelDir}`
            );
            await RNFS.mkdir(modelDir);
          }

          // Create an empty model file if it doesn't exist
          const fileExists = await RNFS.exists(modelPath);
          if (!fileExists) {
            console.log(
              `[AutoReplyService] Creating placeholder model file: ${modelPath}`
            );
            await RNFS.writeFile(
              modelPath,
              "This is a placeholder model file",
              "utf8"
            );
          }

          // Save the model path to AsyncStorage
          await LocalLLMService.saveSelectedModel(modelPath);
          const loaded = await LocalLLMService.loadModel(modelPath);
          console.log(`[AutoReplyService] Model loaded: ${loaded}`);
        }
      }

      // Save to SharedPreferences through native module - this is the most important call
      if (CallSmsModule.setLLMAutoReplyEnabled) {
        console.log(
          `[AutoReplyService] Calling native setLLMAutoReplyEnabled with value: ${enabled}`
        );
        await CallSmsModule.setLLMAutoReplyEnabled(enabled);
      } else {
        console.warn(
          "[AutoReplyService] CallSmsModule.setLLMAutoReplyEnabled is not available!"
        );
      }

      // For testing purposes, also set the regular auto-reply flag to ensure something happens
      if (enabled && CallSmsModule.setAutoReplyEnabled) {
        console.log(
          "[AutoReplyService] Also enabling normal auto-reply for backup"
        );
        await CallSmsModule.setAutoReplyEnabled(true);
      }

      // Always save to AsyncStorage for consistency
      console.log(`[AutoReplyService] Saving to AsyncStorage: ${enabled}`);
      await AsyncStorage.setItem(LLM_AUTO_REPLY_ENABLED_KEY, String(enabled));

      // Add a test phone number to missed call list if needed
      if (enabled && CallSmsModule.addTestPhoneNumber) {
        try {
          console.log(
            "[AutoReplyService] Adding test phone number to missed call list"
          );
          await CallSmsModule.addTestPhoneNumber("1234567890");
        } catch (e) {
          console.warn("[AutoReplyService] Error adding test phone number:", e);
        }
      }

      return true;
    } catch (error) {
      console.error("Error setting LLM auto-reply enabled:", error);
      return false;
    }
  }

  /**
   * Create a sample document if none exist
   */
  async createSampleDocumentIfNeeded(): Promise<boolean> {
    try {
      const documents = await LocalLLMService.listDocuments();
      if (documents.length > 0) {
        return true;
      }

      console.log("Creating sample document for LLM");
      const content = `# Sample Document for Auto-Reply

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
      return result !== null;
    } catch (error) {
      console.error("Error creating sample document:", error);
      return false;
    }
  }

  /**
   * Get the context length for LLM
   */
  async getLLMContextLength(): Promise<number> {
    try {
      if (Platform.OS !== "android") {
        return 2048; // Default
      }

      const storedValue = await AsyncStorage.getItem(LLM_CONTEXT_LENGTH_KEY);
      return storedValue ? parseInt(storedValue, 10) : 2048;
    } catch (error) {
      console.error("Error getting LLM context length:", error);
      return 2048; // Default
    }
  }

  /**
   * Set the context length for LLM
   */
  async setLLMContextLength(length: number): Promise<boolean> {
    try {
      if (Platform.OS !== "android") {
        return false;
      }

      // Save the context length
      await AsyncStorage.setItem(LLM_CONTEXT_LENGTH_KEY, String(length));

      // If the native module has this method, call it
      if (CallSmsModule.setLLMContextLength) {
        await CallSmsModule.setLLMContextLength(length);
      }

      return true;
    } catch (error) {
      console.error("Error setting LLM context length:", error);
      return false;
    }
  }

  /**
   * Check if AI (OpenAI-based) auto-reply is enabled
   */
  async isAIEnabled(): Promise<boolean> {
    try {
      const value = await AsyncStorage.getItem("@AutoSMS:AIEnabled");
      return value === "true";
    } catch (error) {
      console.error("Error checking if AI is enabled:", error);
      return false;
    }
  }

  /**
   * Set AI (OpenAI-based) auto-reply enabled state
   */
  async setAIEnabled(enabled: boolean): Promise<boolean> {
    try {
      if (Platform.OS !== "android") {
        return false;
      }

      // Use the native module if available
      if (CallSmsModule.setAIEnabled) {
        await CallSmsModule.setAIEnabled(enabled);
      }

      // Save to AsyncStorage
      await AsyncStorage.setItem("@AutoSMS:AIEnabled", String(enabled));

      // If enabling AI auto-reply, disable LLM auto-reply (they are mutually exclusive)
      if (enabled) {
        const llmEnabled = await this.isLLMAutoReplyEnabled();
        if (llmEnabled) {
          await this.setLLMAutoReplyEnabled(false);
        }
      }

      return true;
    } catch (error) {
      console.error("Error setting AI enabled:", error);
      return false;
    }
  }

  /**
   * Get missed call numbers
   */
  async getMissedCallNumbers(): Promise<
    Array<{ phoneNumber: string; timestamp: number }>
  > {
    try {
      if (Platform.OS !== "android") {
        return [];
      }

      if (!CallSmsModule) {
        console.warn("CallSmsModule is not available");
        return [];
      }

      return await CallSmsModule.getMissedCallNumbers();
    } catch (error) {
      console.error("Error getting missed call numbers:", error);
      return [];
    }
  }

  /**
   * Clear missed call numbers
   */
  async clearMissedCallNumbers(): Promise<boolean> {
    try {
      if (Platform.OS !== "android") {
        return false;
      }

      if (!CallSmsModule) {
        console.warn("CallSmsModule is not available");
        return false;
      }

      return await CallSmsModule.clearMissedCallNumbers();
    } catch (error) {
      console.error("Error clearing missed call numbers:", error);
      return false;
    }
  }

  /**
   * Run LLM diagnostics to check auto-reply functionality
   */
  async runLLMDiagnostics(): Promise<any> {
    try {
      if (Platform.OS !== "android") {
        console.log("LLM diagnostics are only available on Android");
        return {
          error: "LLM diagnostics are only available on Android",
          platform: Platform.OS,
        };
      }

      if (!CallSmsModule?.checkLLMStatus) {
        console.warn("checkLLMStatus method not available in CallSmsModule");
        return {
          error: "checkLLMStatus method not available in CallSmsModule",
        };
      }

      console.log("Running LLM diagnostics...");
      const results = await CallSmsModule.checkLLMStatus();
      console.log("LLM diagnostic results:", results);

      return results;
    } catch (error: any) {
      console.error("Error running LLM diagnostics:", error);
      return {
        error: error.message || "Unknown error",
        stack: error.stack,
      };
    }
  }
}

export default new AutoReplyService();
