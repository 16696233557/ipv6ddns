#!/bin/sh
# Gradle wrapper for POSIX. Generated wrapper script.
# Source: gradle/gradle v8.5.0 (gradlew)
DEFAULT_JVM_OPTS=""
APP_NAME="Gradle"
APP_BASE_NAME=$(basename "$0")

set -e
DIR=$(cd "$(dirname "$0")" && pwd)
APP_HOME=$DIR
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
JAVA_CMD="${JAVA_HOME:-}/bin/java"
[ -x "$JAVA_CMD" ] || JAVA_CMD="java"
exec "$JAVA_CMD" $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain "$@"
