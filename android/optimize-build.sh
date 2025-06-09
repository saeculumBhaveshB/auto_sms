#!/bin/bash

# Script to optimize the React Native Android build
echo "Starting build optimization..."

# Clean the build directories
echo "Cleaning build directories..."
./gradlew clean

# Remove any existing APKs
echo "Removing old APKs..."
rm -rf app/build/outputs/apk/

# Run a minified release build
echo "Building optimized release APK..."
./gradlew assembleRelease

echo "Build optimization completed!"

# Optional: Display APK size
echo "APK Sizes:"
find app/build/outputs/apk -name "*.apk" -exec ls -lh {} \; 