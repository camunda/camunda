#!/bin/bash
set -exo pipefail

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

if [ -z $1 ]
then
  echo "Please provide a benchmark name! Format should be 'YOUR_NAME-TOPIC'"
  exit 1
fi
benchmark=$1

# Check if docker daemon is running
if ! docker info >/dev/null 2>&1; then
    echo "Docker daemon does not seem to be running, make sure it's running and retry"
    exit 1
fi

mvn clean install -DskipTests -T1C

docker build --build-arg DISTBALL=dist/target/zeebe-distribution-*.tar.gz --build-arg APP_ENV=dev -t "gcr.io/zeebe-io/zeebe:$benchmark" .
docker push "gcr.io/zeebe-io/zeebe:$benchmark"

cd benchmarks/project

sed_inplace "s/:SNAPSHOT/:$benchmark/" docker-compose.yml
docker-compose build
docker-compose push
git restore -- docker-compose.yml

cd ../setup/

./newBenchmark.sh "$benchmark"

cd "$benchmark"

# calls OS specific sed inplace function
sed_inplace 's/camunda\/zeebe/gcr.io\/zeebe-io\/zeebe/' zeebe-values.yaml
sed_inplace "s/SNAPSHOT/$benchmark/" zeebe-values.yaml
sed_inplace "s/starter:zeebe/starter:$benchmark/" starter.yaml
sed_inplace "s/starter:zeebe/starter:$benchmark/" simpleStarter.yaml
sed_inplace "s/starter:zeebe/starter:$benchmark/" timer.yaml
sed_inplace "s/worker:zeebe/worker:$benchmark/" worker.yaml

make zeebe starter worker
