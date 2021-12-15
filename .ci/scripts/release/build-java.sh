#!/bin/bash -xeu
export JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -XX:MaxRAMFraction=$((LIMITS_CPU))"
mvn -B -T1C -s "${MAVEN_SETTINGS_XML}" -DskipTests clean install -Pchecks,prepare-offline
