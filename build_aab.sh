#!/bin/bash

echo "📦 Building Android App Bundle (AAB) for Play Store"
echo "=================================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Check if keystore exists
KEYSTORE_PATH="android/app/release.keystore"
GRADLE_PROPS="android/gradle.properties"

echo -e "${BLUE}🔍 Checking prerequisites...${NC}"

# Check if keystore exists
if [ ! -f "$KEYSTORE_PATH" ]; then
    echo -e "${RED}❌ Release keystore not found at: $KEYSTORE_PATH${NC}"
    echo -e "${YELLOW}💡 Run ./create_keystore.sh first to create a release keystore${NC}"
    exit 1
fi

# Check if gradle.properties exists
if [ ! -f "$GRADLE_PROPS" ]; then
    echo -e "${RED}❌ gradle.properties not found${NC}"
    echo -e "${YELLOW}💡 Copy android/gradle.properties.example to android/gradle.properties and update with your keystore passwords${NC}"
    exit 1
fi

# Check if gradle.properties has keystore configuration
if ! grep -q "MYAPP_RELEASE_STORE_PASSWORD" "$GRADLE_PROPS"; then
    echo -e "${RED}❌ gradle.properties missing keystore configuration${NC}"
    echo -e "${YELLOW}💡 Update android/gradle.properties with your keystore passwords${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Prerequisites check passed${NC}"
echo ""

# Clean previous builds
echo -e "${BLUE}🧹 Cleaning previous builds...${NC}"
cd android
./gradlew clean

if [ $? -ne 0 ]; then
    echo -e "${RED}❌ Clean failed${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Clean completed${NC}"
echo ""

# Build the AAB
echo -e "${BLUE}🔨 Building release AAB...${NC}"
echo "This may take a few minutes..."

./gradlew bundleRelease

if [ $? -eq 0 ]; then
    echo ""
    echo -e "${GREEN}🎉 AAB build successful!${NC}"
    echo ""
    
    # Find the AAB file
    AAB_FILE=$(find app/build/outputs/bundle/release -name "*.aab" | head -1)
    
    if [ -f "$AAB_FILE" ]; then
        AAB_SIZE=$(du -h "$AAB_FILE" | cut -f1)
        echo -e "${GREEN}📦 AAB file created:${NC}"
        echo -e "   📍 Location: android/$AAB_FILE"
        echo -e "   📏 Size: $AAB_SIZE"
        echo ""
        
        # Copy to root directory for easier access
        cp "$AAB_FILE" "../auto_sms_release.aab"
        echo -e "${GREEN}📋 Copied to root directory as: auto_sms_release.aab${NC}"
        echo ""
        
        echo -e "${BLUE}📋 Next steps for Play Store submission:${NC}"
        echo "1. Go to Google Play Console (https://play.google.com/console)"
        echo "2. Create a new app or select existing app"
        echo "3. Upload the AAB file: auto_sms_release.aab"
        echo "4. Fill in app details, screenshots, and descriptions"
        echo "5. Set up content rating and privacy policy"
        echo "6. Submit for review"
        echo ""
        
        echo -e "${YELLOW}⚠️  Important notes:${NC}"
        echo "• Keep your release.keystore file safe - you'll need it for updates"
        echo "• The app requires sensitive permissions (SMS, Call Log) - provide clear justification"
        echo "• Test the release build thoroughly before submission"
        
    else
        echo -e "${RED}❌ AAB file not found after build${NC}"
        exit 1
    fi
else
    echo -e "${RED}❌ AAB build failed${NC}"
    echo "Check the error messages above and fix any issues"
    exit 1
fi

cd ..