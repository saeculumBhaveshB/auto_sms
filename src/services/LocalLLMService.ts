import { NativeModules, Platform } from "react-native";
import AsyncStorage from "@react-native-async-storage/async-storage";
import * as DocumentPicker from "@react-native-documents/picker";

const { LocalLLMModule } = NativeModules;

// Storage keys
const LLM_SELECTED_MODEL_KEY = "@AutoSMS:SelectedModel";
const LLM_TEMPERATURE_KEY = "@AutoSMS:Temperature";
const LLM_MAX_TOKENS_KEY = "@AutoSMS:MaxTokens";

// Define types
export interface DeviceInfo {
  manufacturer: string;
  model: string;
  device: string;
  product: string;
  hardware: string;
  sdk: number;
  cpuAbi: string;
  cores: number;
  freeMemoryMB: number;
  totalMemoryMB: number;
}

export interface DocumentInfo {
  name: string;
  path: string;
  size: number;
  lastModified: number;
}

export interface ModelConfig {
  temperature: number;
  maxTokens: number;
}

// Helper function to check if native module is available
const isNativeModuleAvailable = (): boolean => {
  return LocalLLMModule != null;
};

class LocalLLMService {
  // Default configuration
  private defaultConfig: ModelConfig = {
    temperature: 0.7,
    maxTokens: 150,
  };

  /**
   * Get device information for model compatibility check
   */
  async getDeviceInfo(): Promise<DeviceInfo> {
    try {
      if (!isNativeModuleAvailable()) {
        console.warn(
          "LocalLLMModule native module is not available. Did you rebuild the app?"
        );
        // Return default values for device info
        return {
          manufacturer: "Unknown",
          model: "Unknown",
          device: "Unknown",
          product: "Unknown",
          hardware: "Unknown",
          sdk: Platform.OS === "android" ? 23 : 0,
          cpuAbi: "Unknown",
          cores: 4,
          freeMemoryMB: 1000,
          totalMemoryMB: 4000,
        };
      }

      return await LocalLLMModule.getDeviceInfo();
    } catch (error) {
      console.error("Error getting device info:", error);
      throw error;
    }
  }

  /**
   * Check if the device is compatible with local LLM
   */
  async checkDeviceCompatibility(): Promise<{
    compatible: boolean;
    reason?: string;
  }> {
    try {
      if (!isNativeModuleAvailable()) {
        console.warn(
          "LocalLLMModule native module is not available. Did you rebuild the app?"
        );
        return {
          compatible: false,
          reason: "Native module not available. Please restart the app.",
        };
      }

      const deviceInfo = await this.getDeviceInfo();

      // Define minimum requirements for running local LLM
      const MIN_SDK = 23; // Android 6.0
      const MIN_CORES = 4;
      const MIN_MEMORY_MB = 1000; // 1GB (enough for simple LLM models)

      console.log(`Device info: ${JSON.stringify(deviceInfo, null, 2)}`);

      if (Platform.OS !== "android") {
        return {
          compatible: false,
          reason: "Local LLM is only supported on Android devices.",
        };
      }

      if (deviceInfo.sdk < MIN_SDK) {
        return {
          compatible: false,
          reason: `Android SDK version ${deviceInfo.sdk} is too low. Minimum required is ${MIN_SDK}.`,
        };
      }

      if (deviceInfo.cores < MIN_CORES) {
        return {
          compatible: false,
          reason: `Device has ${deviceInfo.cores} CPU cores. Minimum required is ${MIN_CORES}.`,
        };
      }

      if (deviceInfo.totalMemoryMB < MIN_MEMORY_MB) {
        return {
          compatible: false,
          reason: `Device has ${Math.round(
            deviceInfo.totalMemoryMB
          )}MB of RAM. Minimum required is ${MIN_MEMORY_MB}MB.`,
        };
      }

      return { compatible: true };
    } catch (error) {
      console.error("Error checking device compatibility:", error);
      return {
        compatible: false,
        reason: "Error checking device compatibility.",
      };
    }
  }

