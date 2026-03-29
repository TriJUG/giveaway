#!/usr/bin/env bash
# Build and run the TUI directly (bypasses Gradle's terminal wrapping).
# Usage:
#   ./run.sh
#   TRIJUG_API_URL=http://localhost:3001 TRIJUG_ADMIN_TOKEN=test ./run.sh
set -e
JAVA_HOME="${JAVA_HOME:-$HOME/.sdkman/candidates/java/25.0.2-tem}"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

./gradlew installDist -q
exec build/install/trijug/bin/trijug "$@"
