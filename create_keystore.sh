#!/bin/bash

echo "🔐 Creating Release Keystore for Play Store"
echo "==========================================="

# Keystore configuration
KEYSTORE_PATH="android/app/release.keystore"
KEY_ALIAS="auto-sms-release"
VALIDITY_DAYS=10000

echo "📝 This will create a release keystore for your Auto SMS app."
echo "⚠️  IMPORTANT: Keep this keystore file safe! You'll need it for all future app updates."
echo ""

# Check if keystore already exists
if [ -f "$KEYSTORE_PATH" ]; then
    echo "⚠️  Release keystore already exists at: $KEYSTORE_PATH"
    echo "Do you want to create a new one? (This will overwrite the existing keystore)"
    read -p "Continue? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "❌ Keystore creation cancelled."
        exit 1
    fi
fi

echo "🔑 Creating keystore..."
echo "You'll be prompted to enter information for the certificate:"
echo ""

# Generate the keystore
keytool -genkeypair \
    -v \
    -keystore "$KEYSTORE_PATH" \
    -alias "$KEY_ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity $VALIDITY_DAYS \
    -storetype PKCS12

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ Keystore created successfully!"
    echo "📍 Location: $KEYSTORE_PATH"
    echo "🔑 Key alias: $KEY_ALIAS"
    echo ""
    echo "⚠️  IMPORTANT SECURITY NOTES:"
    echo "1. Keep this keystore file safe and secure"
    echo "2. Remember your keystore password"
    echo "3. Remember your key password"
    echo "4. Back up this keystore file"
    echo "5. Never commit this keystore to version control"
    echo ""
    echo "Next steps:"
    echo "1. Update gradle.properties with your keystore passwords"
    echo "2. Build the release AAB file"
else
    echo "❌ Failed to create keystore"
    exit 1
fi