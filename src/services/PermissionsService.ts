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

// Keys for storing permission status
const PERMISSION_STORAGE_KEY_PREFIX = "@AutoSMS:Permission:";

// Define our required permissions
export type PermissionType =
  | "callLog"
  | "phoneState"
  | "sendSms"
  | "readSms"
  | "readContacts"
  | "autoReply";

// Define permission info for UI
export interface PermissionInfo {
  key: PermissionType;
  name: string;
  description: string;
  androidPermission: Permission;
}

// All required permissions for the app
export const REQUIRED_PERMISSIONS: PermissionInfo[] = [
  {
    key: "callLog",
    name: "Call Log",
    description: "Required to detect missed calls",
    androidPermission: PERMISSIONS.ANDROID.READ_CALL_LOG,
  },
  {
    key: "phoneState",
    name: "Phone State",
    description: "Required to monitor incoming calls",
    androidPermission: PERMISSIONS.ANDROID.READ_PHONE_STATE,
  },
  {
    key: "sendSms",
    name: "Send SMS",
    description: "Required to send automatic SMS messages",
    androidPermission: PERMISSIONS.ANDROID.SEND_SMS,
  },
  {
    key: "readSms",
    name: "Read SMS",
    description: "Required to verify SMS status",
    androidPermission: PERMISSIONS.ANDROID.READ_SMS,
  },
  {
    key: "readContacts",
    name: "Read Contacts",
    description: "Required to display contact names",
    androidPermission: PERMISSIONS.ANDROID.READ_CONTACTS,
  },
  {
    key: "autoReply",
    name: "Auto Reply",
    description: "Required to automatically respond to incoming messages",
    androidPermission: PERMISSIONS.ANDROID.RECEIVE_SMS,
  },
];

// Define possible permission states
export type PermissionStatus =
  | "granted"
  | "denied"
  | "blocked"
  | "unavailable"
  | "limited"
  | "never_ask_again";

/**
 * Service to manage app permissions
 */
class PermissionsService {
  /**
   * Check a single permission status
   */
  async checkPermission(
    permissionType: PermissionType
  ): Promise<PermissionStatus> {
    // Only Android is supported
    if (Platform.OS !== "android") {
      return "unavailable";
    }

    const permissionInfo = REQUIRED_PERMISSIONS.find(
      (p) => p.key === permissionType
    );
    if (!permissionInfo) {
      throw new Error(`Unknown permission type: ${permissionType}`);
    }

    try {
      const result = await check(permissionInfo.androidPermission);
      return this.mapPermissionResult(result);
    } catch (error) {
      console.error(`Error checking permission ${permissionType}:`, error);
      return "unavailable";
    }
  }

  /**
   * Request a single permission
   */
  async requestPermission(
    permissionType: PermissionType
  ): Promise<PermissionStatus> {
    // Only Android is supported
    if (Platform.OS !== "android") {
      return "unavailable";
    }

    const permissionInfo = REQUIRED_PERMISSIONS.find(
      (p) => p.key === permissionType
    );
    if (!permissionInfo) {
      throw new Error(`Unknown permission type: ${permissionType}`);
    }

    try {
      const result = await request(permissionInfo.androidPermission);
      const status = this.mapPermissionResult(result);

      // Store the result
      await this.storePermissionStatus(permissionType, status);

      return status;
    } catch (error) {
      console.error(`Error requesting permission ${permissionType}:`, error);
      return "unavailable";
    }
  }

  /**
   * Check if all required permissions are granted
   */
  async areAllPermissionsGranted(): Promise<boolean> {
    const criticalPermissions: PermissionType[] = [
      "callLog",
      "phoneState",
      "sendSms",
      "readSms",
      "autoReply",
    ];

    for (const permissionType of criticalPermissions) {
      const status = await this.checkPermission(permissionType);
      if (status !== "granted") {
        return false;
      }
    }

    return true;
  }

  /**
   * Store permission status in AsyncStorage
   */
  private async storePermissionStatus(
    permissionType: PermissionType,
    status: PermissionStatus
  ): Promise<void> {
    try {
      const key = `${PERMISSION_STORAGE_KEY_PREFIX}${permissionType}`;
      await AsyncStorage.setItem(key, status);
    } catch (error) {
      console.error(
        `Error storing permission status for ${permissionType}:`,
        error
      );
    }
  }

  /**
   * Get stored permission status from AsyncStorage
   */
  async getStoredPermissionStatus(
    permissionType: PermissionType
  ): Promise<PermissionStatus | null> {
    try {
      const key = `${PERMISSION_STORAGE_KEY_PREFIX}${permissionType}`;
      const status = await AsyncStorage.getItem(key);
      return status as PermissionStatus | null;
    } catch (error) {
      console.error(
        `Error getting stored permission status for ${permissionType}:`,
        error
      );
      return null;
    }
  }

  /**
   * Map react-native-permissions result to our PermissionStatus type
   */
  private mapPermissionResult(result: string): PermissionStatus {
    switch (result) {
      case RESULTS.GRANTED:
        return "granted";
      case RESULTS.DENIED:
        return "denied";
      case RESULTS.BLOCKED:
        return "blocked";
      case RESULTS.UNAVAILABLE:
        return "unavailable";
      case RESULTS.LIMITED:
        return "limited";
      default:
        return "unavailable";
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
