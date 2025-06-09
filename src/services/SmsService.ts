import { Platform, Linking, NativeModules } from "react-native";
import { isAndroid15OrHigher } from "./PermissionsService";

/**
 * Service to handle SMS functionality with Android 15+ compatibility
 */
class SmsService {
  /**
   * Send SMS using appropriate method based on Android version
   * @param phoneNumber - The phone number to send SMS to
   * @param message - The message content
   * @returns Promise resolving to success status
   */
  async sendSms(phoneNumber: string, message: string): Promise<boolean> {
    try {
      if (Platform.OS !== "android") {
        console.warn("SMS functionality is only supported on Android");
        return false;
      }

      // For Android 15+, use different approach
      if (isAndroid15OrHigher()) {
        // First check if we're the default SMS handler
        const isDefaultHandler = await this.isDefaultSmsHandler();

        if (isDefaultHandler) {
          // If we're the default SMS handler, we can use the native module
          return await NativeModules.CallSmsModule.sendSms(
            phoneNumber,
            message
          );
        } else {
          // Otherwise, use intent-based approach
          return await this.sendSmsViaIntent(phoneNumber, message);
        }
      } else {
        // For pre-Android 15, use direct SMS API via native module
        return await NativeModules.CallSmsModule.sendSms(phoneNumber, message);
      }
    } catch (error) {
      console.error("Error sending SMS:", error);
      return false;
    }
  }

  /**
   * Send SMS via intent (system SMS app)
   * @param phoneNumber - The phone number to send SMS to
   * @param message - The message content
   * @returns Promise resolving to success status
   */
  private async sendSmsViaIntent(
    phoneNumber: string,
    message: string
  ): Promise<boolean> {
    try {
      const url = `sms:${phoneNumber}?body=${encodeURIComponent(message)}`;
      const canOpen = await Linking.canOpenURL(url);

      if (canOpen) {
        await Linking.openURL(url);
        return true;
      } else {
        console.warn("No app can handle SMS URI");
        return false;
      }
    } catch (error) {
      console.error("Error sending SMS via intent:", error);
      return false;
    }
  }

  /**
   * Check if the app is the default SMS handler
   * @returns Promise resolving to true if app is default SMS handler
   */
  async isDefaultSmsHandler(): Promise<boolean> {
    if (Platform.OS !== "android") {
      return false;
    }

    try {
      // Use our native module to check default SMS handler status
      return await NativeModules.CallSmsModule.isDefaultSmsHandler();
    } catch (error) {
      console.error("Error checking default SMS handler:", error);
      return false;
    }
  }

  /**
   * Request to become the default SMS handler
   * @returns Promise resolving to true if request was initiated successfully
   */
  async requestDefaultSmsHandler(): Promise<boolean> {
    if (Platform.OS !== "android") {
      return false;
    }

    try {
      // Check if we're already the default SMS handler
      const isDefault = await this.isDefaultSmsHandler();
      if (isDefault) {
        return true;
      }

      // Use native module to request becoming default SMS handler
      return await NativeModules.CallSmsModule.requestDefaultSmsHandler();
    } catch (error) {
      console.error("Error requesting default SMS handler:", error);
      return false;
    }
  }
}

export default new SmsService();
