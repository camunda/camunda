#!/bin/bash -xue

export CGO_ENABLED=0

ORG_DIR=${GOPATH}/src/github.com/camunda-cloud
GOBINDATA_VERSION="3.1.3"

mkdir -p ${ORG_DIR}
ln -s ${PWD} ${ORG_DIR}/zeebe

curl -sL https://github.com/go-bindata/go-bindata/archive/v${GOBINDATA_VERSION}.tar.gz | tar xz
cd go-bindata-${GOBINDATA_VERSION}
go install ./...

cd ${ORG_DIR}/zeebe/clients/go/internal/embedded
echo ${RELEASE_VERSION} > data/VERSION
go-bindata -pkg embedded -o embedded.go -prefix data data/

git commit -am "chore(project): update go embedded version data"
git push origin ${RELEASE_BRANCH} 

cd ${ORG_DIR}/zeebe/clients/go/cmd/zbctl
./build.sh
