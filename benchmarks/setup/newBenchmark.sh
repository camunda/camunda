#!/bin/bash

# Contains OS specific sed function
. utils.sh

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
cp -rv default/ $namespace
cd $namespace

# calls OS specific sed inplace function
sed_inplace "s/default/$namespace/g" Makefile starter.yaml timer.yaml simpleStarter.yaml worker.yaml

# get latest updates from zeebe repo
helm repo add camunda https://helm.camunda.io # skips if already exists
helm repo update
