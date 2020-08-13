#!/bin/bash

GO_OS=${GO_OS:-"linux"}

function detect_os {
    # Detect the OS name
    case "$(uname -s)" in
      Darwin)
        host_os=darwin
        ;;
      Linux)
        host_os=linux
        ;;
      *)
        echo "Unsupported host OS. Must be Linux or Mac OS X." >&2
        exit 1
        ;;
    esac

   GO_OS="${host_os}"
}

detect_os

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

if [ "${GO_OS}" == "darwin" ]; then
    sed -i '' -e "s/default/$namespace/g" Makefile starter.yaml timer.yaml simpleStarter.yaml worker.yaml
else
    sed -i -e "s/default/$namespace/g" Makefile starter.yaml timer.yaml simpleStarter.yaml worker.yaml
fi

