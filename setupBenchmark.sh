#!/bin/bash
set -exo pipefail

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
