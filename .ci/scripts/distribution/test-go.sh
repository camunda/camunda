#!/bin/sh -eux
ORG_DIR=${GOPATH}/src/github.com/zeebe-io

cd ${ORG_DIR}/zeebe/clients/go
go test -v -mod=vendor  ./... 2>&1 | go-junit-report > TEST-go.xml
