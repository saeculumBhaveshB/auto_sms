import { NativeModules, Platform } from "react-native";
import AsyncStorage from "@react-native-async-storage/async-storage";

// Define permission types
export type PermissionType =
  | "READ_CALL_LOG"
  | "CALL_PHONE"
  | "ANSWER_PHONE_CALLS"
  | "READ_CONTACTS"
  | "SEND_SMS"
  | "READ_SMS"
  | "POST_NOTIFICATIONS";

// Interface for permission status
export interface PermissionsStatus {
  READ_CALL_LOG: boolean;
  CALL_PHONE: boolean;
  ANSWER_PHONE_CALLS: boolean;
  READ_CONTACTS: boolean;
  SEND_SMS: boolean;
  READ_SMS: boolean;
  POST_NOTIFICATIONS: boolean;
}

// Default permission status
const DEFAULT_PERMISSIONS_STATUS: PermissionsStatus = {
  READ_CALL_LOG: false,
  CALL_PHONE: false,
  ANSWER_PHONE_CALLS: false,
  READ_CONTACTS: false,
  SEND_SMS: false,
  READ_SMS: false,
  POST_NOTIFICATIONS: false,
};

// Storage key for permission status
const PERMISSIONS_STORAGE_KEY = "@AutoSMS:PermissionsStatus";

// Get the native module
const { PermissionsManager } = NativeModules;

/**
 * Class that handles all permission-related operations
 */
class PermissionsService {
  /**
   * Check if a specific permission is granted
   * @param permission The permission to check
   * @returns Promise resolving to boolean indicating if permission is granted
   */
  async checkPermission(permission: PermissionType): Promise<boolean> {
    if (Platform.OS !== "android") {
      return false;
    }
    return await PermissionsManager.checkPermission(permission);
  }

  /**
   * Request a specific permission
   * @param permission The permission to request
   * @returns Promise resolving to boolean indicating if permission was granted
   */
  async requestPermission(permission: PermissionType): Promise<boolean> {
    if (Platform.OS !== "android") {
      return false;
    }
    const granted = await PermissionsManager.requestPermission(permission);
    await this.savePermissionStatus(permission, granted);
    return granted;
  }

  /**
   * Open app settings
   * @returns Promise resolving when settings are opened
   */
  async openSettings(): Promise<boolean> {
    if (Platform.OS !== "android") {
      return false;
    }
    return await PermissionsManager.openSettings();
  }

  /**
   * Save permission status to AsyncStorage
   * @param permission The permission to save
   * @param granted Whether the permission is granted
   */
  async savePermissionStatus(
    permission: PermissionType,
    granted: boolean
  ): Promise<void> {
    try {
      // Get current status
      const currentStatus = await this.getPermissionsStatus();

      // Update with new status
      const updatedStatus = {
        ...currentStatus,
        [permission]: granted,
      };

      // Save to AsyncStorage
      await AsyncStorage.setItem(
        PERMISSIONS_STORAGE_KEY,
        JSON.stringify(updatedStatus)
      );
    } catch (error) {
      console.error("Error saving permission status:", error);
    }
  }

  /**
   * Get all permissions status
   * @returns Promise resolving to PermissionsStatus object
   */
  async getPermissionsStatus(): Promise<PermissionsStatus> {
    try {
      const storedStatus = await AsyncStorage.getItem(PERMISSIONS_STORAGE_KEY);
      if (storedStatus) {
        return JSON.parse(storedStatus) as PermissionsStatus;
      }
      return DEFAULT_PERMISSIONS_STATUS;
    } catch (error) {
      console.error("Error getting permissions status:", error);
      return DEFAULT_PERMISSIONS_STATUS;
    }
  }

  /**
   * Refresh all permission statuses from the system and save to AsyncStorage
   * @returns Promise resolving to updated PermissionsStatus
   */
  async refreshPermissionsStatus(): Promise<PermissionsStatus> {
    if (Platform.OS !== "android") {
      return DEFAULT_PERMISSIONS_STATUS;
    }

    try {
      const updatedStatus: PermissionsStatus = {
        READ_CALL_LOG: await this.checkPermission("READ_CALL_LOG"),
        CALL_PHONE: await this.checkPermission("CALL_PHONE"),
        ANSWER_PHONE_CALLS: await this.checkPermission("ANSWER_PHONE_CALLS"),
        READ_CONTACTS: await this.checkPermission("READ_CONTACTS"),
        SEND_SMS: await this.checkPermission("SEND_SMS"),
        READ_SMS: await this.checkPermission("READ_SMS"),
        POST_NOTIFICATIONS: await this.checkPermission("POST_NOTIFICATIONS"),
      };

      // Save the updated status to AsyncStorage
      await AsyncStorage.setItem(
        PERMISSIONS_STORAGE_KEY,
        JSON.stringify(updatedStatus)
      );

      return updatedStatus;
    } catch (error) {
      console.error("Error refreshing permissions status:", error);
      return DEFAULT_PERMISSIONS_STATUS;
    }
  }
}

export default new PermissionsService();
