import { NativeModules, Platform, NativeEventEmitter } from "react-native";
import AsyncStorage from "@react-native-async-storage/async-storage";
import PermissionsService from "./PermissionsService";
import AIService from "./AIService";

const { CallSmsModule } = NativeModules;
const eventEmitter = new NativeEventEmitter(CallSmsModule);

// Define SMS history item interface
export interface SmsHistoryItem {
  id: string;
  phoneNumber: string;
  message: string;
  status: "SENT" | "FAILED";
  timestamp: number;
  error?: string;
}

// Define call type constants
export const CALL_TYPES = {
  INCOMING: 1,
  OUTGOING: 2,
  MISSED: 3,
};

// SMS history storage key
const SMS_HISTORY_STORAGE_KEY = "@AutoSMS:SmsHistory";
const AUTO_SMS_ENABLED_KEY = "@AutoSMS:Enabled";
const AI_SMS_ENABLED_KEY = "@AutoSMS:AIEnabled";
const INITIAL_SMS_MESSAGE_KEY = "@AutoSMS:InitialMessage";

/**
 * Service to handle call monitoring and SMS sending
 */
class CallSmsService {
  private listeners: { [key: string]: (() => void)[] } = {};
  private isMonitoring: boolean = false;
  private defaultInitialMessage: string =
    "AI: I am busy, available only for chat. How may I help you?";

  constructor() {
    // Initialize event listeners
    this.setupEventListeners();
  }

  /**
   * Set up event listeners for SMS events
   */
  private setupEventListeners() {
    // SMS sent event
    eventEmitter.addListener("onSmsSent", this.handleSmsSent);

    // SMS error event
    eventEmitter.addListener("onSmsError", this.handleSmsError);

    // New SMS received event
    eventEmitter.addListener("onSmsReceived", this.handleSmsReceived);
  }

  /**
   * Handle SMS sent event
   */
  private handleSmsSent = async (data: any) => {
    console.log("SMS sent:", data);

    // Create history item
    const historyItem: SmsHistoryItem = {
      id: `${data.phoneNumber}-${data.timestamp}`,
      phoneNumber: data.phoneNumber,
      message: data.message,
      status: "SENT",
      timestamp: data.timestamp,
    };

    // Save to history
    await this.addToSmsHistory(historyItem);

    // Notify listeners
    this.notifyListeners("smsSent", historyItem);
  };

  /**
   * Handle SMS error event
   */
  private handleSmsError = async (data: any) => {
    console.log("SMS error:", data);

    // Create history item
    const historyItem: SmsHistoryItem = {
      id: `${data.phoneNumber}-${data.timestamp}`,
      phoneNumber: data.phoneNumber,
      message: data.message,
      status: "FAILED",
      timestamp: data.timestamp,
      error: data.error,
    };

    // Save to history
    await this.addToSmsHistory(historyItem);

    // Notify listeners
    this.notifyListeners("smsError", historyItem);
  };

  /**
   * Handle incoming SMS message
   */
  private handleSmsReceived = async (data: any) => {
    console.log("SMS received:", data);

    // If AI SMS is not enabled, just log the message
    const aiEnabled = await this.isAIEnabled();
    if (!aiEnabled) {
      console.log("AI SMS is disabled. Not responding to incoming message.");
      return;
    }

    try {
      // Check if this is a response to a missed call SMS we sent earlier
      const { phoneNumber, message } = data;

      // Get the AI-generated response
      const aiResponse = await AIService.generateResponse(phoneNumber, message);

      // Send the AI response
      await this.sendSms(phoneNumber, aiResponse);
    } catch (error) {
      console.error("Error handling incoming SMS:", error);
    }
  };

  /**
   * Add a new item to SMS history
   */
  private async addToSmsHistory(item: SmsHistoryItem): Promise<void> {
    try {
      // Get current history
      const history = await this.getSmsHistory();

      // Add new item
      history.unshift(item);

      // Save updated history
      await AsyncStorage.setItem(
        SMS_HISTORY_STORAGE_KEY,
        JSON.stringify(history)
      );
    } catch (error) {
      console.error("Error adding to SMS history:", error);
    }
  }

  /**
   * Check if all required permissions are granted
   */
  async hasRequiredPermissions(): Promise<boolean> {
    return await PermissionsService.areAllPermissionsGranted();
  }

