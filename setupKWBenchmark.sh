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

docker build --build-arg DISTBALL=dist/target/camunda-zeebe-*.tar.gz --build-arg APP_ENV=dev -t "gcr.io/zeebe-io/zeebe:$benchmark" .
docker push "gcr.io/zeebe-io/zeebe:$benchmark"

cd benchmarks/project

sed_inplace "s/:SNAPSHOT/:$benchmark/" docker-compose.yml
# Use --no-cache to force re-build the application. Without this flag, changes to zeebe-client were not picked up. This can take longer to build than usual.
docker-compose build --no-cache
docker-compose push
git restore -- docker-compose.yml

cd ../setup/

./newBenchmark.sh "$benchmark"

cd "$benchmark"

sed_inplace 's/camunda\/zeebe/gcr.io\/zeebe-io\/zeebe/' zeebe-values.yaml
sed_inplace "s/SNAPSHOT/$benchmark/" zeebe-values.yaml
sed_inplace "s/starter:SNAPSHOT/starter:$benchmark/" starter.yaml
sed_inplace "s/starter:SNAPSHOT/starter:$benchmark/" simpleStarter.yaml
sed_inplace "s/starter:SNAPSHOT/starter:$benchmark/" timer.yaml
sed_inplace "s/worker:SNAPSHOT/worker:$benchmark/" worker.yaml

make zeebe starter worker

git add .
git commit -m "test(benchmark): add $benchmark"
git push origin medic-cw-benchmarks
