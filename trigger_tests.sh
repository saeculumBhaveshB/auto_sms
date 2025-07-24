#!/bin/bash

DEVICE_ID="ZD2224C4SG"

echo "🧪 Triggering Auto SMS App Tests"
echo "📱 Device: $DEVICE_ID"
echo "=" | tr '=' '=' | head -c 40 && echo

# 1. Bring app to foreground
echo "1. Bringing app to foreground..."
adb -s $DEVICE_ID shell am start -n com.auto_sms/.MainActivity
sleep 2

# 2. Check if app is responsive
echo "2. Checking app responsiveness..."
adb -s $DEVICE_ID shell input keyevent KEYCODE_MENU
sleep 1

# 3. Try to trigger some UI interactions
echo "3. Simulating UI interactions..."
# Tap on screen center to interact with the app
adb -s $DEVICE_ID shell input tap 500 1000
sleep 1

# 4. Check current activity
echo "4. Current activity:"
adb -s $DEVICE_ID shell "dumpsys activity activities | grep -E 'auto_sms|MainActivity'" | head -3

# 5. Check memory usage
echo -e "\n5. App memory usage:"
adb -s $DEVICE_ID shell "dumpsys meminfo com.auto_sms | head -10"

echo -e "\n✅ Test triggers completed!"
echo "💡 Monitor the live_monitor.sh output for real-time logs"