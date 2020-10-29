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
. benchmarks/setup/utils.sh

if [ -z $1 ]
then
  echo "Please provide a benchmark name! Format should be 'YOUR_NAME-TOPIC'"
  exit 1
fi
benchmark=$1

mvn clean install -DskipTests -T1C

docker build --build-arg DISTBALL=dist/target/zeebe-distribution-*.tar.gz --build-arg APP_ENV=dev -t "gcr.io/zeebe-io/zeebe:$benchmark" .
docker push "gcr.io/zeebe-io/zeebe:$benchmark"

cd benchmarks/setup/

./newBenchmark.sh "$benchmark"

cd "$benchmark"

# calls OS specific sed inplace function
sed_inplace 's/camunda\/zeebe/gcr.io\/zeebe-io\/zeebe/' zeebe-values.yaml
sed_inplace "s/SNAPSHOT/$benchmark/" zeebe-values.yaml

make zeebe starter worker
