#!/bin/sh -eux

# configure Jenkins GitHub user for GO container
git config --global user.email "ci@camunda.com"
git config --global user.name "ci.automation[bot]"

# install binary tools; these are installed under $GOPATH/bin
# NOTE: this will not work on Go > 1.16 - instead we'll have to replace `go get -u` with
# `go install` (and possibly GO111MODULE=on is not required)
export CGO_ENABLED=0
export GO111MODULE=on

# We cannot build tooling dependencies from scratch, as these might suddenly rely on a dependency
# does not build on our system anymore. Better to fetch the pre-compiled binary, unfortunately.
# With Go 1.17, it might be possible that `go install` will solve this issue, so we should try again
curl -sL https://github.com/smola/gocompat/releases/download/v0.3.0/gocompat_linux_amd64.tar.gz | tar -xz
mv gocompat_linux_amd64 $GOPATH/bin/gocompat

# go-bindata does not provided pre-compiled binaries, so we have to build it ourselves. That said,
# we should switch to go:embed once we update the go version we use.
go get -u "github.com/go-bindata/go-bindata/...@v3"

# Verify binary is available under $GOPATH/bin
if ! command -v go-bindata > /dev/null 2>&1; then
  echo "Failed to install go-bindata, go-bindata is not available on the path"
  exit 1
fi

if ! command -v gocompat > /dev/null 2>&1; then
  echo "Failed to install gocopmat, gocompat is not available on the path"
  exit 1
fi
