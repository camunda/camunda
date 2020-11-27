#!/bin/bash -eux

# getconf is a POSIX way to get the number of processors available which works on both Linux and macOS
LIMITS_CPU=${LIMITS_CPU:-$(getconf _NPROCESSORS_ONLN)}
MAVEN_PARALLELISM=${MAVEN_PARALLELISM:-$LIMITS_CPU}
SUREFIRE_FORK_COUNT=${SUREFIRE_FORK_COUNT:-}
MAVEN_PROPERTIES=(
  -Dzeebe.it.skip
  -DtestMavenId=1
  -Dsurefire.rerunFailingTestsCount=7
)
tmpfile=$(mktemp)

if [ ! -z "$SUREFIRE_FORK_COUNT" ]; then
  MAVEN_PROPERTIES+=("-DforkCount=$SUREFIRE_FORK_COUNT")
  # if we know the fork count, we can limit the max heap for each fork to ensure we're not OOM killed
  JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS} -XX:MaxRAMPercentage=$((100 / ($MAVEN_PARALLELISM * $SUREFIRE_FORK_COUNT)))"
fi

mvn -o -B --fail-never -T${MAVEN_PARALLELISM} -s ${MAVEN_SETTINGS_XML} verify -P skip-unstable-ci,parallel-tests "${MAVEN_PROPERTIES[@]}" | tee ${tmpfile}

status=${PIPESTATUS[0]}

if grep -q "\[WARNING\] Flakes:" ${tmpfile}; then

  tmpfile2=$(mktemp)

  awk '/^\[WARNING\] Flakes:.*$/{flag=1}/^\[ERROR\] Tests run:.*Flakes: [0-9]*$/{print;flag=0}flag' ${tmpfile} > ${tmpfile2}

  grep "\[ERROR\]   Run 1: " ${tmpfile2} | awk '{print $4}' >> ./FlakyTests.txt

  echo ERROR: Flaky Tests detected>&2

  exit 1
fi

if [[ $status != 0 ]]; then
  exit $status;
fi

if grep -q "\[INFO\] Build failures were ignored\." ${tmpfile}; then
  exit 1
fi
