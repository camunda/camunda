#!/bin/sh -eux
ORG_DIR=${GOPATH}/src/github.com/zeebe-io

cd ${ORG_DIR}/zeebe/clients/go
make test | go-junit-report > TEST-go.xml

cd ${ORG_DIR}/zeebe/clients/zbctl
make test | go-junit-report > TEST-zbctl.xml

