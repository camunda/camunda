#!/bin/bash -xeu

OS=( linux windows darwin )
BINARY=( zbctl zbctl.exe zbctl.darwin )
SRC_DIR=$(dirname "${BASH_SOURCE[0]}")
DIST_DIR="$SRC_DIR/dist"

VERSION=${RELEASE_VERSION:-development}
COMMIT=${RELEASE_HASH:-$(git rev-parse HEAD)}

mkdir -p ${DIST_DIR}
rm -rf ${DIST_DIR}/*

for i in "${!OS[@]}"; do
	if [ $# -eq 0 ] || [ ${OS[$i]} = $1 ]; then
	    CGO_ENABLED=0 GOOS="${OS[$i]}" GOARCH=amd64 go build -a -tags netgo -ldflags "-w -X github.com/camunda/zeebe/clients/go/v8/cmd/zbctl/internal/commands.Version=${VERSION} -X github.com/camunda/zeebe/clients/go/v8/cmd/zbctl/internal/commands.Commit=${COMMIT}" -o "${DIST_DIR}/${BINARY[$i]}" "${SRC_DIR}/main.go" &
	fi
done

wait
