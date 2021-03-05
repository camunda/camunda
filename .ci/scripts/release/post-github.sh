#!/bin/bash

cd ${GOPATH}/src/github.com/camunda-cloud/zeebe/clients/go/internal/embedded

echo ${DEVELOPMENT_VERSION} > data/VERSION
go-bindata -pkg embedded -o embedded.go -prefix data/ data/

git commit -am "chore(project): prepare next development version (Go client)"
git push origin ${RELEASE_BRANCH}

