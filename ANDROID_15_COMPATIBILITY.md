# Android 15+ SMS Compatibility

## Overview

Starting with Android 15 (API level 35), the `SEND_SMS` permission is no longer grantable to third-party applications that are not system apps or the default SMS handler. This means that any app attempting to use direct SMS APIs will crash with a `SecurityException`, even if the user grants the permission in Settings.

This document explains how our app handles SMS functionality across different Android versions.

## Implementation Details

### 1. Version Detection

Our app detects the Android version at runtime:

```typescript
// From PermissionsService.ts
export const isAndroid15OrHigher = (): boolean => {
  return Platform.OS === "android" && Platform.Version >= 35;
};
```

### 2. SMS Sending Approach

Based on the Android version, we use different methods for sending SMS:

#### For Android 6-14 (API 23-34)

- We request the `SEND_SMS` permission at runtime
- We use the direct SMS API via `SmsManager.sendTextMessage()` or the react-native-sms package

#### For Android 15+ (API 35+)

- We don't request the `SEND_SMS` permission (as it would be denied)
- We use an intent-based approach that opens the default SMS app with a pre-filled message:
  ```typescript
  // From SmsService.ts
  private async sendSmsViaIntent(phoneNumber: string, message: string): Promise<boolean> {
    try {
      const url = `sms:${phoneNumber}?body=${encodeURIComponent(message)}`;
      await Linking.openURL(url);
      return true;
    } catch (error) {
      console.error('Error opening SMS app:', error);
      return false;
    }
  }
  ```

### 3. User Experience

On Android 15+, the user will see the following differences:

1. The app will not request the `SEND_SMS` permission
2. When sending an SMS, the default SMS app will open with the pre-filled message
3. The user will need to tap "Send" in the SMS app to actually send the message
4. A notice component (`SmsBehaviorNotice`) explains this behavior change to users

### 4. Native Implementation

The native Kotlin code also adapts based on Android version:

```kotlin
// From CallLogCheckService.kt
private fun sendSmsForMissedCall(phoneNumber: String) {
    // ...
    // For Android 15+, use intent-based approach
    if (Build.VERSION.SDK_INT >= 35) {
        sendSmsViaIntent(phoneNumber, smsMessage)
    } else {
        // For older Android versions, use direct SMS API
        sendSmsDirectly(phoneNumber, smsMessage)
    }
    // ...
}
```

## How This Affects Users

1. **On Android 14 and earlier**: No change in behavior, the app works as before
2. **On Android 15+**:
   - SMS messages are sent through the default messaging app
   - Users will need to manually press "Send" in the messaging app
   - The app displays a notice explaining this change

## Technical Considerations

1. The app maintains a single codebase but adapts at runtime
2. We handle permissions differently based on Android version
3. We've implemented graceful degradation for SMS functionality on Android 15+

## References

- [Android Developer Documentation on SMS Permissions](https://developer.android.com/about/versions/15/behavior-changes-all#sms-permissions)
- [Android Developer Documentation on Intent-based SMS](https://developer.android.com/guide/components/intents-common#Messaging)
