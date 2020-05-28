#!/bin/bash -eux

export JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -XX:MaxRAMFraction=$((LIMITS_CPU))"

tmpfile=$(mktemp)

mvn -o -B --fail-never -T$LIMITS_CPU -s ${MAVEN_SETTINGS_XML} verify -P skip-unstable-ci,parallel-tests -Dzeebe.it.skip -DtestMavenId=1 | tee ${tmpfile}

status=${PIPESTATUS[0]}

if [[ $status != 0 ]]; then
  exit $status;
fi

if grep -q "\[INFO\] Build failures were ignored\." ${tmpfile}; then
  exit 1
fi
