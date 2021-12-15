#!/bin/bash -xue

pushd clients/go/internal/embedded
echo "${RELEASE_VERSION}" > data/VERSION
"${GOPATH}/bin/go-bindata" -pkg embedded -o embedded.go -prefix data data/

git commit -am "build(project): update go embedded version data"
git push origin "${RELEASE_BRANCH}"

popd
pushd clients/go/cmd/zbctl
./build.sh
