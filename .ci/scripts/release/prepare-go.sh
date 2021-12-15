#!/bin/sh -eux

# configure Jenkins GitHub user for GO container
git config --global user.email "ci@camunda.com"
git config --global user.name "${GITHUB_TOKEN_USR}"

# install binary tools; these are installed under $GOPATH/bin
# NOTE: this will not work on Go > 1.16 - instead we'll have to replace `go get -u` with
# `go install` (and possibly GO111MODULE=on is not required)
export CGO_ENABLED=0
export GO111MODULE=on
go get -u "github.com/smola/gocompat/...@v0.3.0"
go get -u "github.com/go-bindata/go-bindata/...@v3"
