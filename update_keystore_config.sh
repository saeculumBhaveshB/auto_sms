#!/bin/bash

echo "🔐 Updating Keystore Configuration"
echo "================================="

GRADLE_PROPS="android/gradle.properties"

echo "Please enter the passwords you used when creating the keystore:"
echo ""

# Get keystore password
echo -n "Enter your keystore password: "
read -s KEYSTORE_PASSWORD
echo ""

# Get key password
echo -n "Enter your key password: "
read -s KEY_PASSWORD
echo ""

# Update the gradle.properties file
sed -i.bak "s/YOUR_KEYSTORE_PASSWORD_HERE/$KEYSTORE_PASSWORD/g" "$GRADLE_PROPS"
sed -i.bak "s/YOUR_KEY_PASSWORD_HERE/$KEY_PASSWORD/g" "$GRADLE_PROPS"

# Remove backup file
rm -f "$GRADLE_PROPS.bak"

echo ""
echo "✅ Keystore configuration updated successfully!"
echo ""
echo "📋 Configuration summary:"
echo "• Keystore file: android/app/release.keystore"
echo "• Key alias: auto-sms-release"
echo "• Passwords: Updated in gradle.properties"
echo ""
echo "🚀 Ready to build AAB! Run: ./build_aab.sh"