  /**
   * Load a model for inference
   */
  async loadModel(modelPath: string): Promise<boolean> {
    try {
      if (!isNativeModuleAvailable()) {
        console.warn(
          "LocalLLMModule native module is not available. Did you rebuild the app?"
        );
        return false;
      }

      return await LocalLLMModule.loadModel(modelPath);
    } catch (error) {
      console.error("Error loading model:", error);
      throw error;
    }
  }

  /**
   * Check if a model is loaded
   */
  async isModelLoaded(): Promise<boolean> {
    try {
      if (!isNativeModuleAvailable()) {
        console.warn(
          "LocalLLMModule native module is not available. Did you rebuild the app?"
        );
        return false;
      }

      return await LocalLLMModule.isModelLoaded();
    } catch (error) {
      console.error("Error checking if model is loaded:", error);
      return false;
    }
  }

  /**
   * Unload the current model
   */
  async unloadModel(): Promise<boolean> {
    try {
      if (!isNativeModuleAvailable()) {
        console.warn(
          "LocalLLMModule native module is not available. Did you rebuild the app?"
        );
        return false;
      }

      return await LocalLLMModule.unloadModel();
    } catch (error) {
      console.error("Error unloading model:", error);
      throw error;
    }
  }

  /**
   * Generate an answer to a question using the local LLM
   */
  async generateAnswer(
    question: string,
    config?: Partial<ModelConfig>
  ): Promise<string> {
    try {
      if (!isNativeModuleAvailable()) {
        console.warn(
          "LocalLLMModule native module is not available. Did you rebuild the app?"
        );
        return "AI: Sorry, I am not capable of giving this answer. Wait for a call or try with a different question.";
      }

      const modelConfig = await this.getModelConfig();
      const temperature = config?.temperature ?? modelConfig.temperature;
      const maxTokens = config?.maxTokens ?? modelConfig.maxTokens;

      return await LocalLLMModule.generateAnswer(
        question,
        temperature,
        maxTokens
      );
    } catch (error) {
      console.error("Error generating answer:", error);
      return "AI: Sorry, I am not capable of giving this answer. Wait for a call or try with a different question.";
    }
  }

  /**
   * Pick a document and upload it
   */
  async pickAndUploadDocument(): Promise<DocumentInfo | null> {
    try {
      if (!isNativeModuleAvailable()) {
        console.warn(
          "LocalLLMModule native module is not available. Did you rebuild the app?"
        );
        throw new Error("Native module not available. Please restart the app.");
      }

      const results = await DocumentPicker.pick({
        type: [
          "application/pdf",
          "application/msword",
          "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
          "text/plain",
        ],
        allowMultiSelection: false,
      });

      if (results.length > 0) {
        const document = results[0];
        const filename = document.name || `document_${Date.now()}`;

        // Upload to native storage
        const result = await LocalLLMModule.uploadDocument(
          document.uri,
          filename
        );
        return result;
      }

      return null;
    } catch (err) {
      if (
        DocumentPicker.isErrorWithCode(err) &&
        err.code === DocumentPicker.errorCodes.OPERATION_CANCELED
      ) {
        console.log("User cancelled document picker");
        return null;
      }
      console.error("Error picking document:", err);
      throw err;
    }
  }

  /**
   * List all uploaded documents
   */
  async listDocuments(): Promise<DocumentInfo[]> {
    try {
      if (!isNativeModuleAvailable()) {
        console.warn(
          "LocalLLMModule native module is not available. Did you rebuild the app?"
        );
        return [];
      }

      return await LocalLLMModule.listDocuments();
    } catch (error) {
      console.error("Error listing documents:", error);
      return [];
    }
  }

  /**
   * Delete a document
   */
  async deleteDocument(fileName: string): Promise<boolean> {
    try {
      if (!isNativeModuleAvailable()) {
        console.warn(
          "LocalLLMModule native module is not available. Did you rebuild the app?"
        );
        return false;
      }

      return await LocalLLMModule.deleteDocument(fileName);
    } catch (error) {
      console.error("Error deleting document:", error);
      throw error;
    }
  }

