#!/bin/sh -eux


export JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -XX:MaxRAMFraction=$((LIMITS_CPU))"

su jenkins -c "mvn -B -T$LIMITS_CPU -s settings.xml verify -P skip-unstable-ci,retry-tests,parallel-tests -Dzeebe.it.skip"
