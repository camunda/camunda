#!/bin/bash -eux

# getconf is a POSIX way to get the number of processors available which works on both Linux and macOS
LIMITS_CPU=${LIMITS_CPU:-$(getconf _NPROCESSORS_ONLN)}
MAVEN_PARALLELISM=${MAVEN_PARALLELISM:-$LIMITS_CPU}
MAVEN_PROPERTIES=(
  -DskipUTs
  -DskipChecks
  -Dmaven.javadoc.skip=true
)

# make sure to specify the profiles used in the verify goal when running preparing to go offline, as
# these may require some additional plugin dependencies
mvn -B -T "${MAVEN_PARALLELISM}" -s "${MAVEN_SETTINGS_XML}" package -Pprepare-offline,parallel-tests "${MAVEN_PROPERTIES[@]}"
