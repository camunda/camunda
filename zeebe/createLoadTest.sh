#!/bin/bash
set -eo pipefail

# The purpose of this script is to make it easier for developers to setup new load tests.
# As input the load test name is expected, which is used for the docker image tag and k8 namespace.
# This script does the following:
#
# 1. Build the current branch
# 2. Build the docker dev image
# 3. Publish the docker image
# 4. Setups new namespace
# 5. Configures the load test
# 6. Deploy's the load test

# Contains OS specific sed function
source load-tests/setup/utils.sh

function printUsageAndExit {
  printf "\nUsage ./createLoadTest <load-test-name>\n"
  exit 1
}

if [[ -z $1 ]]
then
  echo "<load-test-name> not provided"
  printUsageAndExit
fi
loadtest=$1

# DNS Label regex, see https://regex101.com/r/vjsrEy/2
if [[ ! $loadtest =~ ^[a-z0-9]([-a-z0-9]{0,61}[a-z0-9])?$ ]]; then
  echo "<load-test-name> '$loadtest' not a valid DNS label"
  echo "See https://kubernetes.io/docs/concepts/overview/working-with-objects/names/#dns-label-names"
  printUsageAndExit
fi

# Check if docker daemon is running
if ! docker info >/dev/null 2>&1; then
    echo "Docker daemon does not seem to be running, make sure it's running and retry"
    exit 1
fi

set -x

mvn clean install -DskipTests -DskipChecks -T1C

docker build --build-arg DISTBALL=dist/target/camunda-zeebe-*.tar.gz -t "gcr.io/zeebe-io/zeebe:$loadtest" --target app .
docker push "gcr.io/zeebe-io/zeebe:$loadtest"

cd load-tests/project

sed_inplace "s/:SNAPSHOT/:$loadtest/" docker-compose.yml
# Use --no-cache to force rebuild the image for the load test application. Without this changes to zeebe-client were not picked up. This can take longer to build.
docker-compose build --no-cache
docker-compose push
git restore -- docker-compose.yml

cd ../setup/

./newLoadTest.sh "$loadtest"

cd "$loadtest"

# calls OS specific sed inplace function
sed_inplace 's/camunda\/zeebe/gcr.io\/zeebe-io\/zeebe/g' zeebe-values.yaml
sed_inplace "s/SNAPSHOT/$loadtest/g" zeebe-values.yaml

make load-test
