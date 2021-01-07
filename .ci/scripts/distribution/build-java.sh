#!/bin/sh -eux


export JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -XX:MaxRAMFraction=$((LIMITS_CPU))"

mvn -B -T$LIMITS_CPU -s ${MAVEN_SETTINGS_XML} -DskipTests clean install -Pchecks,spotbugs,prepare-offline
