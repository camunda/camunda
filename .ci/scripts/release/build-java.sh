#!/bin/bash -xeu

export JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -XX:MaxRAMFraction=$((LIMITS_CPU))"

mvn -B -s ${MAVEN_SETTINGS_XML} -DskipTests clean com.mycila:license-maven-plugin:check com.coveo:fmt-maven-plugin:2.9:check org.apache.maven.plugins:maven-dependency-plugin:3.1.1:go-offline install -Pprepare-offline
