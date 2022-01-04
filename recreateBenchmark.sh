#!/bin/bash
set -eo pipefail

# The purpose of this script is to make it easier for developers to recreate benchmarks.
# As input the benchmark name is expected, which is used for the docker image tag and k8 namespace.
# This script does the following:
#
# 1. Build the current branch (zeebe + benchmark-project)
# 2. Build the docker images
# 3. Publish the docker images
# 4. Redeploys the benchmark

# Contains OS specific sed function
source benchmarks/setup/utils.sh

function printUsageAndExit {
  printf "\nUsage ./createBenchmark <benchmark-name>\n"
  exit 1
}

if [[ -z $1 ]]
then
  echo "<benchmark-name> not provided"
  printUsageAndExit
fi
benchmark=$1
pwd=$(pwd)

# DNS Label regex, see https://regex101.com/r/vjsrEy/2
if [[ ! $benchmark =~ ^[a-z0-9]([-a-z0-9]{0,61}[a-z0-9])?$ ]]; then
  echo "<benchmark-name> '$benchmark' not a valid DNS label"
  echo "See https://kubernetes.io/docs/concepts/overview/working-with-objects/names/#dns-label-names"
  printUsageAndExit
fi

set +e
ns=$(kubectl get namespace "$benchmark")
set -e
if [[ ! "$ns" ]]; then
  echo "Perhaps you meant to use ./createBenchmark.sh instead. Or consider switching your k8s context"
  exit 1
fi

# Check if docker daemon is running
if ! docker info >/dev/null 2>&1; then
    echo "Docker daemon does not seem to be running, make sure it's running and retry"
    exit 1
fi

set -x

mvn clean install -DskipTests -DskipChecks -T1C

docker build --build-arg DISTBALL=dist/target/camunda-cloud-zeebe-*.tar.gz --build-arg APP_ENV=dev -t "gcr.io/zeebe-io/zeebe:$benchmark" .
docker push "gcr.io/zeebe-io/zeebe:$benchmark"

cd "$pwd/benchmarks/project"
sed_inplace "s/:SNAPSHOT/:$benchmark/" docker-compose.yml
# Use --no-cache to force rebuild the image for the benchmark application. Without this changes to zeebe-client were not picked up. This can take longer to build.
docker-compose build --no-cache
docker-compose push
git restore -- docker-compose.yml

# Restart the pods by deleting them, this is not a rolling update
# `make update` would not restart the pods, but once they restart the new image will be pulled because of pullPolicy: Always
#
# TODO: apply rolling updates with helm
# see https://helm.sh/docs/howto/charts_tips_and_tricks/#automatically-roll-deployments
kubectl -n "$benchmark" get pods -l app.kubernetes.io/name=zeebe-cluster-helm -o=jsonpath='{.items..metadata.name}' \
  | xargs kubectl -n "$benchmark" delete pod

# Return where you started
cd "$pwd"