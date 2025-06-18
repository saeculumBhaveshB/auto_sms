# RCS SMS Reply Issue Fixes

This document outlines the issues and fixes implemented to resolve problems with RCS (Rich Communication Services) SMS replies not working in the Auto SMS app.

## Issues Identified

1. **Notification Listener Permission**

   - The RcsNotificationListener service wasn't properly registered as an enabled notification listener
   - No user notification or guidance was provided when permission was missing
   - No way to check permission status from the app

2. **Silent Failures in RCS Reply Actions**

   - When RCS reply actions failed, there was no robust fallback mechanism
   - The SmsSender class wasn't consistently used as a backup when RCS replies failed
   - Multiple failure points without proper error handling

3. **RCS-Specific SMS Handling**
   - RCS-enabled phone numbers require special handling for SMS replies
   - The app wasn't properly detecting when standard SMS approaches would fail with RCS numbers

## Implemented Fixes

### 1. Permission Handling

- Added `RcsPermissionHelper` to check and request notification listener permissions
- Created user-friendly notification that guides users to enable required permissions
- Added React Native module to expose permission checks to the app UI
- Implemented automatic permission status checking on app startup

### 2. Robust Fallback Mechanisms

- Enhanced `SmsSender` class with multiple fallback approaches:

  - Made `sendSmsViaIntent` public and improved its reliability
  - Added ACTION_VIEW intent as an additional fallback when ACTION_SENDTO fails
  - Improved phone number normalization for better compatibility

- Modified `RcsNotificationListener`:
  - Always attempt direct SMS sending before RCS reply action
  - Added multiple fallback paths if primary SMS or RCS methods fail
  - Enhanced error handling with detailed logging

### 3. Testing and Diagnostics

- Added `testRcsSmsReply` method to CallSmsModule for easy testing
- Enhanced `RcsTestHelper` with methods to reset state and set testing-friendly rate limits
- Improved logging throughout the RCS message handling flow

## How to Test

1. **Check Permissions**:

   ```javascript
   AutoSms.checkRcsPermissions().then((status) => console.log(status));
   ```

2. **Open Settings if Needed**:

   ```javascript
   if (!permissionsGranted) {
     AutoSms.openNotificationListenerSettings();
   }
   ```

3. **Test RCS Reply**:
   ```javascript
   AutoSms.testRcsSmsReply("+1234567890", "Test message");
   ```

## Troubleshooting

If RCS replies are still not working:

1. **Check Logs**: Look for errors with tags "RcsNotification", "SmsSender", or "RcsMessageHandler"
2. **Verify Permissions**: Ensure notification access is enabled in system settings
3. **Test Standard SMS**: Try sending a standard SMS to verify basic functionality
4. **Check Default SMS App**: Some RCS features work better when the app is set as the default SMS app

## Implementation Details

The fixes implement a multi-layered approach to ensure message delivery:

1. First attempt: Direct SMS via SmsSender (most reliable)
2. Second attempt: RCS reply action through notification
3. Fallbacks:
   - Alternative SMS via ACTION_SENDTO intent
   - Alternative SMS via ACTION_VIEW intent
   - Last resort direct SMS attempt

This ensures that even if the primary RCS reply mechanism fails, the app will still attempt to send a response through alternative channels.
