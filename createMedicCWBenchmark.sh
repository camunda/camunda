#!/bin/bash

set -euxo pipefail

cw=$(date +%V)
if [ $cw -gt 4 ]
then
  nameOfOldestBenchmark=$(ls | grep medic-cw- | sort | head -n 1)
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
nameOfNewestBenchmark=$(ls | grep medic-cw- | sort | tail -n 1)
echo "Finished creating new medic benchmark: $nameOfNewestBenchmark"
