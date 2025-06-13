RCS Auto‑Reply Implementation Document

Version: 1.0Date: 2025‑06‑12Author: ChatGPT Dev (Business Analyst)

1. Purpose

Provide a complete blueprint for adding Rich Communication Services (RCS) auto‑reply capability to the existing Auto‑SMS Android application without disrupting current SMS auto‑reply features.

2. Scope

Android 11 (API 30) through Android 15 (API 35) inclusive, with forward‑compatibility hooks for future releases.

Consumer phones running Google Messages or OEM RCS‑capable messaging apps.

Enterprise/Kiosk deployments out of scope (separate MDM document).

3. Definitions & Acronyms

Term

Definition

RCS

Rich Communication Services, an IP‑based successor to SMS/MMS.

SMS

Short Message Service.

NotificationListenerService (NLS)

Android service that receives posted notifications.

RemoteInput

Android class enabling inline replies from notifications.

Sensitive Notifications Toggle (A15)

New Android 15 switch gating access to notification text for sideloaded apps.

AAI

Auto‑reply Action Intent fired back to messaging app.

4. High‑Level Goals

Detect incoming RCS chat notifications in real‑time.

Apply user‑defined auto‑reply rules (time‑based, sender‑based, message‑content‑based etc.).

Dispatch a reply via the notification’s RemoteInput action.

Guarantee parity with existing SMS auto‑reply engine, including:

RoleManager‑based default SMS behaviour remains intact.

Legacy SmsManager flows remain regression‑tested.

Maintain compliance with Google Play policies for sensitive user data and restricted permissions.

5. Assumptions & Constraints

No public RCS API: notification interception is the sole viable mechanism.

User must explicitly grant Notification Access (and Sensitive notifications on A15+) via system settings.

The app is distributed via Google Play; no root or ADB commands permitted in production.

Cursor AI will generate source code; this document therefore omits code samples.

6. Functional Requirements

ID

Requirement

FR‑01

The system SHALL listen for notifications from com.google.android.apps.messaging, com.samsung.android.messaging, and other OEM package IDs defined in a configuration list.

FR‑02

The system SHALL identify notifications tagged with Notification.CATEGORY_MESSAGE.

FR‑03

The system SHALL extract sender name/number, conversation ID, and message preview text when available and permitted.

FR‑04

When a rule matches, the system SHALL locate the first Notification.Action that contains a non‑null RemoteInput and is intended for direct reply.

FR‑05

The system SHALL generate and send an auto‑reply via the action’s PendingIntent.

FR‑06

The system SHALL support rule conditions: always‑on, calendar‑based (Do Not Disturb periods), contact groups, keywords, first‑response‑only, and rate‑limiting (one reply per x minutes).

FR‑07

The system SHALL preserve existing SMS auto‑reply flows based on SmsManager / ROLE_SMS.

FR‑08

The system SHALL provide UI toggles to enable/disable RCS auto‑reply independently from SMS auto‑reply.

FR‑09

The system SHALL log each auto‑reply event (timestamp, sender, channel type, rule ID, success/failure).

FR‑10

The system SHALL gracefully handle missing RemoteInput (e.g., group MMS or media‑only RCS) and skip auto‑reply with a logged warning.

FR‑11

The system SHALL preserve notification visibility to the user unless “Mark as read” is enabled by preference.

FR‑12

The system SHALL respect Android power‑saving modes; no persistent wake‑locks longer than 3 seconds.

FR‑13

The system SHALL store user rules in encrypted SharedPreferences compliant with AES‑256.

7. Non‑Functional Requirements

Reliability: ≥ 99.5% success rate replying under normal network conditions.

Latency: Auto‑reply dispatched ≤ 1 second from notification receipt.

Battery impact: < 0.5% daily drain measured on Pixel 6 baseline.

Privacy: No message bodies stored server‑side; local storage encrypted.

Accessibility: Settings screens compatible with TalkBack and large‑font modes.

8. Compatibility Matrix

Android Version

SMS Auto‑Reply Status

RCS Auto‑Reply Status

Special Handling

11‑12 (API 30‑31)

Works via default‑SMS role

Works via NLS

None

13‑14 (API 33‑34)

Works

Works; message text may be redacted on OEM skins if “Sensitive content” off

Prompt user to enable sensitive content

15 (API 35)

Works

Reply action works; reading message preview requires RECEIVE_SENSITIVE_NOTIFICATIONS App‑Op permission granted by user

Display onboarding banner

9. Architecture Overview

┌──────────┐ Notification ┌───────────────────┐
│Messaging │ ───▶ Listener Service ─▶ │ Rule Engine │
│ App │ │(Match & Decide) │
└──────────┘ └────────┬──────────┘
▼
Auto‑Reply
(RemoteInput Sender)
│
▼
Audit Logger/DB

