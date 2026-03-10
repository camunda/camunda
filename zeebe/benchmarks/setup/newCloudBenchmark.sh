#!/bin/bash
set -exo pipefail

# Contains OS specific sed function
. utils.sh

if [ -z $1 ]
then
  echo "Please provide an namespace name!"
  exit 1
fi

### Cloud load test helper script
### First parameter is used as namespace name
### For a new namespace a new folder will be created


namespace=$1

kubectl create namespace $namespace
cp -rv cloud-default/ $namespace
cd $namespace


# Update Makefile to use the namespace
sed_inplace "s/__NAMESPACE__/$namespace/" Makefile
