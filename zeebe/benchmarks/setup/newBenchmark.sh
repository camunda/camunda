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
sed_inplace "s/default/$namespace/g" Makefile

# get latest updates from zeebe repo
helm repo add zeebe-benchmark https://zeebe-io.github.io/benchmark-helm # skips if already exists
helm repo update