Components

Notification Listener Service (NLS) – Parses incoming notifications.

Rule Engine – Evaluates user‑defined criteria.

Auto‑Reply Dispatcher – Crafts reply Bundle, fires action.

SMS Engine (legacy) – Existing module; remains untouched.

Settings UI – Toggle switches, rule builder, onboarding flows.

Persistence Layer – Encrypted storage for rules & logs.

Analytics & Crashlytics – Firebase or equivalent SDK.

10. Detailed Design

10.1 Notification Listener Service

Registration: Declare in AndroidManifest.xml with BIND_NOTIFICATION_LISTENER_SERVICE.

Lifecycle: Start at boot; stop only when permission revoked.

Filtering Pipeline:

Verify packageName in allow‑list.

Confirm Notification.CATEGORY_MESSAGE.

Extract metadata into Notification DTO.

Forward DTO to Rule Engine via Kotlin Flow channel.

10.2 Rule Engine

Input: Notification DTO.

Process: Sequential evaluation of ordered rule list until first match.

Output: RuleDecision (reply text, flags).

Extensibility: Rules defined as strategy objects pluggable via reflection.

10.3 Auto‑Reply Dispatcher

Lookup: Iterate through notification.actions seeking RemoteInput.

Preparation: Build result Bundle keyed by RemoteInput.resultKey.

Dispatch: Send via PendingIntent.send().

Post‑Action: Optionally call NotificationManager.cancel(key) if “Mark as read” enabled.

10.4 Settings & UX

Onboarding Flow

Step 1: Request Notification Access.

Step 2: Explain Sensitive Notifications toggle (A15).

Step 3: Offer to become default SMS app (optional).

Main Switches

Enable SMS Auto‑Reply (existing).

Enable RCS Auto‑Reply (new).

Rule Builder UI

Trigger type (time, sender, keyword, always‑on).

Reply template (supports placeholders like %sender%).

Rate limit.

Logs Screen – Chronological list; tap entry reveals debug details.

11. Permissions Matrix

Permission

Purpose

Acquisition

POST_NOTIFICATIONS

Show app‑generated status toasts

Runtime dialog (API 33+)

BIND_NOTIFICATION_LISTENER_SERVICE

Receive notifications

Granted by user in OS Settings

RECEIVE_SENSITIVE_NOTIFICATIONS (App‑Op)

Access message text on A15+

Explain setting; instruct user to toggle

Role ROLE_SMS

Existing SMS auto‑reply

Optional; requested via RoleManager

READ_CONTACTS (optional)

Match known contacts in rules

Runtime dialog

12. Error Handling & Fallbacks

If RemoteInput not found → skip auto‑reply; log ACTION_NOT_SUPPORTED.

If PendingIntent.send() throws CanceledException → retry once with 100 ms back‑off.

If user revoked Notification Access → show critical alert notification; disable RCS auto‑reply.

If battery optimizations kill NLS → Display in‑app banner prompting user to whitelist app.

13. Security & Privacy

No outbound network calls with message content.

Encrypt rule definitions and logs at rest.

Expose Data Safety section updates for Google Play disclosure.

Provide opt‑out for analytics.

14. Logging & Analytics

Event schema: event_name, timestamp, channel_type, sender_hash, rule_id, result.

Retention: 30 days rolling, local‑only.

Crashlytics: Ensure exceptions in Dispatcher are non‑fatal but reported.

15. Testing Strategy

Unit Tests – Rule evaluation logic, DTO parsing.

Instrumentation Tests – Simulated notifications via NotificationCompat.Builder.

Manual QA Matrix – Device farm covering Google/Samsung/OnePlus.

Regression Suite – Existing SMS auto‑reply scenarios.

Monkey Test – 10k random events to ensure stability.

Accessibility Audit – TalkBack, font scaling.

16. Deployment

Build Variants: free vs pro; both include RCS module.

Gradle CI/CD: GitHub Actions with signing, Play Console upload.

Roll‑out: Staged 10% → 50% → 100% with crash‑rate gates.

Rollback Plan: Keep last stable APK in track; disable feature flag remotely if crash > 1%.

17. Maintenance & Monitoring

Feature Flag: Server‑driven boolean rcs_auto_reply_enabled.

Dashboard Metrics: daily replies sent, failures, battery impact, ANRs.

User Feedback Loop: In‑app “Report Issue” pre‑filled with log snippet.

18. Future Enhancements

Media‑rich quick replies using suggested actions.

Per‑conversation contextual smart replies (ML‑driven).

Wear OS & Auto support.

Enterprise device‑owner silent grant flow (separate build flavor).
