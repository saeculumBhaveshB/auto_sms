# App Size Optimization Guide

This document outlines the steps taken to optimize the app size and remove unused files without breaking functionality.

## Optimizations Applied

1. **Enabled ProGuard for Release Builds**

   - Modified `android/app/build.gradle` to enable ProGuard
   - Created enhanced ProGuard rules in `android/app/proguard-rules.pro`

2. **Optimized Android Build Configuration**

   - Enabled resource shrinking for release builds
   - Configured APK splitting by ABI to reduce individual download sizes
   - Enhanced React Native bundle configuration with minification
   - Added optimizations for Hermes JavaScript engine

3. **Removed Unused Files**

   - Deleted unused `.docx` files from the root directory
   - Optimized and simplified large components like `LLMTester.tsx`

4. **Added Build Optimization Script**
   - Created `android/optimize-build.sh` to streamline the build process
   - Added clean steps to remove intermediate files

## Building an Optimized APK

To build an optimized APK:

1. Run the optimization script:

   ```
   cd android
   ./optimize-build.sh
   ```

2. Find the optimized APKs in:
   ```
   android/app/build/outputs/apk/release/
   ```

## Size Impact

The above optimizations should significantly reduce the app size compared to the original build. The exact reduction depends on the specific device architecture being targeted.

## Maintaining Optimizations

When adding new features or dependencies:

1. Consider their impact on app size
2. Use lazy loading for non-critical components
3. Keep the ProGuard rules updated
4. Run regular size audits

## Notes

- ProGuard may sometimes strip code that's actually needed. If functionality breaks after optimization, check ProGuard rules.
- The ABI split configuration produces multiple APKs, each for a specific device architecture, which reduces individual download size.
- Resource shrinking removes unused resources but may occasionally remove needed ones. Test thoroughly after optimization.
