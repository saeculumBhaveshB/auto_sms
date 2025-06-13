# SMS and RCS Message Logging Implementation Summary

## Overview

This document summarizes the changes made to implement enhanced logging for SMS and RCS messages in the Auto-SMS application. These changes enable developers to easily view and filter incoming message details in logcat for debugging and testing purposes.

## Implementation Details

### Enhanced Logging Tags

We added consistent log tags for all SMS and RCS message logs to make filtering easier:

- `LOGTAG_SMS_DETAILS` for SMS messages
- `LOGTAG_RCS_DETAILS` for RCS messages

### SmsReceiver.kt Changes

1. Enhanced the logging in the `onReceive` method to include detailed information about incoming SMS messages
2. Added additional metadata fields to the logs:
   - Display originating address
   - Message class
   - Message ID
   - Is from missed call number
3. Ensured all logs use the `LOGTAG_SMS_DETAILS` tag for easy filtering

### RcsNotificationListener.kt Changes

1. Enhanced the logging in the `onNotificationPosted` method to include detailed RCS message information
2. Added comprehensive logging to the `isRcsMessage` method to show all notification data, extras, and actions
3. Added detailed logging for RCS message indicators and detection process
4. Ensured all logs use the `LOGTAG_RCS_DETAILS` tag for easy filtering

### Utility Script (log_filter.sh)

Created a bash script that uses `adb logcat` to filter and colorize SMS and RCS logs:

- Green for SMS logs
- Blue for RCS logs
- Automatically clears previous logs
- Easy to run from the terminal

### LogTestModule

Created a new native module to test logging functionality:

1. `generateTestSms` - Simulates an incoming SMS by sending a broadcast intent
2. `logTestSms` - Outputs SMS log entries directly to logcat
3. `logTestRcs` - Outputs RCS log entries directly to logcat

### React Native Integration

1. Created `LoggingPackage.kt` to register the LogTestModule with React Native
2. Added the package to `MainApplication.kt`
3. Created a JavaScript utility (`LogTester.js`) for easy testing from React Native code

### Documentation

1. Created `SMS_RCS_LOGGING.md` with detailed instructions on how to view logs
2. Added examples of log output and troubleshooting tips
3. Added instructions for using the LogTester utility

## Files Modified

1. `android/app/src/main/java/com/auto_sms/callsms/SmsReceiver.kt`
2. `android/app/src/main/java/com/auto_sms/callsms/RcsNotificationListener.kt`
3. `android/app/src/main/java/com/auto_sms/callsms/RcsAutoReplyManager.kt`
4. `android/app/src/main/java/com/auto_sms/MainApplication.kt`

## Files Created

1. `android/scripts/log_filter.sh`
2. `android/app/src/main/java/com/auto_sms/callsms/LogTestModule.kt`
3. `android/app/src/main/java/com/auto_sms/callsms/LoggingPackage.kt`
4. `src/utils/LogTester.js`
5. `SMS_RCS_LOGGING.md`
6. `LOGGING_IMPLEMENTATION_SUMMARY.md`

## How to Use

1. Run the log filter script: `./android/scripts/log_filter.sh`
2. Or filter manually with: `adb logcat | grep -E "LOGTAG_(SMS|RCS)_DETAILS"`
3. Generate test logs using the LogTester utility from React Native code
4. See the full documentation in `SMS_RCS_LOGGING.md`
