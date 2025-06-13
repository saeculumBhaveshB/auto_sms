import { NativeModules } from "react-native";
const { LogTestModule } = NativeModules;

/**
 * Utility for testing SMS and RCS message logging functionality
 */
const LogTester = {
  /**
   * Generate a test SMS to verify logging functionality
   * @param {string} senderNumber - The phone number of the sender
   * @param {string} message - The message content
   * @returns {Promise<boolean>} - Promise that resolves to true if successful
   */
  generateTestSms: (
    senderNumber = "+1234567890",
    message = "This is a test SMS message"
  ) => {
    return LogTestModule.generateTestSms(senderNumber, message);
  },

  /**
   * Output a test SMS log directly to logcat for testing
   * @param {string} senderNumber - The phone number of the sender
   * @param {string} message - The message content
   * @returns {Promise<boolean>} - Promise that resolves to true if successful
   */
  logTestSms: (
    senderNumber = "+1234567890",
    message = "This is a test SMS log entry"
  ) => {
    return LogTestModule.logTestSms(senderNumber, message);
  },

  /**
   * Output a test RCS log directly to logcat for testing
   * @param {string} sender - The name of the sender
   * @param {string} message - The message content
   * @returns {Promise<boolean>} - Promise that resolves to true if successful
   */
  logTestRcs: (sender = "John Doe", message = "This is a test RCS message") => {
    return LogTestModule.logTestRcs(sender, message);
  },
};

export default LogTester;
