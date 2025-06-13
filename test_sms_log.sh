#!/bin/bash

# Clear existing logs
adb logcat -c

# Generate test SMS log entries
adb shell log -t SmsReceiver "LOGTAG_SMS_DETAILS: 📩📩📩 SMS MESSAGE DETAILS 📩📩📩"
adb shell log -t SmsReceiver "LOGTAG_SMS_DETAILS: ↘️ From: +1234567890"
adb shell log -t SmsReceiver "LOGTAG_SMS_DETAILS: ↘️ Message: Hello, this is a test message!"
adb shell log -t SmsReceiver "LOGTAG_SMS_DETAILS: ↘️ Timestamp: $(date +%s%3N)"
adb shell log -t SmsReceiver "LOGTAG_SMS_DETAILS: ↘️ Message Length: 28 characters"
adb shell log -t SmsReceiver "LOGTAG_SMS_DETAILS: ↘️ Display Originating Address: +1234567890"
adb shell log -t SmsReceiver "LOGTAG_SMS_DETAILS: ↘️ Message Class: UNKNOWN"
adb shell log -t SmsReceiver "LOGTAG_SMS_DETAILS: ↘️ Message ID: 0"
adb shell log -t SmsReceiver "LOGTAG_SMS_DETAILS: ↘️ Is from missed call number: true"

# Show the logs
adb logcat -d | grep "LOGTAG_SMS_DETAILS"

echo "Test SMS log entries generated successfully!" 