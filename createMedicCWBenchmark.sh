#!/bin/bash

set -euxo pipefail

# Ensure you are on zeebe-cluster
kubectx gke_zeebe-io_europe-west1-b_zeebe-cluster

# switch do develop
git checkout develop

# get latest changes
git fetch
git pull origin develop

# switch to cw branch
git checkout medic-cw-benchmarks
git pull origin medic-cw-benchmarks

# update kw branch
git merge develop --no-edit
git push origin medic-cw-benchmarks

# create new kw image and deploy benchmark
./setupKWBenchmark.sh

# delete older benchmark
cd benchmarks/setup/

nameOfOldestBenchmark=$(ls | grep medic-cw- | sort | head -n 1)

./stopBenchmarks.sh $nameOfOldestBenchmark
./deleteBenchmark.sh $nameOfOldestBenchmark

# commit that change

git commit -am "test(benchmark): rm $nameOfOldestBenchmark"
git push origin medic-cw-benchmarks
