#!/bin/bash

# Log Filter Script for SMS and RCS Messages
# This script filters logcat output to show only SMS and RCS message details

echo "===== SMS and RCS Message Log Filter ====="
echo "This script will display incoming SMS and RCS messages from the logs"
echo "Press Ctrl+C to exit"
echo ""

# Clear existing logs first
adb logcat -c

# Define colors for better visibility
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
RESET='\033[0m'

# Filter for specific log tags using grep
# This will show only the SMS and RCS message details logs
adb logcat | grep -E "LOGTAG_(SMS|RCS)_DETAILS" | while read -r line; do
    if [[ $line == *"LOGTAG_SMS_DETAILS"* ]]; then
        # Format SMS logs with green color
        echo -e "${GREEN}[SMS]${RESET} $line"
    elif [[ $line == *"LOGTAG_RCS_DETAILS"* ]]; then
        # Format RCS logs with blue color
        echo -e "${BLUE}[RCS]${RESET} $line"
    else
        # Other log lines
        echo "$line"
    fi
done 