#!/bin/bash

set -euxo pipefail

# Make sure git index is clean
git diff-index --quiet HEAD -- || \
	(echo "You have a dirty git index, please clean it"; exit 1)
test -z "$(git ls-files --exclude-standard --others)" || \
	(echo "You have untracked files, please clean your git repo"; exit 1)

# Ensure you are on zeebe-cluster
kubectx gke_zeebe-io_europe-west1-b_zeebe-cluster

# switch do main
git checkout main

# get latest changes
git fetch
git pull origin main

# switch to cw branch
git checkout medic-cw-benchmarks
git pull origin medic-cw-benchmarks

# update kw branch
git merge main --no-edit
git push origin medic-cw-benchmarks

# create new kw image and deploy benchmark
./setupKWBenchmark.sh

# delete older benchmark
cd benchmarks/setup/

cw=$(date +%V)
if [ $cw -gt 4 ]
then
  nameOfOldestBenchmark=$(ls | grep medic-y- | sort | head -n 1)
  ./deleteBenchmark.sh $nameOfOldestBenchmark

  # commit that change
  git commit -am "test(benchmark): rm $nameOfOldestBenchmark"
  git push origin medic-cw-benchmarks

else
  set +x
  echo -e "\e[31m!!!!!!!!!!!!!!"
  echo -e "We currently not support to delete benchmarks, before calendar week 5. Our deletion logic is not sophisticated enough. Please delete the benchmark manually."
  echo -e "!!!!!!!!!!!!!!\e[0m"
  set -x
fi


# print out the name of the new benchmark so it can be easily copied
nameOfNewestBenchmark=$(ls | grep medic-y- | sort | tail -n 1)
echo "Finished creating new medic benchmark: $nameOfNewestBenchmark"
