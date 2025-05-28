import { NativeModules, PermissionsAndroid, Platform } from "react-native";

type Permission = keyof typeof PermissionsAndroid.PERMISSIONS;
type PermissionStatus = keyof typeof PermissionsAndroid.RESULTS;

class PermissionsManager {
  /**
   * Check if multiple permissions are granted
   * @param permissions Array of permissions to check
   * @returns Object with permission as key and boolean as value
   */
  async checkMultiplePermissions(
    permissions: string[]
  ): Promise<Record<string, boolean>> {
    if (Platform.OS !== "android") {
      return permissions.reduce(
        (acc, permission) => ({ ...acc, [permission]: false }),
        {}
      );
    }

    try {
      const results: Record<string, boolean> = {};

      for (const permission of permissions) {
        try {
          // @ts-ignore - Some custom permissions might not be in the type definitions
          const granted = await PermissionsAndroid.check(permission);
          results[permission] = granted;
        } catch (error) {
          console.warn(`Error checking permission ${permission}:`, error);
          results[permission] = false;
        }
      }

      return results;
    } catch (error) {
      console.error("Error checking permissions:", error);
      return permissions.reduce(
        (acc, permission) => ({ ...acc, [permission]: false }),
        {}
      );
    }
  }

  /**
   * Request multiple permissions
   * @param permissions Array of permissions to request
   * @returns Object with permission as key and boolean as value
   */
  async requestMultiplePermissions(
    permissions: string[]
  ): Promise<Record<string, boolean>> {
    if (Platform.OS !== "android") {
      return permissions.reduce(
        (acc, permission) => ({ ...acc, [permission]: false }),
        {}
      );
    }

    try {
      // For each permission, show a rationale if needed and request it
      const results: Record<string, boolean> = {};

      for (const permission of permissions) {
        try {
          // Get user friendly name for permission
          const permissionName = this.getPermissionFriendlyName(permission);

          // Request the permission directly
          // @ts-ignore - We need to support custom permissions
          const granted = await PermissionsAndroid.request(permission, {
            title: `${permissionName} Permission`,
            message: `Auto SMS needs access to your ${permissionName.toLowerCase()} to automatically respond to messages after missed calls.`,
            buttonPositive: "Grant Permission",
          });

          results[permission] = granted === PermissionsAndroid.RESULTS.GRANTED;
        } catch (error) {
          console.warn(`Error requesting permission ${permission}:`, error);
          results[permission] = false;
        }
      }

      return results;
    } catch (error) {
      console.error("Error requesting permissions:", error);
      return permissions.reduce(
        (acc, permission) => ({ ...acc, [permission]: false }),
        {}
      );
    }
  }

  /**
   * Get a user-friendly name for a permission
   */
  private getPermissionFriendlyName(permission: string): string {
    const permissionMap: Record<string, string> = {
      "android.permission.READ_PHONE_STATE": "Phone State",
      "android.permission.READ_CALL_LOG": "Call Log",
      "android.permission.SEND_SMS": "SMS",
      "android.permission.RECEIVE_SMS": "SMS",
      "android.permission.READ_CONTACTS": "Contacts",
    };

    return (
      permissionMap[permission] || permission.split(".").pop() || "Unknown"
    );
  }
}

export default PermissionsManager;
