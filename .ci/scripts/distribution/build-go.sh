#!/bin/bash -eux
set -o pipefail

export CGO_ENABLED=0

ORG_DIR=${GOPATH}/src/github.com/camunda-cloud

mkdir -p "${ORG_DIR}"
ln -s "${PWD}" "${ORG_DIR}/zeebe"

cd "${ORG_DIR}/zeebe/clients/go"

PREFIX=github.com/camunda-cloud/zeebe/clients/go
EXCLUDE=""

for file in {internal,cmd/zbctl/internal}/*; do
  EXCLUDE="$EXCLUDE --exclude-package $PREFIX/$file"
done

# we actually do want word-splitting here
# shellcheck disable=SC2086
/usr/bin/gocompat compare --go1compat ${EXCLUDE} ./...

cd "${ORG_DIR}/zeebe/clients/go/cmd/zbctl"

./build.sh
