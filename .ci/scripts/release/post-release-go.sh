#!/bin/bash

cd "clients/go/internal/embedded"

echo "${DEVELOPMENT_VERSION}" > data/VERSION
go-bindata -pkg embedded -o embedded.go -prefix data/ data/

git commit -am "build(project): prepare next development version (Go client)"
git push origin "${RELEASE_BRANCH}"

