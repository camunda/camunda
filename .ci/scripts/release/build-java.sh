#!/bin/bash -xeu

export JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -XX:MaxRAMFraction=$((LIMITS_CPU))"

# single quote the !development profile as this can cause issues with shell substitution
mvn -B -s ${MAVEN_SETTINGS_XML} -DskipTests clean install -Pchecks,prepare-offline -P '!development'
