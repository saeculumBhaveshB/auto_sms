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

const { PermissionsManager } = NativeModules;

// Keys for storing permission status
const PERMISSION_STORAGE_KEY_PREFIX = "@AutoSMS:Permission:";
const NOTIFICATION_LISTENER_KEY = "@AutoSMS:NotificationListenerEnabled";
const NOTIFICATION_PERMISSION_KEY = "@AutoSMS:NotificationPermission";

// Define our required permissions
export type PermissionType =
  | "callLog"
  | "phoneState"
  | "sendSms"
  | "readSms"
  | "readContacts"
  | "autoReply"
  | "notificationListener"
  | "sensitiveBadges"
  | "notifications";

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
  {
    key: "notifications",
    name: "Notifications",
    description: "Required to show important app notifications",
    androidPermission: PERMISSIONS.ANDROID.POST_NOTIFICATIONS,
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
   * Check if notification listener service is enabled
   */
  async isNotificationListenerEnabled(): Promise<boolean> {
    try {
      if (Platform.OS !== "android") {
        return false;
      }

      // Check if native method is available
      if (PermissionsManager?.checkPermission) {
        const result = await PermissionsManager.checkPermission(
          "NOTIFICATION_LISTENER"
        );
        return result === true;
      }

      // Fallback to stored value if native method not available
      const storedValue = await AsyncStorage.getItem(NOTIFICATION_LISTENER_KEY);
      return storedValue === "true";
    } catch (error) {
      console.error("Error checking notification listener permission:", error);
      return false;
    }
  }

  /**
   * Request notification listener permission by opening system settings
   */
  async openNotificationListenerSettings(): Promise<boolean> {
    try {
      if (Platform.OS !== "android") {
        return false;
      }

      // Use native method if available
      if (PermissionsManager?.openNotificationListenerSettings) {
        await PermissionsManager.openNotificationListenerSettings();
        return true;
      } else {
        // Fallback to general settings
        await this.openSettings();
        return true;
      }
    } catch (error) {
      console.error("Error opening notification listener settings:", error);
      return false;
    }
  }

  /**
   * Check sensitive notifications permission (Android 15+)
   */
  async checkSensitiveNotificationsPermission(): Promise<boolean> {
    try {
      if (Platform.OS !== "android") {
        return false;
      }

      // Check if native method is available
      if (PermissionsManager?.checkPermission) {
        const result = await PermissionsManager.checkPermission(
          "RECEIVE_SENSITIVE_NOTIFICATIONS"
        );
        return result === true;
      }

      return false;
    } catch (error) {
      console.error(
        "Error checking sensitive notifications permission:",
        error
      );
      return false;
    }
  }

  /**
   * Check notification permission status (Android 13+)
   */
  async checkNotificationPermission(): Promise<boolean> {
    try {
      if (Platform.OS !== "android") {
        return false;
      }

      // For Android 13+ (API 33+), check POST_NOTIFICATIONS permission
      if (Platform.Version >= 33) {
        const result = await check(PERMISSIONS.ANDROID.POST_NOTIFICATIONS);
        const granted = result === RESULTS.GRANTED;

        // Store the result for future reference
        await AsyncStorage.setItem(
          NOTIFICATION_PERMISSION_KEY,
          granted ? "true" : "false"
        );

        return granted;
      }

      // For Android < 13, notifications are automatically granted
      return true;
    } catch (error) {
      console.error("Error checking notification permission:", error);

      // Fallback to stored value if available
      const storedValue = await AsyncStorage.getItem(
        NOTIFICATION_PERMISSION_KEY
      );
      return storedValue === "true";
    }
  }

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

    // Special handling for notification listener
    if (permissionType === "notificationListener") {
      const isEnabled = await this.isNotificationListenerEnabled();
      return isEnabled ? "granted" : "denied";
    }

    // Special handling for sensitive notifications
    if (permissionType === "sensitiveBadges") {
      const isEnabled = await this.checkSensitiveNotificationsPermission();
      return isEnabled ? "granted" : "denied";
    }

    // Special handling for notifications
    if (permissionType === "notifications") {
      const isEnabled = await this.checkNotificationPermission();
      return isEnabled ? "granted" : "denied";
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

    // Special handling for notification listener
    if (permissionType === "notificationListener") {
      await this.openNotificationListenerSettings();
      // We can't immediately know if the user enabled it, so return pending
      return "denied";
    }

    // Special handling for sensitive notifications
    if (permissionType === "sensitiveBadges") {
      if (PermissionsManager?.requestPermission) {
        try {
          const result = await PermissionsManager.requestPermission(
            "RECEIVE_SENSITIVE_NOTIFICATIONS"
          );
          return result ? "granted" : "denied";
        } catch (e) {
          console.error(
            "Error requesting sensitive notifications permission:",
            e
          );
          return "denied";
        }
      }
      return "denied";
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

      // Special handling for notifications permission
      if (permissionType === "notifications") {
        await AsyncStorage.setItem(
          NOTIFICATION_PERMISSION_KEY,
          status === "granted" ? "true" : "false"
        );
      }

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
      "notifications", // Add notifications to critical permissions
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
   * Request notification permission for Android 13+
   * This should be called at app launch
   */
  async requestNotificationPermissionAtLaunch(): Promise<boolean> {
    // Only needed for Android 13+ (API 33+)
    if (Platform.OS !== "android" || Platform.Version < 33) {
      return true;
    }

    try {
      // Check if we've already requested this permission
      const storedStatus = await AsyncStorage.getItem(
        NOTIFICATION_PERMISSION_KEY
      );
      if (storedStatus === "true") {
        return true;
      }

      // Request the permission
      const status = await this.requestPermission("notifications");
      return status === "granted";
    } catch (error) {
      console.error(
        "Error requesting notification permission at launch:",
        error
      );
      return false;
    }
  }

  /**
   * Refresh all permission statuses from the system and save to AsyncStorage
   */
  async refreshPermissionsStatus(): Promise<
    Record<PermissionType, PermissionStatus>
  > {
    if (Platform.OS !== "android") {
      return {} as Record<PermissionType, PermissionStatus>;
    }

    try {
      const updatedStatus: Record<PermissionType, PermissionStatus> =
        {} as Record<PermissionType, PermissionStatus>;

      for (const permission of REQUIRED_PERMISSIONS) {
        updatedStatus[permission.key] = await this.checkPermission(
          permission.key
        );
      }

      // Also check special permissions
      updatedStatus.notificationListener = await this.checkPermission(
        "notificationListener"
      );
      updatedStatus.sensitiveBadges = await this.checkPermission(
        "sensitiveBadges"
      );

      return updatedStatus;
    } catch (error) {
      console.error("Error refreshing permissions status:", error);
      return {} as Record<PermissionType, PermissionStatus>;
    }
  }
}

export default new PermissionsService();
