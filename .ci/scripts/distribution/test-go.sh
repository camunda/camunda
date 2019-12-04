#!/bin/sh -eux
ORG_DIR=${GOPATH}/src/github.com/zeebe-io

cd ${ORG_DIR}/zeebe/clients/go
go test -mod=vendor -v ./... | go-junit-report > TEST-go.xml
