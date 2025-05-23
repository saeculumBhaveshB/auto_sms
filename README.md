# Auto SMS Sender for Missed Calls

An Android application built with React Native that automatically sends SMS messages to callers whose calls have been missed.

## Features

- **Automatic SMS Sending**: Automatically sends a predefined SMS message to callers whose calls have been missed.
- **Call Detection**: Monitors incoming calls and identifies missed calls.
- **SMS Status Tracking**: Keeps track of sent messages and their status (sent or failed).
- **History Display**: Shows a history of automatically sent SMS messages with timestamps and status.
- **Toggle Functionality**: Allows enabling/disabling the auto-send SMS feature.

## Technical Implementation

### Native Android Components

- **CallSmsModule**: Native Kotlin module that handles phone call detection and SMS sending.
- **CallReceiver**: BroadcastReceiver for handling call state changes and SMS events.
- **CallSmsPackage**: Package class to register the native module with React Native.

### React Native Components

- **CallSmsService**: JavaScript service that interfaces with the native module.
- **AutoSmsStatusScreen**: UI component to display SMS history and toggle the auto-SMS feature.
- **PermissionsStatusScreen**: UI component to manage and request necessary permissions.

## Required Permissions

The app requires the following Android permissions:

- `READ_CALL_LOG`: To access call history and detect missed calls.
- `READ_PHONE_STATE`: To detect incoming calls.
- `SEND_SMS`: To send automatic SMS messages.
- `READ_SMS`: To verify message status.
- `RECEIVE_SMS`: For receiving SMS status updates.
- `READ_CONTACTS`: For displaying contact names (optional).

## Setup and Running

### Prerequisites

- Node.js (>= 18.x)
- JDK 17
- Android SDK
- React Native CLI

### Installation

1. Clone the repository
2. Install dependencies:
   ```
   npm install
   ```
3. Start the app:
   ```
   npx react-native run-android
   ```

## Usage

1. Launch the app
2. Grant the required permissions on the Permissions screen
3. Navigate to the "Auto SMS Status" tab
4. Toggle the auto-send SMS feature on/off
5. Miss a call to trigger the automatic SMS sending
6. View the status and history of sent messages on the same screen

## Default SMS Message

The default SMS message is:

> "I am busy, please give me some time, I will contact you."

## Troubleshooting

- If calls are not being detected, ensure all permissions are granted.
- For some devices, you may need to disable battery optimization for the app.
- Make sure the app is set as the default SMS app or has been granted permission to send SMS.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.
