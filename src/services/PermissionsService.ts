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
import { NativeEventEmitter } from "react-native";
import { DeviceEventEmitter } from "react-native";
import { Alert } from "react-native";

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

      // Create a promise that will be resolved when the defaultSmsHandlerChanged event fires
      const resultPromise = new Promise<boolean>((resolve, reject) => {
        // Set up listener for the defaultSmsHandlerChanged event
        const eventListener = DeviceEventEmitter.addListener(
          "defaultSmsHandlerChanged",
          (event) => {
            console.log("Default SMS handler changed event received:", event);
            if (event && typeof event.isDefault === "boolean") {
              // Clear the timeout since we got a response
              if (timeoutId) {
                clearTimeout(timeoutId);
              }

              // Clean up the listener
              eventListener.remove();
              failureListener.remove();
              successListener.remove();
              settingsListener.remove();

              // Resolve with the result
              resolve(event.isDefault);
            }
          }
        );

        // Also listen for the success event which is sent when we successfully become the default
        const successListener = DeviceEventEmitter.addListener(
          "defaultSmsHandlerSet",
          () => {
            console.log("Default SMS handler successfully set");
            if (timeoutId) {
              clearTimeout(timeoutId);
            }

            successListener.remove();
            eventListener.remove();
            failureListener.remove();
            settingsListener.remove();

            // Double-check that we're really the default
            setTimeout(async () => {
              const isReallyDefault = await this.isDefaultSmsHandler();
              resolve(isReallyDefault);
            }, 1000);
          }
        );

        // Set up listener for the failure event
        const failureListener = DeviceEventEmitter.addListener(
          "defaultSmsHandlerRequestFailed",
          (error) => {
            console.log("Default SMS handler request failed:", error);
            if (timeoutId) {
              clearTimeout(timeoutId);
            }

            // Clean up listeners
            eventListener.remove();
            successListener.remove();
            failureListener.remove();
            settingsListener.remove();

            // Provide better error message for troubleshooting
            const errorMsg =
              typeof error === "string"
                ? error
                : error?.error || "Failed to set as default SMS handler";

            console.error(
              `SMS default handler error details: ${JSON.stringify(error)}`
            );
            reject(new Error(errorMsg));
          }
        );

        // New listener for the settings event - when direct settings are opened
        const settingsListener = DeviceEventEmitter.addListener(
          "defaultSmsHandlerSettings",
          (event) => {
            console.log("Default SMS handler settings opened:", event);

            // This is fired when direct settings are opened
            // Show a modal to guide the user through the manual settings
            if (event?.status === "settingsOpened") {
              Alert.alert(
                "Set Default SMS App",
                "Please follow these steps in the Settings app:\n\n" +
                  "1. Tap on 'Default apps' or 'SMS app'\n" +
                  "2. Select this app from the list\n" +
                  "3. Return to this app when done\n\n" +
                  "This app will check if you've successfully set it as default when you return.",
                [{ text: "OK" }]
              );

              // Start a polling mechanism to check if we've become the default SMS app
              // This will run when the app regains focus
              const checkInterval = setInterval(async () => {
                const checkResult = await this.isDefaultSmsHandler();
                console.log("Polling default SMS status:", checkResult);

                if (checkResult) {
                  // We've become the default SMS handler!
                  clearInterval(checkInterval);

                  // Clear the timeout
                  if (timeoutId) {
                    clearTimeout(timeoutId);
                  }

                  // Clean up listeners
                  eventListener.remove();
                  successListener.remove();
                  failureListener.remove();
                  settingsListener.remove();

                  // Resolve the promise
                  resolve(true);
                }
              }, 2000); // Check every 2 seconds

              // Set a timeout to clear the interval if it runs too long
              setTimeout(() => {
                clearInterval(checkInterval);
                // Don't resolve or reject here - the main timeout will handle that
              }, 58000); // Stop polling 2 seconds before the main timeout
            }
          }
        );

        // Set a timeout in case we never get a response
        // Increased from 30 seconds to 60 seconds
        const timeoutId = setTimeout(() => {
          console.log("Timeout waiting for default SMS handler change event");

          // Clean up listeners
          eventListener.remove();
          successListener.remove();
          failureListener.remove();
          settingsListener.remove();

          // Try one last check before rejecting
          this.isDefaultSmsHandler().then((isDefault) => {
            if (isDefault) {
              console.log(
                "Final check shows we are the default SMS handler despite timeout"
              );
              resolve(true);
            } else {
              // Reject with timeout error
              reject(
                new Error(
                  "Timeout waiting for default SMS handler change. Please try again and make sure to select this app when prompted."
                )
              );
            }
          });
        }, 60000); // 60 second timeout
      });

      // Request to become default SMS handler
      await NativeModules.CallSmsModule.requestDefaultSmsHandler();
      console.log("Default SMS handler request initiated");

      // Wait for the result or timeout
      return await resultPromise;
    } catch (error) {
      console.error("Error requesting default SMS handler:", error);
      // Check one more time in case the error was in the event system but the permission was granted
      const finalCheck = await this.isDefaultSmsHandler();
      if (finalCheck) {
        return true;
      }
      return false;
    }
  }
}

export default new PermissionsService();
