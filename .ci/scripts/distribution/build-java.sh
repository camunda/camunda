#!/bin/sh -eux


export JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -XX:MaxRAMFraction=$((LIMITS_CPU))"

su jenkins -c "mvn -B -T2 -s settings.xml -DskipTests clean com.mycila:license-maven-plugin:check com.coveo:fmt-maven-plugin:check install -Pspotbugs"

su jenkins -c "mvn -B -T$LIMITS_CPU -s settings.xml verify -P skip-unstable-ci,parallel-tests"
