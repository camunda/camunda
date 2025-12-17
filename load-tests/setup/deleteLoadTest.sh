#!/bin/bash
set -exo pipefail

if [ -z "$1" ]
then
  echo "Please provide a namespace name!"
  exit 1
fi


### Load test helper script
### First parameter is used as namespace name
### Given namespace will be completely deleted.

namespace=${1//\//} # remove trailing slashes

kubectl delete namespace "$namespace" --wait=false
rm -r "$namespace"
