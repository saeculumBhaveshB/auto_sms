#!/bin/bash

DEVICE_ID="ZD2224C4SG"

echo "🔍 Live Auto SMS App Monitor"
echo "📱 Device: $DEVICE_ID"
echo "🕐 Started: $(date)"
echo "🛑 Press Ctrl+C to stop monitoring"
echo "=" | tr '=' '=' | head -c 60 && echo

# Clear logs and start fresh monitoring
adb -s $DEVICE_ID logcat -c

# Monitor with real-time filtering
adb -s $DEVICE_ID logcat | while read line; do
    # Filter for relevant logs
    if echo "$line" | grep -qE "(auto_sms|CallSmsModule|AutoReplyModule|ReactNativeJS|PermissionsManager|LocalLLMModule|LOGTAG_SMS|LOGTAG_RCS)"; then
        timestamp=$(date '+%H:%M:%S')
        
        # Color and emoji coding
        if echo "$line" | grep -q "ERROR\|FATAL"; then
            echo -e "\033[31m[$timestamp] 🔴 ERROR: $line\033[0m"
        elif echo "$line" | grep -q "WARN"; then
            echo -e "\033[33m[$timestamp] ⚠️  WARN: $line\033[0m"
        elif echo "$line" | grep -q "CallSmsModule"; then
            echo -e "\033[32m[$timestamp] 📞 CALL/SMS: $line\033[0m"
        elif echo "$line" | grep -q "AutoReplyModule"; then
            echo -e "\033[34m[$timestamp] 🤖 AUTO-REPLY: $line\033[0m"
        elif echo "$line" | grep -q "PermissionsManager"; then
            echo -e "\033[93m[$timestamp] 🔐 PERMISSIONS: $line\033[0m"
        elif echo "$line" | grep -q "LocalLLMModule"; then
            echo -e "\033[95m[$timestamp] 🧠 LLM: $line\033[0m"
        elif echo "$line" | grep -q "LOGTAG_SMS_DETAILS"; then
            echo -e "\033[35m[$timestamp] 📩 SMS: $line\033[0m"
        elif echo "$line" | grep -q "LOGTAG_RCS_DETAILS"; then
            echo -e "\033[36m[$timestamp] 📨 RCS: $line\033[0m"
        elif echo "$line" | grep -q "ReactNativeJS"; then
            echo -e "\033[37m[$timestamp] ⚛️  RN: $line\033[0m"
        else
            echo -e "\033[90m[$timestamp] ℹ️  INFO: $line\033[0m"
        fi
    fi
done