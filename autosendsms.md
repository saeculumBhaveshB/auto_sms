# Auto SMS Sender on Missed Call - Functional Requirements

## Objective

Automatically send a predefined SMS to callers whose calls have been missed, and display the message status on-screen for user awareness.

## Functional Overview

When the user receives an incoming call and does not answer (missed call), the app will automatically detect this event and send a predefined static SMS message to the caller's phone number.

## Message Content

- The SMS will contain a static, predefined message:

  - "I am busy, please give me some time, I will contact you."

## Requirements Breakdown

### 1. Missed Call Detection

- Listen continuously for incoming calls.
- Identify calls not answered by the user (missed calls).
- Extract the caller’s phone number accurately.

### 2. Automatic SMS Sending

- Upon detecting a missed call:

  - Access the caller’s number.
  - Trigger an automatic SMS to the caller using the predefined static message.
  - Ensure the SMS sending process does not require user intervention once permissions are granted.

### 3. SMS Status Management

- Store the status of each SMS sent:

  - Status options: "Sent", "Failed".
  - Timestamp of when the SMS was sent.
  - The phone number to which the SMS was sent.

- Use local storage (AsyncStorage) to persist the SMS sending statuses and related information.

### 4. User Interface (UI) for SMS Status

- Develop a clear and intuitive screen that displays the history and status of auto-sent SMS:

  - Display a list of phone numbers.
  - Corresponding SMS statuses (Sent, Failed).
  - Timestamps of when messages were sent.
  - Refresh capability to update the status list in real-time.

### 5. Error Handling

- Capture errors clearly (e.g., SMS failed due to network issues).
- Update AsyncStorage and UI accordingly.

### 6. Permissions

Ensure the following permissions (as per the previous permissions phase) are in place and actively checked:

- Send and View SMS Messages
- Access Call Logs
- Identify Missed Calls

### 7. Security & Privacy

- Store user data (SMS logs, phone numbers) locally.
- Do not transmit data to external servers without explicit user consent.

### UI Example Layout

- **Screen Title:** "Auto SMS Status"
- **List View:**

  - Each item includes:

    - Caller Phone Number
    - Message Content (truncated for space)
    - Status indicator (Sent or Failed)
    - Timestamp

### Technical Stack

- Frontend: React Native
- Local Storage: AsyncStorage
- Native Android integration for SMS sending and missed call detection.

## Deliverables

- Functional auto-send SMS feature on missed calls.
- Comprehensive UI to display SMS sending status clearly.
- Robust local data storage management to ensure data integrity and privacy.

By adhering to this document, Cursor AI can accurately generate the necessary code to fulfill the stated functionalities.
