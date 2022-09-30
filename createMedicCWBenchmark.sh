#!/bin/bash

set -euxo pipefail

cw=$(date +%V)
if [ $cw -gt 4 ]
then
  cw_to_delete=$((cw-4))
  namesOfOldestBenchmarks=$(ls | grep "medic-cw-$cw_to_delete")
  for oldBenchmarkName in $namesOfOldestBenchmarks; do
    ./deleteBenchmark.sh $oldBenchmarkName
    # commit that change
    git commit -am "test(benchmark): rm $oldBenchmarkName"
  done

  git push origin medic-cw-benchmarks

else
  set +x
  echo -e "\e[31m!!!!!!!!!!!!!!"
  echo -e "We currently not support to delete benchmarks, before calendar week 5. Our deletion logic is not sophisticated enough. Please delete the benchmark manually."
  echo -e "!!!!!!!!!!!!!!\e[0m"
  set -x
fi


# print out the names of the new benchmarks so they can be easily copied
namesOfNewestBenchmarks=$(ls | grep "medic-cw-$cw")
for newBenchmarkName in $namesOfNewestBenchmarks; do
  echo "Finished creating new medic benchmark: $newBenchmarkName"
done
