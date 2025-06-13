#!/bin/bash

# Colors for better readability
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

echo -e "${CYAN}====================================${NC}"
echo -e "${CYAN}     RCS Testing Reset Tool         ${NC}"
echo -e "${CYAN}====================================${NC}"

echo -e "${YELLOW}Sending commands to reset RCS state...${NC}"

# Use adb to send broadcast to reset the RCS state
adb shell am broadcast -a com.auto_sms.RESET_RCS_STATE
echo -e "${GREEN}✓ Sent broadcast to reset RCS state${NC}"

# Clear logs
adb logcat -c
echo -e "${GREEN}✓ Cleared logcat buffer${NC}"

# Set a testing-friendly rate limit
adb shell am broadcast -a com.auto_sms.SET_TESTING_RATE_LIMIT
echo -e "${GREEN}✓ Set testing-friendly rate limit${NC}"

echo -e "\n${BLUE}RCS state has been reset. You can now test with a clean slate.${NC}"
echo -e "${YELLOW}Tip: Run ./monitor_rcs_logs.sh to watch for RCS messages${NC}" 