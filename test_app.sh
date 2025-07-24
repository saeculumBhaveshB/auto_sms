#!/bin/bash

DEVICE_ID="ZD2224C4SG"

echo "🧪 Testing Auto SMS App on device: $DEVICE_ID"
echo "=" | tr '=' '=' | head -c 50 && echo

# Check if app is running
echo "1. Checking if app is running..."
APP_PID=$(adb -s $DEVICE_ID shell "ps | grep auto_sms" | awk '{print $2}')
if [ ! -z "$APP_PID" ]; then
    echo "✅ App is running with PID: $APP_PID"
else
    echo "❌ App is not running"
    exit 1
fi

# Check app permissions
echo -e "\n2. Checking app permissions..."
adb -s $DEVICE_ID shell "dumpsys package com.auto_sms | grep permission" | head -10

# Check if Metro is connected
echo -e "\n3. Checking Metro connection..."
adb -s $DEVICE_ID shell "netstat -an | grep 8081" | head -3

# Get recent app logs
echo -e "\n4. Recent app logs (last 20 lines)..."
adb -s $DEVICE_ID logcat -d | grep -E "(auto_sms|ReactNativeJS|CallSmsModule)" | tail -20

echo -e "\n✅ App test completed!"