import React, { useEffect } from "react";
import { NativeEventEmitter, NativeModules } from "react-native";
import PermissionsService from "../services/PermissionsService";

/**
 * Component that listens for default SMS handler status changes
 * and updates the PermissionsService.
 * This should be included in the app's root component.
 */
const DefaultSmsHandlerListener: React.FC = () => {
  useEffect(() => {
    // Create event emitter for events from native module
    const eventEmitter = new NativeEventEmitter(NativeModules.CallSmsModule);

    // Listen for default SMS handler changed event
    const subscription = eventEmitter.addListener(
      "defaultSmsHandlerChanged",
      async (event: { isDefault: boolean }) => {
        console.log("Default SMS handler status changed:", event.isDefault);

        try {
          if (event.isDefault) {
            // We've become the default SMS handler, now request SMS permissions
            console.log(
              "App is now the default SMS handler, requesting SMS permissions"
            );

            // Request each SMS-related permission
            await PermissionsService.requestPermission("sendSms");
            await PermissionsService.requestPermission("readSms");
            await PermissionsService.requestPermission("autoReply");

            // Refresh all permissions status
            await PermissionsService.refreshPermissionsStatus();
          }
        } catch (error) {
          console.error("Error handling default SMS handler change:", error);
        }
      }
    );

    // Clean up the event listener on unmount
    return () => {
      subscription.remove();
    };
  }, []);

  // This component doesn't render anything
  return null;
};

export default DefaultSmsHandlerListener;