  /**
   * Start monitoring for missed calls
   */
  async startMonitoringCalls(): Promise<boolean> {
    if (Platform.OS !== "android") {
      console.warn("Call monitoring is only supported on Android");
      return false;
    }

    // Check permissions first
    if (!(await this.hasRequiredPermissions())) {
      console.warn(
        "Cannot start call monitoring: Missing required permissions. Please grant all permissions first."
      );
      return false;
    }

    try {
      const result = await CallSmsModule.startMonitoringCalls();
      this.isMonitoring = result;
      return result;
    } catch (error: any) {
      // Check if this is a permissions error
      if (error.message && error.message.includes("permission")) {
        console.warn(
          "Missing permissions for call monitoring. Please grant all required permissions (Call Log, Phone State, SMS) in the Permissions screen."
        );
      } else {
        console.error("Error starting call monitoring:", error);
      }
      this.isMonitoring = false;
      return false;
    }
  }

  /**
   * Stop monitoring for missed calls
   */
  async stopMonitoringCalls(): Promise<boolean> {
    if (Platform.OS !== "android") {
      return false;
    }

    try {
      const result = await CallSmsModule.stopMonitoringCalls();
      this.isMonitoring = !result;
      return result;
    } catch (error) {
      console.error("Error stopping call monitoring:", error);
      return false;
    }
  }

  /**
   * Check if call monitoring is active
   */
  isMonitoringCalls(): boolean {
    return this.isMonitoring;
  }

  /**
   * Send an SMS message
   */
  async sendSms(phoneNumber: string, message: string): Promise<boolean> {
    if (Platform.OS !== "android") {
      console.warn("SMS sending is only supported on Android");
      return false;
    }

    // Check permissions first
    if (!(await this.hasRequiredPermissions())) {
      console.warn(
        "Cannot send SMS: Missing required permissions. Please grant all permissions first."
      );
      return false;
    }

    try {
      return await CallSmsModule.sendSms(phoneNumber, message);
    } catch (error) {
      console.error("Error sending SMS:", error);
      return false;
    }
  }

  /**
   * Get recent calls
   * @param days Number of days to look back
   */
  async getRecentCalls(days: number = 7): Promise<any[]> {
    if (Platform.OS !== "android") {
      return [];
    }

    // Check permissions first
    const callLogPermission = await PermissionsService.checkPermission(
      "callLog"
    );
    if (callLogPermission !== "granted") {
      console.warn("Cannot get call log: Missing required permissions.");
      return [];
    }

    try {
      return await CallSmsModule.getRecentCalls(days);
    } catch (error) {
      console.error("Error getting recent calls:", error);
      return [];
    }
  }

  /**
   * Get SMS history from storage
   */
  async getSmsHistory(): Promise<SmsHistoryItem[]> {
    try {
      const historyJson = await AsyncStorage.getItem(SMS_HISTORY_STORAGE_KEY);

      if (historyJson) {
        return JSON.parse(historyJson);
      }

      return [];
    } catch (error) {
      console.error("Error getting SMS history:", error);
      return [];
    }
  }

  /**
   * Clear SMS history
   */
  async clearSmsHistory(): Promise<boolean> {
    try {
      await AsyncStorage.removeItem(SMS_HISTORY_STORAGE_KEY);
      this.notifyListeners("historyCleared", null);
      return true;
    } catch (error) {
      console.error("Error clearing SMS history:", error);
      return false;
    }
  }

  /**
   * Set auto SMS enabled setting
   */
  async setAutoSmsEnabled(enabled: boolean): Promise<void> {
    try {
      // Save setting to AsyncStorage (for React Native UI)
      await AsyncStorage.setItem(AUTO_SMS_ENABLED_KEY, enabled.toString());

      // Apply setting to native module (for use when app is killed)
      if (Platform.OS === "android") {
        await CallSmsModule.setAutoSmsEnabled(enabled);
      }

      this.notifyListeners("settingsChanged", { autoSmsEnabled: enabled });
    } catch (error) {
      console.error("Error setting auto SMS enabled:", error);
    }
  }

  /**
   * Get auto SMS enabled setting
   */
  async isAutoSmsEnabled(): Promise<boolean> {
    try {
      if (Platform.OS === "android") {
        // Get value from native module that syncs with SharedPreferences
        try {
          return await CallSmsModule.isAutoSmsEnabled();
        } catch (e) {
          console.warn(
            "Error getting setting from native module, falling back to AsyncStorage",
            e
          );
        }
      }

      // Fallback to AsyncStorage
      const value = await AsyncStorage.getItem(AUTO_SMS_ENABLED_KEY);
      return value === null ? true : value === "true";
    } catch (error) {
      console.error("Error getting auto SMS enabled setting:", error);
      return true; // Default to enabled
    }
  }

