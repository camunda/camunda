#!/bin/bash -eux


export JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -XX:MaxRAMFraction=$((LIMITS_CPU))"

mvn -v

tmpfile=$(mktemp)

mvn -o -B --fail-never -T$LIMITS_CPU -s ${MAVEN_SETTINGS_XML} verify -pl clients/java -DtestMavenId=3 | tee ${tmpfile}

status=${PIPESTATUS[0]}

if [[ $status != 0 ]]; then
  exit $status;
fi

if grep -q "\[INFO\] Build failures were ignored\." ${tmpfile}; then
  exit 1
fi
