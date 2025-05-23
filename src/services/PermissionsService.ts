import { Platform } from "react-native";
import AsyncStorage from "@react-native-async-storage/async-storage";
import {
  PERMISSIONS,
  RESULTS,
  check,
  request,
  openSettings,
  Permission,
} from "react-native-permissions";

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

// Map our permission types to react-native-permissions permission types
const permissionMapping: Record<
  string,
  Record<PermissionType, Permission | string>
> = {
  android: {
    READ_CALL_LOG: PERMISSIONS.ANDROID.READ_CALL_LOG,
    CALL_PHONE: PERMISSIONS.ANDROID.CALL_PHONE,
    ANSWER_PHONE_CALLS: PERMISSIONS.ANDROID.ANSWER_PHONE_CALLS,
    READ_CONTACTS: PERMISSIONS.ANDROID.READ_CONTACTS,
    SEND_SMS: PERMISSIONS.ANDROID.SEND_SMS,
    READ_SMS: PERMISSIONS.ANDROID.READ_SMS,
    POST_NOTIFICATIONS: "android.permission.POST_NOTIFICATIONS", // Add manually if not available
  },
  ios: {
    // iOS doesn't have the same permissions, but we'll define placeholders
    READ_CALL_LOG: "unsupported",
    CALL_PHONE: "unsupported",
    ANSWER_PHONE_CALLS: "unsupported",
    READ_CONTACTS: PERMISSIONS.IOS.CONTACTS,
    SEND_SMS: "unsupported",
    READ_SMS: "unsupported",
    POST_NOTIFICATIONS: "ios.permission.NOTIFICATIONS", // Add manually if not available
  },
  default: {
    READ_CALL_LOG: "unsupported",
    CALL_PHONE: "unsupported",
    ANSWER_PHONE_CALLS: "unsupported",
    READ_CONTACTS: "unsupported",
    SEND_SMS: "unsupported",
    READ_SMS: "unsupported",
    POST_NOTIFICATIONS: "unsupported",
  },
};

// Get the correct permission mapping based on platform
const currentMapping =
  Platform.select(permissionMapping) || permissionMapping.default;

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
    if (
      Platform.OS !== "android" ||
      currentMapping[permission] === "unsupported"
    ) {
      return false;
    }

    try {
      const result = await check(currentMapping[permission] as Permission);
      return result === RESULTS.GRANTED;
    } catch (error) {
      console.error(`Error checking permission ${permission}:`, error);
      return false;
    }
  }

  /**
   * Request a specific permission
   * @param permission The permission to request
   * @returns Promise resolving to boolean indicating if permission was granted
   */
  async requestPermission(permission: PermissionType): Promise<boolean> {
    if (
      Platform.OS !== "android" ||
      currentMapping[permission] === "unsupported"
    ) {
      return false;
    }

    try {
      const result = await request(currentMapping[permission] as Permission);
      const granted = result === RESULTS.GRANTED;

      // Save the status to AsyncStorage
      await this.savePermissionStatus(permission, granted);

      return granted;
    } catch (error) {
      console.error(`Error requesting permission ${permission}:`, error);
      return false;
    }
  }

  /**
   * Open app settings
   * @returns Promise resolving when settings are opened
   */
  async openSettings(): Promise<boolean> {
    try {
      await openSettings();
      return true;
    } catch (error) {
      console.error("Error opening settings:", error);
      return false;
    }
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