  /**
   * Sync SMS history from native storage to AsyncStorage
   */
  async syncHistoryFromNative(): Promise<void> {
    if (Platform.OS !== "android") {
      return;
    }

    try {
      // Request the native module to sync any history saved while app was killed
      const nativeHistory = await CallSmsModule.getSmsHistoryFromNative();
      if (nativeHistory && nativeHistory.length > 0) {
        // Get current history from AsyncStorage
        const currentHistory = await this.getSmsHistory();

        // Merge histories (avoid duplicates by using Set of IDs)
        const mergedHistory = [...currentHistory];
        const existingIds = new Set(currentHistory.map((item) => item.id));

        for (const item of nativeHistory) {
          if (!existingIds.has(item.id)) {
            mergedHistory.unshift(item);
          }
        }

        // Save merged history
        await AsyncStorage.setItem(
          SMS_HISTORY_STORAGE_KEY,
          JSON.stringify(mergedHistory)
        );

        // Notify listeners of updated history
        this.notifyListeners("historyUpdated", null);
      }
    } catch (error) {
      console.error("Error syncing native SMS history:", error);
    }
  }

  /**
   * Set initial SMS message
   */
  async setInitialSmsMessage(message: string): Promise<void> {
    try {
      await AsyncStorage.setItem(INITIAL_SMS_MESSAGE_KEY, message);
    } catch (error) {
      console.error("Error setting initial SMS message:", error);
    }
  }

  /**
   * Get initial SMS message
   */
  async getInitialSmsMessage(): Promise<string> {
    try {
      const message = await AsyncStorage.getItem(INITIAL_SMS_MESSAGE_KEY);
      return message || this.defaultInitialMessage;
    } catch (error) {
      console.error("Error getting initial SMS message:", error);
      return this.defaultInitialMessage;
    }
  }

  /**
   * Set AI SMS enabled setting
   */
  async setAIEnabled(enabled: boolean): Promise<void> {
    try {
      // Save setting to AsyncStorage
      await AsyncStorage.setItem(AI_SMS_ENABLED_KEY, enabled.toString());

      // Apply setting to native module
      if (Platform.OS === "android") {
        await NativeModules.CallSmsModule.setAIEnabled(enabled);
      }

      this.notifyListeners("settingsChanged", { aiEnabled: enabled });
    } catch (error) {
      console.error("Error setting AI SMS enabled:", error);
    }
  }

  /**
   * Check if AI SMS is enabled
   */
  async isAIEnabled(): Promise<boolean> {
    try {
      if (Platform.OS === "android") {
        try {
          return await NativeModules.CallSmsModule.isAIEnabled();
        } catch (e) {
          console.warn(
            "Error getting AI setting from native module, falling back to AsyncStorage",
            e
          );
        }
      }

      // Fallback to AsyncStorage
      const value = await AsyncStorage.getItem(AI_SMS_ENABLED_KEY);
      return value === null ? false : value === "true"; // Default to false
    } catch (error) {
      console.error("Error checking if AI SMS is enabled:", error);
      return false;
    }
  }

  /**
   * Send an SMS message for a missed call with AI support
   */
  async sendMissedCallSms(phoneNumber: string): Promise<boolean> {
    try {
      // Check if AI is enabled
      const aiEnabled = await this.isAIEnabled();

      // Get the appropriate message
      const message = await this.getInitialSmsMessage();

      // Send the message
      return await this.sendSms(phoneNumber, message);
    } catch (error) {
      console.error("Error sending missed call SMS:", error);
      return false;
    }
  }

  /**
   * Add a listener for a specific event
   */
  addListener(event: string, callback: () => void): void {
    if (!this.listeners[event]) {
      this.listeners[event] = [];
    }

    this.listeners[event].push(callback);
  }

  /**
   * Remove a listener for a specific event
   */
  removeListener(event: string, callback: () => void): void {
    if (this.listeners[event]) {
      this.listeners[event] = this.listeners[event].filter(
        (cb) => cb !== callback
      );
    }
  }

  /**
   * Notify all listeners of an event
   */
  private notifyListeners(event: string, data: any): void {
    if (this.listeners[event]) {
      this.listeners[event].forEach((callback) => callback());
    }
  }

  /**
   * Process any pending SMS messages that were received when the app was not active
   */
  async processPendingMessages(): Promise<boolean> {
    if (Platform.OS !== "android") {
      return false;
    }

    try {
      return await NativeModules.CallSmsModule.processPendingMessages();
    } catch (error) {
      console.error("Error processing pending messages:", error);
      return false;
    }
  }
}

export default new CallSmsService();
