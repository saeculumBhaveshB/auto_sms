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

const { PermissionsManager, RcsPermissions } = NativeModules;

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
  | "notificationPermission"
  | "sensitiveBadges";

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
    key: "notificationListener",
    name: "Notification Access",
    description: "Required for RCS message auto-replies",
    androidPermission: PERMISSIONS.ANDROID.READ_PHONE_STATE,
  },
  {
    key: "notificationPermission",
    name: "Notification Permission",
    description: "Required to send notifications for RCS messages",
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

      // Use RcsPermissions module if available
      if (RcsPermissions?.isNotificationListenerEnabled) {
        try {
          return await RcsPermissions.isNotificationListenerEnabled();
        } catch (e) {
          console.error("Error using RcsPermissions:", e);
        }
      }

      // Fall back to PermissionsManager
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
   * Check if notification listener is enabled directly from system settings
   * This is more reliable than the standard check
   */
  async isNotificationListenerEnabledDirect(): Promise<boolean> {
    try {
      if (Platform.OS !== "android") {
        return false;
      }

      // Use direct check method if available
      if (RcsPermissions?.isNotificationListenerEnabledDirect) {
        try {
          return await RcsPermissions.isNotificationListenerEnabledDirect();
        } catch (e) {
          console.error("Error using RcsPermissions direct check:", e);
        }
      }

      // Fall back to standard check
      return this.isNotificationListenerEnabled();
    } catch (error) {
      console.error(
        "Error checking notification listener permission directly:",
        error
      );
      return false;
    }
  }

  /**
   * Check if notification permission is granted (for Android 13+)
   */
  async isNotificationPermissionGranted(): Promise<boolean> {
    try {
      if (Platform.OS !== "android") {
        return false;
      }

      // Use RcsPermissions module if available
      if (RcsPermissions?.isNotificationPermissionGranted) {
        try {
          return await RcsPermissions.isNotificationPermissionGranted();
        } catch (e) {
          console.error("Error using RcsPermissions:", e);
        }
      }

      // Fall back to standard permission check for Android 13+
      if (Platform.Version >= 33) {
        const result = await check(PERMISSIONS.ANDROID.POST_NOTIFICATIONS);
        return result === RESULTS.GRANTED;
      }

      // On older Android versions, notification permission is implicitly granted
      return true;
    } catch (error) {
      console.error("Error checking notification permission:", error);
      return false;
    }
  }

  /**
   * Request notification permission (for Android 13+)
   */
  async requestNotificationPermission(): Promise<boolean> {
    try {
      if (Platform.OS !== "android") {
        return false;
      }

      // Use RcsPermissions module if available
      if (RcsPermissions?.requestNotificationPermission) {
        try {
          return await RcsPermissions.requestNotificationPermission();
        } catch (e) {
          console.error("Error using RcsPermissions:", e);
        }
      }

      // Fall back to standard permission request for Android 13+
      if (Platform.Version >= 33) {
        const result = await request(PERMISSIONS.ANDROID.POST_NOTIFICATIONS);
        return result === RESULTS.GRANTED;
      }

      // On older Android versions, notification permission is implicitly granted
      return true;
    } catch (error) {
      console.error("Error requesting notification permission:", error);
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

      // Use RcsPermissions module if available
      if (RcsPermissions?.openNotificationListenerSettings) {
        try {
          await RcsPermissions.openNotificationListenerSettings();

          // Start polling for permission status
          this.pollNotificationListenerStatus();

          return true;
        } catch (e) {
          console.error("Error using RcsPermissions:", e);
        }
      }

      // Use native method if available
      if (PermissionsManager?.openNotificationListenerSettings) {
        await PermissionsManager.openNotificationListenerSettings();

        // Start polling for permission status
        this.pollNotificationListenerStatus();

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
   * Poll for notification listener status changes
   * This helps detect when the user enables the permission in settings
   */
  private async pollNotificationListenerStatus(
    attempts = 0,
    maxAttempts = 10
  ): Promise<void> {
    // Stop after max attempts
    if (attempts >= maxAttempts) return;

    // Wait 1 second between checks
    await new Promise((resolve) => setTimeout(resolve, 1000));

    try {
      // Check if permission is granted
      const isEnabled = await this.isNotificationListenerEnabledDirect();

      if (isEnabled) {
        // Permission granted, store it
        await AsyncStorage.setItem(NOTIFICATION_LISTENER_KEY, "true");
        console.log("Notification listener permission granted during polling");
        return;
      }

      // Try again
      this.pollNotificationListenerStatus(attempts + 1, maxAttempts);
    } catch (error) {
      console.error("Error polling notification listener status:", error);
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
      // Try direct check first for more reliability
      try {
        const isEnabledDirect =
          await this.isNotificationListenerEnabledDirect();
        if (isEnabledDirect) {
          return "granted";
        }
      } catch (e) {
        console.error("Error in direct notification listener check:", e);
      }

      // Fall back to standard check
      const isEnabled = await this.isNotificationListenerEnabled();
      return isEnabled ? "granted" : "denied";
    }

    // Special handling for notification permission
    if (permissionType === "notificationPermission") {
      const isEnabled = await this.isNotificationPermissionGranted();
      return isEnabled ? "granted" : "denied";
    }

    // Special handling for sensitive notifications
    if (permissionType === "sensitiveBadges") {
      const isEnabled = await this.checkSensitiveNotificationsPermission();
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

    // Special handling for notification permission
    if (permissionType === "notificationPermission") {
      const granted = await this.requestNotificationPermission();
      return granted ? "granted" : "denied";
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
      const storedValue = await AsyncStorage.getItem(key);
      return storedValue as PermissionStatus | null;
    } catch (error) {
      console.error(
        `Error retrieving stored permission status for ${permissionType}:`,
        error
      );
      return null;
    }
  }

  /**
   * Map permission result to permission status
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
   * Open system settings
   */
  async openSettings(): Promise<boolean> {
    try {
      if (Platform.OS === "android") {
        await openSettings();
        return true;
      }
      return false;
    } catch (error) {
      console.error("Error opening system settings:", error);
      return false;
    }
  }

  /**
   * Refresh all permission statuses from the system and save to AsyncStorage
   * @returns Promise resolving to updated permissions status
   */
  async refreshPermissionsStatus(): Promise<Record<string, PermissionStatus>> {
    if (Platform.OS !== "android") {
      return {};
    }

    try {
      const updatedStatus: Record<string, PermissionStatus> = {};

      // Check all required permissions
      for (const permission of REQUIRED_PERMISSIONS) {
        updatedStatus[permission.key] = await this.checkPermission(
          permission.key
        );
      }

      return updatedStatus;
    } catch (error) {
      console.error("Error refreshing permissions status:", error);
      return {};
    }
  }
}

export default new PermissionsService();
