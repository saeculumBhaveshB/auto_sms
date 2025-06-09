import { Platform, NativeModules } from "react-native";
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

// Define interface for permissions status
export interface PermissionsStatus {
  [key: string]: PermissionStatus;
}

// Default permissions status
export const DEFAULT_PERMISSIONS_STATUS: PermissionsStatus = {
  callLog: "unavailable",
  phoneState: "unavailable",
  sendSms: "unavailable",
  readSms: "unavailable",
  readContacts: "unavailable",
  autoReply: "unavailable",
};

// Storage key for permissions
const PERMISSIONS_STORAGE_KEY = "@AutoSMS:PermissionsStatus";

// Android 15 (API level 35) detection
export const isAndroid15OrHigher = (): boolean => {
  return Platform.OS === "android" && Platform.Version >= 35;
};

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

    // For Android 15+, special handling for SMS-related permissions
    if (isAndroid15OrHigher()) {
      if (
        permissionType === "sendSms" ||
        permissionType === "readSms" ||
        permissionType === "autoReply"
      ) {
        // For SMS permissions on Android 15+, we need to check if we're the default SMS handler
        try {
          const isSmsHandler = await this.isDefaultSmsHandler();
          if (!isSmsHandler) {
            return "unavailable";
          }
        } catch (error) {
          console.error("Error checking if default SMS handler:", error);
          return "unavailable";
        }
      }
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

    // For Android 15+, special handling for SMS-related permissions
    if (isAndroid15OrHigher()) {
      if (
        permissionType === "sendSms" ||
        permissionType === "readSms" ||
        permissionType === "autoReply"
      ) {
        // For SMS permissions on Android 15+, we need to be the default SMS handler
        try {
          const isSmsHandler = await this.isDefaultSmsHandler();
          if (!isSmsHandler) {
            // Request to become default SMS handler
            const becameDefault = await this.requestDefaultSmsHandler();
            if (!becameDefault) {
              return "unavailable";
            }
            // Give the system more time to process the change - increased from 500ms to 2000ms
            await new Promise((resolve) => setTimeout(resolve, 2000));
          }
        } catch (error) {
          console.error("Error handling default SMS handler:", error);
          return "unavailable";
        }
      }
    }

    const permissionInfo = REQUIRED_PERMISSIONS.find(
      (p) => p.key === permissionType
    );
    if (!permissionInfo) {
      throw new Error(`Unknown permission type: ${permissionType}`);
    }

    try {
      // For SMS permissions on Android 15+, special handling to avoid crashes
      let result;
      if (
        isAndroid15OrHigher() &&
        (permissionType === "sendSms" ||
          permissionType === "readSms" ||
          permissionType === "autoReply")
      ) {
        try {
          // Double-check we're still the default SMS handler
          const isStillDefault = await this.isDefaultSmsHandler();
          if (!isStillDefault) {
            console.warn(
              "Lost default SMS handler status before requesting permission"
            );
            return "unavailable";
          }

          // Try to request the permission with error handling
          result = await request(permissionInfo.androidPermission);
        } catch (error) {
          console.error(
            `Error requesting SMS permission on Android 15+: ${error}`
          );
          return "unavailable";
        }
      } else {
        result = await request(permissionInfo.androidPermission);
      }

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
   * Check if SMS permission is available based on Android version
   */
  isSmsPermissionAvailable(): boolean {
    return Platform.OS === "android" && !isAndroid15OrHigher();
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
      const updatedStatus: PermissionsStatus = {};

      // Check each permission
      for (const permission of REQUIRED_PERMISSIONS) {
        updatedStatus[permission.key] = await this.checkPermission(
          permission.key
        );
      }

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

  /**
   * Check if app is default SMS handler
   */
  async isDefaultSmsHandler(): Promise<boolean> {
    if (Platform.OS !== "android") {
      return false;
    }

    try {
      return await NativeModules.CallSmsModule.isDefaultSmsHandler();
    } catch (error) {
      console.error("Error checking default SMS handler:", error);
      return false;
    }
  }

  /**
   * Request to become default SMS handler
   */
  async requestDefaultSmsHandler(): Promise<boolean> {
    if (Platform.OS !== "android") {
      return false;
    }

    try {
      // Check if we're already the default SMS handler
      const isDefault = await this.isDefaultSmsHandler();
      if (isDefault) {
        console.log("App is already the default SMS handler");
        return true;
      }

      console.log("Requesting to become default SMS handler");

      // Request to become default SMS handler
      const result =
        await NativeModules.CallSmsModule.requestDefaultSmsHandler();
      console.log("Default SMS handler request initiated:", result);

      // Give the system time to process the change before checking
      // This delay needs to be long enough for the user to see and interact with the system dialog
      return new Promise((resolve) => {
        // Check status after a delay to allow user interaction
        setTimeout(async () => {
          try {
            // Check if we've become the default SMS handler
            const checkResult = await this.isDefaultSmsHandler();
            console.log("Result of default SMS handler check:", checkResult);
            resolve(checkResult);
          } catch (error) {
            console.error(
              "Error checking if app became default SMS handler:",
              error
            );
            resolve(false);
          }
        }, 5000); // Give more time for user to interact with the system dialog
      });
    } catch (error) {
      console.error("Error requesting to become default SMS handler:", error);
      return false;
    }
  }
}

export default new PermissionsService();
