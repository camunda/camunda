#!/bin/sh -eux
ORG_DIR=${GOPATH}/src/github.com/camunda

cd "${ORG_DIR}/zeebe/clients/go"

gotestsum --raw-command --junitfile TEST-go.xml go test -mod=vendor -v -json ./... 2>&1
