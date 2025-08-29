#!/bin/bash
set -eo pipefail

# The purpose of this script is to make it easier for developers to recreate load tests.
# As input the load test name is expected, which is used for the docker image tag and k8 namespace.
# This script does the following:
#
# 1. Build the current branch (zeebe + load-test-project)
# 2. Build the docker images
# 3. Publish the docker images
# 4. Redeploys the load test

# Contains OS specific sed function
source load-tests/setup/utils.sh

function printUsageAndExit {
  printf "\nUsage ./recreateLoadTest <load-test-name>\n"
  exit 1
}

if [[ -z $1 ]]
then
  echo "<load-test-name> not provided"
  printUsageAndExit
fi
loadtest=$1
pwd=$(pwd)

# DNS Label regex, see https://regex101.com/r/vjsrEy/2
if [[ ! $loadtest =~ ^[a-z0-9]([-a-z0-9]{0,61}[a-z0-9])?$ ]]; then
  echo "<load-test-name> '$loadtest' not a valid DNS label"
  echo "See https://kubernetes.io/docs/concepts/overview/working-with-objects/names/#dns-label-names"
  printUsageAndExit
fi

set +e
ns=$(kubectl get namespace "$loadtest")
set -e
if [[ ! "$ns" ]]; then
  echo "Perhaps you meant to use ./createLoadTest.sh instead. Or consider switching your k8s context"
  exit 1
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

cd "$pwd/load-tests/project"
sed_inplace "s/:SNAPSHOT/:$loadtest/" docker-compose.yml
# Use --no-cache to force rebuild the image for the load test application. Without this changes to zeebe-client were not picked up. This can take longer to build.
docker-compose build --no-cache
docker-compose push
git restore -- docker-compose.yml

# Restart the pods by deleting them, this is not a rolling update
# `make update` would not restart the pods, but once they restart the new image will be pulled because of pullPolicy: Always
#
# TODO: apply rolling updates with helm
# see https://helm.sh/docs/howto/charts_tips_and_tricks/#automatically-roll-deployments
kubectl -n "$loadtest" get pods -l app.kubernetes.io/name=zeebe-cluster-helm -o=jsonpath='{.items..metadata.name}' \
  | xargs kubectl -n "$loadtest" delete pod

# Return where you started
cd "$pwd"
