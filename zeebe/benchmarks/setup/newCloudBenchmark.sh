#!/bin/bash
set -exo pipefail

# Contains OS specific sed function
. utils.sh

if [ -z $1 ]
then
  echo "Please provide an namespace name!"
  exit 1
fi

### Cloud Benchmark helper script
### First parameter is used as namespace name
### For a new namespace a new folder will be created


namespace=$1

kubectl create namespace $namespace
cp -rv cloud-default/ $namespace
cd $namespace

# calls OS specific sed inplace function
sed_inplace "s/default/$namespace/g" Makefile starter.yaml timer.yaml simpleStarter.yaml worker.yaml
