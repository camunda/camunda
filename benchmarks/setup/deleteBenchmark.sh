#!/bin/bash
set -exo pipefail

if [ -z $1 ]
then
  echo "Please provide an namespace name!"
  exit 1
fi

### Benchmark helper script
### First parameter is used as namespace name
### Given namespace will be completely deleted.


namespace=$1

kubens default
kubectl delete namespace $namespace --wait=false
rm -r $namespace
