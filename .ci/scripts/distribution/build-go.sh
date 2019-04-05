#!/bin/sh -eux

export CGO_ENABLED=0

ORG_DIR=${GOPATH}/src/github.com/zeebe-io

mkdir -p ${ORG_DIR}
ln -s ${PWD} ${ORG_DIR}/zeebe

go get -u github.com/jstemmer/go-junit-report

cd ${ORG_DIR}/zeebe/clients/go
make install-deps

make test 2>&1 | go-junit-report > TEST-go.xml

cd ${ORG_DIR}/zeebe/clients/zbctl
make test 2>&1 | go-junit-report > TEST-zbctl.xml

./build.sh
