#!/bin/bash -eux

# install the TestContainers Cloud agent and our local repository
curl -L -o testcontainers-agent https://app.testcontainers.cloud/download/testcontainers-cloud-agent_linux_x86-64
chmod +x testcontainers-agent

echo "Starting TestContainers Cloud agent (output at /tmp/agent-output.log)..."
export TC_CLOUD_CONCURRENCY=${TC_CLOUD_CONCURRENCY:-4}
./testcontainers-agent \
  '--private-registry-url=http://localhost:5000' \
  '--private-registry-allowed-image-name-globs=*,*/*' > /tmp/agent-output.log 2>&1 &
./testcontainers-agent wait

# getconf is a POSIX way to get the number of processors available which works on both Linux and macOS
LIMITS_CPU=${LIMITS_CPU:-$(getconf _NPROCESSORS_ONLN)}
MAVEN_PARALLELISM=${MAVEN_PARALLELISM:-$LIMITS_CPU}
MAVEN_PROPERTIES=(
  -DskipTests
  -DskipChecks
  -Dmaven.javadoc.skip=true
)

# make sure to specify the profiles used in the verify goal when running preparing to go offline, as
# these may require some additional plugin dependencies
mvn -B -T2C -s "${MAVEN_SETTINGS_XML}" install \
  -Pspotbugs,prepare-offline,extract-flaky-tests \
  "${MAVEN_PROPERTIES[@]}"
