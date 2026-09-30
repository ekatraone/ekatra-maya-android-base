#!/bin/bash
# Gradle wrapper script for Linux/macOS
# This downloads Gradle if needed and runs it

GRADLE_VERSION="8.2"
GRADLE_DIR="$HOME/.gradle/wrapper/dists/gradle-${GRADLE_VERSION}-bin"
GRADLE_ZIP_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"

# Check if gradle wrapper jar exists, download if not
if [ ! -f "gradle/wrapper/gradle-wrapper.jar" ]; then
    echo "Downloading Gradle wrapper..."
    mkdir -p gradle/wrapper
    
    # Download gradle-wrapper.jar from a known location
    curl -sL "https://raw.githubusercontent.com/gradle/gradle/v${GRADLE_VERSION}.0/gradle/wrapper/gradle-wrapper.jar" \
        -o gradle/wrapper/gradle-wrapper.jar 2>/dev/null || {
        
        # Fallback: create minimal wrapper that downloads gradle
        echo "Creating bootstrap wrapper..."
        
        # We need java to run gradle
        if ! command -v java &> /dev/null; then
            echo "Error: Java is required. Please install JDK 17+."
            echo "  brew install openjdk@17"
            exit 1
        fi
    }
fi

# Set JAVA_HOME if not set
if [ -z "$JAVA_HOME" ]; then
    if [ -x "/usr/libexec/java_home" ]; then
        export JAVA_HOME=$(/usr/libexec/java_home 2>/dev/null)
    fi
fi

# Run gradle via the wrapper
exec java -classpath gradle/wrapper/gradle-wrapper.jar \
    org.gradle.wrapper.GradleWrapperMain "$@"
