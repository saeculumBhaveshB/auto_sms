// Test script for SMS/RCS logging
const { NativeModules } = require("react-native");

try {
  const { LogTestModule } = NativeModules;

  if (LogTestModule) {
    // Generate test SMS log
    LogTestModule.logTestSms("+1234567890", "This is a test SMS message");
    console.log("Test SMS log generated");

    // Generate test RCS log
    LogTestModule.logTestRcs("John Doe", "This is a test RCS message");
    console.log("Test RCS log generated");
  } else {
    console.error("LogTestModule not found");
  }
} catch (e) {
  console.error("Error:", e);
}
