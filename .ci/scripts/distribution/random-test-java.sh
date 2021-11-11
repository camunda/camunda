#!/bin/bash -eux

# specialized script to run tests with random elements without flaky test detection

# getconf is a POSIX way to get the number of processors available which works on both Linux and macOS
LIMITS_CPU=${LIMITS_CPU:-$(getconf _NPROCESSORS_ONLN)}
MAVEN_PARALLELISM=${MAVEN_PARALLELISM:-$LIMITS_CPU}
SUREFIRE_FORK_COUNT=${SUREFIRE_FORK_COUNT:-}
JUNIT_THREAD_COUNT=${JUNIT_THREAD_COUNT:-}
MAVEN_PROPERTIES=(
  -DskipITs
  -DskipChecks
  -DtestMavenId=4
  -Dsurefire.rerunFailingTestsCount=0
  -Dmaven.javadoc.skip=true
)

if [ ! -z "$SUREFIRE_FORK_COUNT" ]; then
  MAVEN_PROPERTIES+=("-DforkCount=$SUREFIRE_FORK_COUNT")
  # if we know the fork count, we can limit the max heap for each fork to ensure we're not OOM killed
  export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS} -XX:MaxRAMFraction=$((MAVEN_PARALLELISM * SUREFIRE_FORK_COUNT))"
fi

if [ ! -z "$JUNIT_THREAD_COUNT" ]; then
  MAVEN_PROPERTIES+=("-DjunitThreadCount=$JUNIT_THREAD_COUNT")
fi

mvn -o -B --fail-never -T "${MAVEN_PARALLELISM}" -s "${MAVEN_SETTINGS_XML}" \
  -P parallel-tests,include-random-tests \
  "${MAVEN_PROPERTIES[@]}" \
  test
