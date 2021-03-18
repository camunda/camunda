#!/bin/bash
set -exuo pipefail

# Check if docker daemon is running
if ! docker info >/dev/null 2>&1; then
    echo "Docker daemon does not seem to be running, make sure it's running and retry"
    exit 1
fi

# Contains OS specific sed function
source ./benchmarks/setup/utils.sh

mvn clean install -DskipTests -T1C

cw=$(date +%V)
commitHash=$(git rev-parse --short HEAD)
benchmark="medic-cw-$cw-$commitHash-benchmark"

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

sed_inplace 's/camunda\/zeebe/gcr.io\/zeebe-io\/zeebe/' zeebe-values.yaml
sed_inplace "s/SNAPSHOT/$benchmark/" zeebe-values.yaml
sed_inplace "s/starter:zeebe/starter:$benchmark/" starter.yaml
sed_inplace "s/starter:zeebe/starter:$benchmark/" simpleStarter.yaml
sed_inplace "s/starter:zeebe/starter:$benchmark/" timer.yaml
sed_inplace "s/worker:zeebe/worker:$benchmark/" worker.yaml

make zeebe starter worker

git add .
git commit -m "add $benchmark"
git push origin medic-cw-benchmarks
