#!/bin/bash -xeu

OS=( linux windows darwin )
BINARY=( zbctl zbctl.exe zbctl.darwin )
SRC_DIR=$(dirname "${BASH_SOURCE[0]}")
DIST_DIR="$SRC_DIR/dist"

VERSION=${RELEASE_VERSION:-development}
COMMIT=$(git rev-parse HEAD)

mkdir -p ${DIST_DIR}
rm -rf ${DIST_DIR}/*

for i in "${!OS[@]}"; do
    CGO_ENABLED=0 GOOS="${OS[$i]}" GOARCH=amd64 go build -a -tags netgo -ldflags "-w -X github.com/zeebe-io/zeebe/clients/zbctl/cmd.Version=${VERSION} -X github.com/zeebe-io/zeebe/clients/zbctl/cmd.Commit=${COMMIT}" -o "${DIST_DIR}/${BINARY[$i]}" "${SRC_DIR}/main.go" &
done

wait
