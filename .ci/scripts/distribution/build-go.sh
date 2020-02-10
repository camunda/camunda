#!/bin/bash -eux
set -o pipefail

export CGO_ENABLED=0

ORG_DIR=${GOPATH}/src/github.com/zeebe-io

mkdir -p ${ORG_DIR}
ln -s ${PWD} ${ORG_DIR}/zeebe

cd ${ORG_DIR}/zeebe/clients/go

cd ${ORG_DIR}/zeebe/clients/go/cmd/zbctl

./build.sh
