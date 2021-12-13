#!/bin/sh -eux

GOCOMPAT_VERSION="v0.3.0"
GOTESTSUM_VERSION="0.4.0"

curl -sL https://github.com/smola/gocompat/releases/download/${GOCOMPAT_VERSION}/gocompat_linux_amd64.tar.gz | tar xzvf - -C /usr/bin gocompat_linux_amd64

mv /usr/bin/gocompat_linux_amd64 /usr/bin/gocompat

curl -sL https://github.com/gotestyourself/gotestsum/releases/download/v${GOTESTSUM_VERSION}/gotestsum_${GOTESTSUM_VERSION}_linux_amd64.tar.gz | tar xfzv - -C /usr/bin gotestsum
