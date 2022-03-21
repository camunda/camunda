#!/bin/bash
set -eo pipefail

# The purpose of this script is to make it easier for developers to setup new benchmarks.
# As input the benchmark name is expected, which is used for the docker image tag and k8 namespace.
# This script does the following:
#
# 1. Build the current branch
# 2. Build the docker dev image
# 3. Publish the docker image
# 4. Setups new namespace
# 5. Configures the benchmark
# 6. Deploy's the benchmark

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

# DNS Label regex, see https://regex101.com/r/vjsrEy/2
if [[ ! $benchmark =~ ^[a-z0-9]([-a-z0-9]{0,61}[a-z0-9])?$ ]]; then
  echo "<benchmark-name> '$benchmark' not a valid DNS label"
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

docker build --build-arg DISTBALL=dist/target/camunda-zeebe-*.tar.gz --build-arg APP_ENV=dev -t "gcr.io/zeebe-io/zeebe:$benchmark" .
docker push "gcr.io/zeebe-io/zeebe:$benchmark"

cd benchmarks/project

sed_inplace "s/:SNAPSHOT/:$benchmark/" docker-compose.yml
# Use --no-cache to force rebuild the image for the benchmark application. Without this changes to zeebe-client were not picked up. This can take longer to build.
docker-compose build --no-cache
docker-compose push
git restore -- docker-compose.yml

cd ../setup/

./newBenchmark.sh "$benchmark"

cd "$benchmark"

# calls OS specific sed inplace function
sed_inplace 's/camunda\/zeebe/gcr.io\/zeebe-io\/zeebe/g' zeebe-values.yaml
sed_inplace "s/SNAPSHOT/$benchmark/g" zeebe-values.yaml
sed_inplace "s/starter:SNAPSHOT/starter:$benchmark/" starter.yaml
sed_inplace "s/starter:SNAPSHOT/starter:$benchmark/" simpleStarter.yaml
sed_inplace "s/starter:SNAPSHOT/starter:$benchmark/" timer.yaml
sed_inplace "s/worker:SNAPSHOT/worker:$benchmark/" worker.yaml

make zeebe starter worker
