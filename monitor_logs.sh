#!/bin/bash

# Auto SMS App Log Monitor
# This script monitors logs from the real Android device for the auto_sms app

DEVICE_ID="ZD2224C4SG"

echo "🚀 Starting Auto SMS App Log Monitor for device: $DEVICE_ID"
echo "📱 Monitoring logs for: com.auto_sms"
echo "⏰ Started at: $(date)"
echo "=" | tr '=' '=' | head -c 80 && echo

# Clear existing logs
adb -s $DEVICE_ID logcat -c

# Monitor logs with color coding
adb -s $DEVICE_ID logcat | grep --line-buffered -E "(auto_sms|CallSmsModule|AutoReplyModule|ReactNativeJS|LOGTAG_SMS_DETAILS|LOGTAG_RCS_DETAILS|PermissionsManager|LocalLLMModule)" | while read line; do
    timestamp=$(date '+%H:%M:%S')
    
    # Color coding based on log content
    if [[ $line == *"ERROR"* ]] || [[ $line == *"FATAL"* ]]; then
        echo -e "\033[31m[$timestamp] 🔴 $line\033[0m"  # Red for errors
    elif [[ $line == *"WARN"* ]]; then
        echo -e "\033[33m[$timestamp] 🟡 $line\033[0m"  # Yellow for warnings
    elif [[ $line == *"CallSmsModule"* ]]; then
        echo -e "\033[32m[$timestamp] 📞 $line\033[0m"  # Green for call/SMS
    elif [[ $line == *"AutoReplyModule"* ]]; then
        echo -e "\033[34m[$timestamp] 🤖 $line\033[0m"  # Blue for auto-reply
    elif [[ $line == *"LOGTAG_SMS_DETAILS"* ]]; then
        echo -e "\033[35m[$timestamp] 📩 $line\033[0m"  # Magenta for SMS details
    elif [[ $line == *"LOGTAG_RCS_DETAILS"* ]]; then
        echo -e "\033[36m[$timestamp] 📨 $line\033[0m"  # Cyan for RCS details
    elif [[ $line == *"PermissionsManager"* ]]; then
        echo -e "\033[93m[$timestamp] 🔐 $line\033[0m"  # Bright yellow for permissions
    elif [[ $line == *"LocalLLMModule"* ]]; then
        echo -e "\033[95m[$timestamp] 🧠 $line\033[0m"  # Bright magenta for LLM
    else
        echo -e "\033[37m[$timestamp] ℹ️  $line\033[0m"  # White for general info
    fi
done