  /**
   * Save model configuration
   */
  async saveModelConfig(config: Partial<ModelConfig>): Promise<void> {
    try {
      if (config.temperature !== undefined) {
        await AsyncStorage.setItem(
          LLM_TEMPERATURE_KEY,
          config.temperature.toString()
        );
      }

      if (config.maxTokens !== undefined) {
        await AsyncStorage.setItem(
          LLM_MAX_TOKENS_KEY,
          config.maxTokens.toString()
        );
      }
    } catch (error) {
      console.error("Error saving model config:", error);
    }
  }

  /**
   * Get model configuration
   */
  async getModelConfig(): Promise<ModelConfig> {
    try {
      const temperatureStr = await AsyncStorage.getItem(LLM_TEMPERATURE_KEY);
      const maxTokensStr = await AsyncStorage.getItem(LLM_MAX_TOKENS_KEY);

      return {
        temperature: temperatureStr
          ? parseFloat(temperatureStr)
          : this.defaultConfig.temperature,
        maxTokens: maxTokensStr
          ? parseInt(maxTokensStr, 10)
          : this.defaultConfig.maxTokens,
      };
    } catch (error) {
      console.error("Error getting model config:", error);
      return this.defaultConfig;
    }
  }

  /**
   * Save selected model
   */
  async saveSelectedModel(modelPath: string): Promise<void> {
    try {
      await AsyncStorage.setItem(LLM_SELECTED_MODEL_KEY, modelPath);
    } catch (error) {
      console.error("Error saving selected model:", error);
    }
  }

  /**
   * Get selected model
   */
  async getSelectedModel(): Promise<string | null> {
    try {
      return await AsyncStorage.getItem(LLM_SELECTED_MODEL_KEY);
    } catch (error) {
      console.error("Error getting selected model:", error);
      return null;
    }
  }

  /**
   * Test the auto-reply SMS functionality
   */
  async testAutoReply(
    phoneNumber: string,
    message: string
  ): Promise<{
    success: boolean;
    response?: string;
    error?: string;
  }> {
    try {
      if (!isNativeModuleAvailable()) {
        console.warn(
          "LocalLLMModule native module is not available. Did you rebuild the app?"
        );
        return {
          success: false,
          error: "Native module not available. Please restart the app.",
        };
      }

      // Get CallSmsModule through NativeModules
      const { CallSmsModule } = NativeModules;

      if (!CallSmsModule || !CallSmsModule.testAutoReplySms) {
        return {
          success: false,
          error: "CallSmsModule.testAutoReplySms method not available",
        };
      }

      // Call the test method
      const result = await CallSmsModule.testAutoReplySms(phoneNumber, message);
      return {
        success: true,
        response: result.response,
      };
    } catch (error) {
      console.error("Error testing auto-reply SMS:", error);
      return {
        success: false,
        error: String(error),
      };
    }
  }

  /**
   * Test LLM functionality with loaded documents
   */
  async testLlmWithDocuments(question: string): Promise<{
    success: boolean;
    response?: string;
    documentCount?: number;
    modelLoaded?: boolean;
    error?: string;
  }> {
    try {
      if (!isNativeModuleAvailable()) {
        console.warn(
          "LocalLLMModule native module is not available. Did you rebuild the app?"
        );
        return {
          success: false,
          error: "Native module not available. Please restart the app.",
        };
      }

      // Test LLM with documents
      const result = await LocalLLMModule.testLlmWithDocuments(question);

      return {
        success: true,
        response: result.response,
        documentCount: result.documentCount,
        modelLoaded: result.modelLoaded,
      };
    } catch (error) {
      console.error("Error testing LLM with documents:", error);
      return {
        success: false,
        error: String(error),
      };
    }
  }
}

export default new LocalLLMService();
