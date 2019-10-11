#!/bin/sh -eux


export JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -XX:MaxRAMFraction=$((LIMITS_CPU))"

mvn -v

mvn -o -B -T$LIMITS_CPU -s .ci/settings.xml verify -pl clients/java -DtestMavenId=3
