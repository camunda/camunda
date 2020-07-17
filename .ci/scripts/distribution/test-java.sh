#!/bin/sh -eux


export JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -XX:MaxRAMFraction=$((LIMITS_CPU))"

mvn -o -B -T1 -s ${MAVEN_SETTINGS_XML} verify -P skip-unstable-ci,parallel-tests -Dzeebe.it.skip -DtestMavenId=1
