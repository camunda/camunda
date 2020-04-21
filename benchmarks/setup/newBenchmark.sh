#!/bin/bash
set -exo pipefail

if [ -z $1 ]
then
  echo "Please provide an namespace name!"
  exit 1
fi

### Benchmark helper script
### First parameter is used as namespace name
### For a new namespace a new folder will be created


namespace=$1

kubectl create namespace $namespace
kubens $namespace
cp -rv default/ $namespace
cd $namespace

sed -i "s/default/$namespace/g" Makefile starter.yaml timer.yaml simpleStarter.yaml worker.yaml
