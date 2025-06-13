# SMS and RCS Message Logging Guide

This document explains how to view detailed logs for incoming SMS and RCS messages in the Auto-SMS application.

## Enhanced Logging Features

We've implemented enhanced logging for both SMS and RCS messages with the following improvements:

1. **Consistent Log Tags**: All SMS and RCS message logs use the `LOGTAG_SMS_DETAILS` and `LOGTAG_RCS_DETAILS` tags for easy filtering
2. **Detailed Message Information**: Logs include sender, message content, timestamp, and other relevant metadata
3. **Comprehensive RCS Detection**: Detailed logging of RCS message detection process and indicators
4. **Arrow Formatting**: All message details use the `↘️` arrow prefix for better readability

## Viewing Logs

### Method 1: Using the Log Filter Script

We've created a dedicated script to filter logs specifically for SMS and RCS messages:

1. Open a terminal in the project root directory
2. Run the script:
   ```bash
   ./android/scripts/log_filter.sh
   ```
3. The script will clear existing logs and display only SMS and RCS message details with color coding
4. Press `Ctrl+C` to exit when done

### Method 2: Using ADB Command Line

If you prefer using ADB directly:

```bash
# Clear existing logs first (optional)
adb logcat -c

# Filter for SMS details
adb logcat | grep "LOGTAG_SMS_DETAILS"

# Filter for RCS details
adb logcat | grep "LOGTAG_RCS_DETAILS"

# Filter for both SMS and RCS details
adb logcat | grep -E "LOGTAG_(SMS|RCS)_DETAILS"
```

### Method 3: Using Android Studio Logcat

1. Open Android Studio's Logcat window
2. Enter one of these filter patterns:
   - `LOGTAG_SMS_DETAILS` - Shows only SMS message details
   - `LOGTAG_RCS_DETAILS` - Shows only RCS message details
   - `LOGTAG_` - Shows both SMS and RCS message details

## Testing Logging Functionality

We've added a utility to test the logging functionality without requiring real SMS or RCS messages. This is useful for development and testing.

### Using the LogTester Utility from React Native

We've created a JavaScript utility class that you can use in your React Native code to generate test logs:

```javascript
import LogTester from "../utils/LogTester";

// Generate a test SMS log entry
LogTester.logTestSms("+1234567890", "This is a test SMS message")
  .then(() => console.log("Test SMS log generated successfully"))
  .catch((error) => console.error("Error generating test SMS log", error));

// Generate a test RCS log entry
LogTester.logTestRcs("John Doe", "This is a test RCS message")
  .then(() => console.log("Test RCS log generated successfully"))
  .catch((error) => console.error("Error generating test RCS log", error));

// Attempt to generate a real SMS broadcast (requires permissions)
LogTester.generateTestSms("+1234567890", "This is a simulated SMS message")
  .then(() => console.log("SMS broadcast sent successfully"))
  .catch((error) => console.error("Error sending SMS broadcast", error));
```

You can add these function calls to a button in your UI for easy testing:

```jsx
<Button
  title="Generate Test SMS Log"
  onPress={() => LogTester.logTestSms()}
/>

<Button
  title="Generate Test RCS Log"
  onPress={() => LogTester.logTestRcs()}
/>
```

## Example Log Output

### SMS Message Logs

```
LOGTAG_SMS_DETAILS: 📩📩📩 SMS MESSAGE DETAILS 📩📩📩
LOGTAG_SMS_DETAILS: ↘️ From: +1234567890
LOGTAG_SMS_DETAILS: ↘️ Message: Hello, how are you?
LOGTAG_SMS_DETAILS: ↘️ Timestamp: 1677481234567
LOGTAG_SMS_DETAILS: ↘️ Message Length: 18 characters
LOGTAG_SMS_DETAILS: ↘️ Display Originating Address: +1234567890
LOGTAG_SMS_DETAILS: ↘️ Message Class: UNKNOWN
LOGTAG_SMS_DETAILS: ↘️ Message ID: 0
LOGTAG_SMS_DETAILS: ↘️ Is from missed call number: true
```

### RCS Message Logs

```
LOGTAG_RCS_DETAILS: 📨📨📨 RCS MESSAGE DETAILS 📨📨📨
LOGTAG_RCS_DETAILS: ↘️ Sender: John Doe
LOGTAG_RCS_DETAILS: ↘️ Message: Hey, can we meet tomorrow?
LOGTAG_RCS_DETAILS: ↘️ Package: com.google.android.apps.messaging
LOGTAG_RCS_DETAILS: ↘️ Timestamp: 1677481345678
LOGTAG_RCS_DETAILS: ↘️ Actions count: 3
LOGTAG_RCS_DETAILS: ↘️ Notification ID: 123
LOGTAG_RCS_DETAILS: ↘️ Notification Key: 0|com.google.android.apps.messaging|123|null|10123
LOGTAG_RCS_DETAILS: ↘️ Is RCS: true
LOGTAG_RCS_DETAILS: ↘️ Conversation ID: 0|com.google.android.apps.messaging|123|null|10123
LOGTAG_RCS_DETAILS: ↘️ Auto-reply: I'm currently unavailable. I'll respond as soon as possible.
```

## Troubleshooting

If you're not seeing any logs:

1. Make sure the app is installed and running on your device
2. Ensure you have enabled the Notification Listener Service for RCS messages
3. Try sending a test SMS or RCS message to your device
4. Check if the Auto-SMS service is enabled in your app settings
5. Restart your device if the notification listener service isn't being triggered
