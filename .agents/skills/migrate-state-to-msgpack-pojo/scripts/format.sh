#!/usr/bin/env bash
# Formats the given Java files with google-java-format taken from the Gradle cache.
# Fallback for when `./mvnw spotless:apply` cannot resolve the snapshot modules offline.
set -euo pipefail
cache="${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1"
gjf=$(ls "$cache"/com.google.googlejavaformat/google-java-format/*/*/google-java-format-*.jar | grep -v sources | tail -1)
guava=$(find "$cache" -name 'guava-3*.jar' | grep -v sources | head -1)
commonmark=$(find "$cache" -name 'commonmark-*.jar' | grep -v sources | tr '\n' ':')
exports=""
for p in api file parser tree util code; do
  exports="$exports --add-exports jdk.compiler/com.sun.tools.javac.$p=ALL-UNNAMED"
done
# shellcheck disable=SC2086
java $exports -cp "$gjf:$guava:$commonmark" com.google.googlejavaformat.java.Main -i "$@"
