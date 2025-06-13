#!/bin/bash

# Colors for better readability
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
NC='\033[0m' # No Color

echo -e "${CYAN}====================================${NC}"
echo -e "${CYAN}    RCS Message Monitoring Tool    ${NC}"
echo -e "${CYAN}====================================${NC}"
echo -e "${YELLOW}Monitoring for RCS message logs...${NC}"
echo -e "${BLUE}Press Ctrl+C to stop${NC}\n"

# Filter for RCS messages using our special tag
adb logcat -v time | grep -E --color=always "LOGTAG_RCS_DETAILS|RcsAutoReply|RcsNotification" | sed \
    -e "s/.*LOGTAG_RCS_DETAILS.*/${MAGENTA}&${NC}/" \
    -e "s/.*RCS MESSAGE DETAILS.*/${GREEN}&${NC}/" \
    -e "s/.*RcsAutoReply.*/${YELLOW}&${NC}/" \
    -e "s/.*RcsNotification.*/${BLUE}&${NC}/" \
    -e "s/.*Error.*/${RED}&${NC}/" \
    -e "s/.*Failed.*/${RED}&${NC}/" 