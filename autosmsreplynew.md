# High-Level Implementation Guide: SMS Auto-Reply Feature

Provide these instructions to Cursor AI to scaffold the application logic—no code snippets, just functional guidance.

---

## 1. Permission Configuration

- Identify and request the following Android permissions at runtime via React Native:

  - RECEIVE_SMS
  - SEND_SMS
  - READ_PHONE_STATE (if leveraging missed-call detection)
  - RECEIVE_BOOT_COMPLETED (for background persistence)

---

## 2. Missed-Call Handling

- Define a native service or receiver to detect when an incoming call transitions to "missed."
- On detection, dispatch a one-time static SMS to the caller indicating availability for chat.

---

## 3. SMS Listening and Auto-Reply

- Implement a background listener that activates when auto-reply is enabled.
- Filter incoming SMS events to respond only to numbers that triggered the missed-call SMS.
- Upon each relevant SMS received, send back the message: "Yes I am."
- Ensure the listener can be dynamically registered or unregistered based on user control.

---

## 4. React Native Integration Points

- **Enable Auto-Reply**: Expose a JS-accessible action that starts both the missed-call detector and SMS listener.
- **Disable Auto-Reply**: Expose a JS action to stop all related background services.
- **User Interface**: Provide a simple on/off toggle and optionally display a log of recent interactions (caller number, received SMS, sent reply).

---

## 5. Background Service Lifecycle

- Ensure all native listeners and services persist across app restarts and device reboots when enabled.
- Tie registration and unregistration to the user’s toggle action, avoiding orphaned receivers.

---

## 6. Testing Scenarios

1. **Permissions Workflow**: Confirm runtime prompts and granted states.
2. **Missed-Call Workflow**: Simulate a missed call → verify static SMS delivery.
3. **Auto-Reply Workflow**: Send an SMS reply → verify "Yes I am" response.
4. **Toggle Off**: Disable auto-reply → verify no further responses are sent.

---

Cursor AI should use these high-level requirements to generate the React Native bridge, native Android receivers/services, manifest updates, and UI components—a complete auto-reply feature without embedding actual code examples.

---

## 7. Step-by-Step Summary of Implementation Steps

1. **Configure Permissions**

   - List required permissions in AndroidManifest.
   - Implement runtime permission requests in React Native.

2. **Set Up Missed-Call Detection**

   - Define native receiver/service for call state changes.
   - On missed call, send a one-time static SMS to caller.

3. **Implement SMS Listener**

   - Create background listener for incoming SMS events.
   - Filter for numbers that received the missed-call SMS.

4. **Auto-Reply Logic**

   - Upon filtered SMS receipt, dispatch "Yes I am" reply.
   - Ensure dynamic registration/unregistration based on user toggle.

5. **React Native Bridge**

   - Expose `enableAutoReply` and `disableAutoReply` actions to JS.
   - Bridge these to native start/stop of detectors and listeners.

6. **User Interface**

   - Design a simple toggle control to start/stop auto-reply.
   - (Optional) Display a log of interactions with caller and replies.

7. **Background Service Management**

   - Ensure services/listeners survive app restarts and device reboots.
   - Tie service lifecycle to the toggle state to prevent orphan processes.

8. **Testing and Validation**

   - Verify permission granting flows.
   - Simulate missed calls and check static SMS delivery.
   - Send SMS replies and confirm auto-reply behavior.
   - Toggle off auto-reply and ensure functionality stops.

---

Use this step-by-step summary for a clear checklist and guidance when scaffolding the feature.
