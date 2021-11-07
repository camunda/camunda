#!/bin/bash -xeu

export JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -XX:MaxRAMFraction=$((LIMITS_CPU))"

mvn -B -s ${MAVEN_SETTINGS_XML} -DskipTests clean install -Pprepare-offline -PcheckFormat,-autoFormat
