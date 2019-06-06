#!/bin/sh -eux


export JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -XX:MaxRAMFraction=$((LIMITS_CPU))"

su jenkins -c "mvn -o -B -T$LIMITS_CPU -s settings.xml verify -P skip-unstable-ci,parallel-tests -Dzeebe.it.skip -DtestMavenId=1"
