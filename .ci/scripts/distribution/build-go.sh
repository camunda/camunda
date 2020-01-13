#!/bin/bash -eux
set -o pipefail

export CGO_ENABLED=0

ORG_DIR=${GOPATH}/src/github.com/zeebe-io

mkdir -p ${ORG_DIR}
ln -s ${PWD} ${ORG_DIR}/zeebe

go get -u github.com/jstemmer/go-junit-report

cd ${ORG_DIR}/zeebe/clients/go

PREFIX=github.com/zeebe-io/zeebe/clients/go
EXCLUDE=""

for file in internal/*; do
  EXCLUDE=$EXCLUDE$PREFIX/$file,
done

/usr/bin/gocompat compare --go1compat --exclude-package=$EXCLUDE ./...

cd ${ORG_DIR}/zeebe/clients/go/cmd/zbctl

./build.sh
