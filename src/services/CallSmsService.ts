import { NativeModules, Platform, NativeEventEmitter } from "react-native";
import AsyncStorage from "@react-native-async-storage/async-storage";

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

/**
 * Service to handle call monitoring and SMS sending
 */
class CallSmsService {
  private listeners: { [key: string]: (() => void)[] } = {};
  private isMonitoring: boolean = false;

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
   * Start monitoring for missed calls
   */
  async startMonitoringCalls(): Promise<boolean> {
    if (Platform.OS !== "android") {
      console.warn("Call monitoring is only supported on Android");
      return false;
    }

    try {
      const result = await CallSmsModule.startMonitoringCalls();
      this.isMonitoring = result;
      return result;
    } catch (error) {
      console.error("Error starting call monitoring:", error);
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
   * Get auto SMS enabled setting
   */
  async isAutoSmsEnabled(): Promise<boolean> {
    try {
      const value = await AsyncStorage.getItem(AUTO_SMS_ENABLED_KEY);
      return value === null ? true : value === "true";
    } catch (error) {
      console.error("Error getting auto SMS enabled setting:", error);
      return true; // Default to enabled
    }
  }

  /**
   * Set auto SMS enabled setting
   */
  async setAutoSmsEnabled(enabled: boolean): Promise<void> {
    try {
      await AsyncStorage.setItem(AUTO_SMS_ENABLED_KEY, enabled.toString());
      this.notifyListeners("settingsChanged", { autoSmsEnabled: enabled });
    } catch (error) {
      console.error("Error setting auto SMS enabled:", error);
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
}

export default new CallSmsService();
