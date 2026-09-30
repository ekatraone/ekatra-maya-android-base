#!/bin/bash
# Build script for Ekatra Alfred Android app

set -e

echo "🎓 Building Ekatra Alfred..."

# Check for Android SDK
if [ -z "$ANDROID_HOME" ]; then
    echo "⚠️  ANDROID_HOME not set. Trying common locations..."
    if [ -d "$HOME/Library/Android/sdk" ]; then
        export ANDROID_HOME="$HOME/Library/Android/sdk"
    elif [ -d "$HOME/Android/Sdk" ]; then
        export ANDROID_HOME="$HOME/Android/Sdk"
    else
        echo "❌ Could not find Android SDK. Please set ANDROID_HOME."
        exit 1
    fi
fi

echo "📱 Using Android SDK: $ANDROID_HOME"

# Navigate to project directory
cd "$(dirname "$0")"

# Make gradlew executable
chmod +x gradlew 2>/dev/null || true

# Download gradle wrapper if missing
if [ ! -f "gradlew" ]; then
    echo "📥 Downloading Gradle wrapper..."
    gradle wrapper --gradle-version 8.2
fi

# Clean and build
echo "🧹 Cleaning..."
./gradlew clean

echo "🔨 Building debug APK..."
./gradlew assembleDebug

echo ""
echo "✅ Build complete!"
echo ""
echo "📦 APK location:"
echo "   app/build/outputs/apk/debug/app-debug.apk"
echo ""
echo "📲 To install on device:"
echo "   adb install app/build/outputs/apk/debug/app-debug.apk"
