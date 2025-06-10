import React, { useEffect, useState } from "react";
import {
  NativeEventEmitter,
  NativeModules,
  Platform,
  Alert,
} from "react-native";
import PermissionsService from "../services/PermissionsService";

/**
 * Component that listens for default SMS handler status changes
 * and updates the PermissionsService.
 * This should be included in the app's root component.
 */
const DefaultSmsHandlerListener: React.FC = () => {
  const [isDefaultSmsHandler, setIsDefaultSmsHandler] = useState<
    boolean | null
  >(null);

  useEffect(() => {
    // Only run on Android
    if (Platform.OS !== "android") return;

    console.log(
      "🔔 DefaultSmsHandlerListener mounted - setting up event listener"
    );

    // Check initial default SMS handler status
    const checkInitialStatus = async () => {
      try {
        const isDefault = await PermissionsService.isDefaultSmsHandler();
        console.log(
          `📱 Initial default SMS handler status: ${isDefault ? "YES" : "NO"}`
        );
        setIsDefaultSmsHandler(isDefault);

        // If already default, ensure permissions are requested
        if (isDefault) {
          console.log(
            "🔐 Already default SMS handler, requesting SMS permissions"
          );
          await requestSmsPermissions();
        }
      } catch (error) {
        console.error(
          "❌ Error checking initial default SMS handler status:",
          error
        );
      }
    };

    // Run initial check
    checkInitialStatus();

    // Create event emitter for events from native module
    const eventEmitter = new NativeEventEmitter(NativeModules.CallSmsModule);

    // Listen for default SMS handler changed event
    console.log("🎧 Adding listener for defaultSmsHandlerChanged event");
    const subscription = eventEmitter.addListener(
      "defaultSmsHandlerChanged",
      async (event: { isDefault: boolean }) => {
        console.log(
          `📲 Default SMS handler status changed: ${
            event.isDefault ? "YES" : "NO"
          }`
        );
        setIsDefaultSmsHandler(event.isDefault);

        try {
          if (event.isDefault) {
            // We've become the default SMS handler, now request SMS permissions
            console.log(
              "✅ App is now the default SMS handler, requesting SMS permissions"
            );
            await requestSmsPermissions();
          } else if (isDefaultSmsHandler && !event.isDefault) {
            // We lost default SMS handler status
            console.log("⚠️ App is no longer the default SMS handler");
            Alert.alert(
              "Default SMS App Status Changed",
              "This app is no longer set as the default SMS app. Some features may not work properly.",
              [{ text: "OK" }]
            );
          }
        } catch (error) {
          console.error("❌ Error handling default SMS handler change:", error);
        }
      }
    );

    // Set up periodic check for default SMS handler status
    const checkInterval = setInterval(async () => {
      try {
        const isDefault = await PermissionsService.isDefaultSmsHandler();

        // Only update and handle if the status actually changed
        if (isDefault !== isDefaultSmsHandler) {
          console.log(
            `📱 Default SMS handler status check: ${
              isDefault ? "YES" : "NO"
            } (changed from ${isDefaultSmsHandler ? "YES" : "NO"})`
          );
          setIsDefaultSmsHandler(isDefault);

          if (isDefault && isDefaultSmsHandler === false) {
            // Status changed to default, request permissions
            console.log(
              "✅ App became default SMS handler (detected in interval check), requesting SMS permissions"
            );
            await requestSmsPermissions();
          }
        }
      } catch (error) {
        console.error("❌ Error in periodic default SMS handler check:", error);
      }
    }, 5000); // Check every 5 seconds

    // Clean up the event listener and interval on unmount
    return () => {
      console.log(
        "🛑 DefaultSmsHandlerListener unmounting - removing listener and interval"
      );
      subscription.remove();
      clearInterval(checkInterval);
    };
  }, [isDefaultSmsHandler]);

  /**
   * Helper function to request SMS permissions
   */
  const requestSmsPermissions = async () => {
    try {
      console.log(
        "🔐 Requesting SMS permissions (sendSms, readSms, autoReply)"
      );

      // Request each SMS-related permission
      const sendSmsStatus = await PermissionsService.requestPermission(
        "sendSms"
      );
      console.log(`📩 sendSms permission status: ${sendSmsStatus}`);

      const readSmsStatus = await PermissionsService.requestPermission(
        "readSms"
      );
      console.log(`📬 readSms permission status: ${readSmsStatus}`);

      const autoReplyStatus = await PermissionsService.requestPermission(
        "autoReply"
      );
      console.log(`🤖 autoReply permission status: ${autoReplyStatus}`);

      // Refresh all permissions status
      await PermissionsService.refreshPermissionsStatus();
      console.log("✅ Refreshed all permissions status");

      // Check if any permission was not granted
      if (
        sendSmsStatus !== "granted" ||
        readSmsStatus !== "granted" ||
        autoReplyStatus !== "granted"
      ) {
        console.warn("⚠️ Some SMS permissions were not granted");
        Alert.alert(
          "SMS Permissions Required",
          "Some SMS permissions were not granted. The app may not function properly without these permissions.",
          [{ text: "OK" }]
        );
      } else {
        console.log("✅ All SMS permissions were granted successfully");
      }
    } catch (error) {
      console.error("❌ Error requesting SMS permissions:", error);
    }
  };

  // This component doesn't render anything
  return null;
};

export default DefaultSmsHandlerListener;
