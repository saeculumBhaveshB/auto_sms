# Android 15+ SMS Handling

## Problem Overview

Starting with Android 15 (API 35), the `SEND_SMS` permission is no longer grantable to third-party apps. Any attempt to use direct SMS APIs will result in a `SecurityException` and app crash. This is a platform-level restriction, not a policy change.

## Solution Implemented

We've implemented a comprehensive solution that:

1. **Detects Android 15+** using platform version detection
2. **Maintains backwards compatibility** for Android 6-14 using direct SMS APIs
3. **Uses intent-based approach** for Android 15+ to open the default SMS app
4. **Conditionally includes permissions** in the AndroidManifest based on build variant
5. **Provides clear user feedback** about the different behavior on Android 15+

## Implementation Details

### 1. Platform Detection

```typescript
// In PermissionsService.ts
export const isAndroid15OrHigher = (): boolean => {
  return Platform.OS === "android" && Platform.Version >= 35;
};
```

### 2. SMS Service

We've created a new `SmsService` that intelligently chooses between direct SMS and intent-based approaches:

```typescript
// In SmsService.ts
async sendSms(phoneNumber: string, message: string): Promise<boolean> {
  // For Android 15+, use intent-based approach
  if (isAndroid15OrHigher()) {
    return await this.sendSmsViaIntent(phoneNumber, message);
  }

  // For older versions, use direct SMS with permission check
  // ...
}
```

### 3. Build Variants

We've configured the build system to create two variants:

- `legacy` - For Android 6-14, includes SEND_SMS permission
- `modern` - For Android 15+, removes SEND_SMS permission

```gradle
// In build.gradle
flavorDimensions "api"
productFlavors {
    legacy {
        dimension "api"
        manifestPlaceholders = [includeSmsPermission: true]
    }
    modern {
        dimension "api"
        manifestPlaceholders = [includeSmsPermission: false]
    }
}
```

### 4. User Interface

We've added a notification component that explains the SMS behavior difference on Android 15+:

```typescript
// In SmsBehaviorNotice.tsx
const SmsBehaviorNotice = () => {
  if (Platform.OS !== "android" || !isAndroid15OrHigher()) {
    return null;
  }

  return (
    <View>
      <Text>SMS will be sent through your default messaging app</Text>
    </View>
  );
};
```

## Build & Distribution

### Building for Different Android Versions

```bash
# For Android 6-14 (API 23-34)
./gradlew assembleReleaseLegacy

# For Android 15+ (API 35+)
./gradlew assembleReleaseModern
```

### Play Store Distribution

For Play Store distribution, we recommend:

1. Upload both APK variants with appropriate version codes
2. Set device targeting in Play Console to direct each APK to compatible Android versions
3. Update your store listing to explain the behavior difference

## Testing

Test your implementation on:

- Android 9-10 device (API 28-29)
- Android 11-13 device (API 30-33)
- Android 14 device (API 34)
- Android 15 device or emulator (API 35+)

Ensure:

- Direct SMS works on API 23-34 when permission granted
- Intent-based SMS works on all versions
- No crashes on API 35+
- UI properly indicates behavior differences

## Related Documentation

- [Android 15 Permission Changes](https://developer.android.com/about/versions/15/changes/sms-permissions)
- [Intent-based SMS](https://developer.android.com/guide/components/intents-common#Messaging)
