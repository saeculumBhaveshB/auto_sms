### Implementation Guide

**Granting & Handling `PERMISSIONS.ANDROID.SEND_SMS` Across Android Versions (API 23 → 35)**

---

#### 1. Scope & Purpose

This document explains **why SMS-related crashes appear on Android 15 (API 35)** and how to redesign your React Native or native Android projects so that SMS features remain functional—and Play-Store compliant—on **Android 6 through 14**, while degrading gracefully on **Android 15 and later**.

---

#### 2. Platform & Policy Snapshot

| Android version | API level | `SEND_SMS` status                                                    | Key policy / system rule                                                                                                 | Practical consequence                                                                                            |
| --------------- | --------- | -------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------- |
| 6 → 10          | 23–29     | _Dangerous_ runtime permission                                       | Standard runtime request                                                                                                 | Direct SMS sending permitted once user grants permission                                                         |
| 11 → 14         | 30–34     | _Special_ + **hard-restricted** (SMS & Call-Log groups)              | • Must justify use in Play Console _Permissions Declaration Form_ <br>• App should be **default SMS handler** to qualify | Most non-SMS-apps are forced to switch to intent-based flow                                                      |
| **15 +**        | **35**    | **System-only / whitelisted**; cannot be granted to third-party apps | Permission toggle is disabled in Settings; Play Store rejects new APKs containing it                                     | Direct SMS APIs crash with `SecurityException`; only system apps or the device's default SMS handler can hold it |

> **Bottom line:** Starting with Android 15 the platform itself blocks regular apps from receiving **any** grant for `SEND_SMS`. You must migrate to a user-initiated flow that relies on implicit SMS intents.

---

#### 3. Why Crashes Occur on Android 15

- At runtime, the framework detects that a non-system app is requesting a **hard-restricted, non-grantable** permission and immediately throws a `SecurityException` → process termination.
- Even adding the permission in `AndroidManifest.xml` no longer helps; the Settings UI shows the toggle as disabled (greyed-out).

---

#### 4. Implementation Strategy by Version

##### 4.1 Common Ground (All Versions)

1. **Clarify business need**: Google restricts SMS/Call-Log permissions to protect users. If SMS is not your **core functionality**, you must remove direct usage across all versions.
2. **Prefer role-based or intent-based flows**: Google recommends opening the system SMS app via an `ACTION_SENDTO` / `sms:` URI; this requires **no special permission** and remains future-proof.

##### 4.2 Android 6 → 10 (API 23–29)

- Runtime-request flow is still valid.
- Keep `SEND_SMS` in the manifest and request it only after an explicit user gesture (button tap).
- Ensure graceful fallback (e.g., toast + open messaging app) if permission is denied.

##### 4.3 Android 11 → 14 (API 30–34)

- **Play-Store compliance**:

  - Complete the **Permissions Declaration Form** for SMS usage every release.
  - Your app must either be the **default SMS handler** _or_ qualify for an exceptional category (e.g., emergency services).

- **Technical guards**:

  1. Detect default-SMS-handler status at runtime; if not default, skip direct APIs.
  2. Remove permission at build-time for Google-Play flavours that do not qualify.

##### 4.4 Android 15 + (API 35 +)

- **Do not declare `SEND_SMS`** in any production build that targets or is expected to run on Android 15.
- **Switch permanently to intent-based flow**: rely on the system Messaging app to send the message.
- **Update build system**:

  - Use Gradle manifest-merging or `manifestPlaceholders` to exclude the permission when `targetSdkVersion >= 35`.
  - Validate at CI that no non-system permission remains in merged manifests for Android 15.

- **User messaging**: Provide a concise banner or dialog explaining that the device will open the default SMS app to finish sending.

---

#### 5. Project-Level Migration Checklist (React Native Focus)

| Task                            | Notes                                                                                                                                                                                                     |
| ------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Audit native modules**        | Remove or gate any Java/Kotlin code calling `SmsManager.sendTextMessage()`.                                                                                                                               |
| **Upgrade dependencies**        | Ensure `react-native-permissions` ≥ latest; newer versions already mark `sendSms` as **restricted** on API 35.                                                                                            |
| **Platform-aware feature flag** | Use `Platform.Version` check in JS to choose intent-based flow automatically on API 35+. No code snippet—just architectural note.                                                                         |
| **Update Play-Store artefacts** | For builds shipping to Play Store, delete `SEND_SMS`, fill declaration for API 30–34 flavours, and verify in Pre-launch report.                                                                           |
| **Regression testing matrix**   | Test on emulators: API 29, 32, 34, 35. Confirm: <br>• No runtime permission dialog on API 35 <br>• Direct API path succeeds on API 29 when granted <br>• Play-Store compliance checks pass for API 32/34. |
| **Document fallback UX**        | UX team should design a consistent flow: "Tap to open Messages and press Send".                                                                                                                           |

---

#### 6. Security & Privacy Considerations

- **Minimise personal data**: Only request SMS **receive** permissions (OTP / Retriever API) if absolutely necessary; they remain special and hard-restricted as well.
- **Store nothing**: Do not persist SMS contents unless business-critical; clarify retention policy in privacy notice.
- **Explain to users** why default SMS app is invoked (transparency builds trust).

---

#### 7. Future-Proofing

1. **Monitor new roles**: Google may expand the **"SMS role"** model; subscribing to Android Developers Blog is recommended.
2. **Prepare for verified SMS / RCS**: Many carriers deprecate plain SMS for transactional flows; evaluate Google Business Messages or Push-Notification fallback.
3. **Automated CI lint rule**: Add a gradle task that fails if `SEND_SMS` appears in merged manifest for any variant targeting API 35+.

---

#### 8. Key References

- Android 15 community reports on hard-restriction of `SEND_SMS`
- Google Play SMS/Call-Log Policy & Declaration Form requirements
- Default-handler roles & recommended intent-based approach
- AOSP runtime-permissions doc confirming SMS is a _hard-restricted_ group

---

### 📌 Takeaway

From Android 15 onward, `SEND_SMS` is effectively **unavailable to third-party apps**. The only sustainable strategy is to **abandon direct SMS-sending permissions** and move to a **user-initiated intent flow**—while still following Play-Store policy for older versions that allow direct access.

Implement the migration steps above to eliminate crashes, remain compliant, and provide a consistent cross-version experience.
