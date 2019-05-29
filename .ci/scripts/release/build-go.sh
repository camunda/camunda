#!/bin/bash -xue

export CGO_ENABLED=0

ORG_DIR=${GOPATH}/src/github.com/zeebe-io

mkdir -p ${ORG_DIR}
ln -s ${PWD} ${ORG_DIR}/zeebe

cd ${ORG_DIR}/zeebe/clients/zbctl
./build.sh
