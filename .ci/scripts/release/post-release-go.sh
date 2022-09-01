#!/bin/bash

# Publish Go tag for the release
git tag "clients/go/v${RELEASE_VERSION}"
git push origin "clients/go/v${RELEASE_VERSION}"

# Prepare Go version for the next release
pushd "clients/go/internal/embedded" || exit $?

echo "${DEVELOPMENT_VERSION}" > data/VERSION
go-bindata -pkg embedded -o embedded.go -prefix data/ data/

git commit -am "build(project): prepare next development version (Go client)"
git push origin "${RELEASE_BRANCH}"

