#!/bin/bash

trap 'echo Terminated $0; exit' INT;

if [ -z $1 ]
then
  echo "Please provide at least 1 namespace name! Usage: ./runLoadTests.sh namespace1 namespace2 etc"
  exit 1
fi

namespaces=$@

for n in $namespaces; do
    echo "Start load test for: $n"
    cd $n
    kubens $n
    make clean zeebe starter worker
    cd -
    echo ""
done
