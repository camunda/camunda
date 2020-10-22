#!/bin/sh -eux

GOCOMPAT_VERSION="v0.2.0"

curl -sL https://github.com/smola/gocompat/releases/download/${GOCOMPAT_VERSION}/gocompat_linux_amd64.tar.gz | tar xzvf - -C /usr/bin gocompat_linux_amd64
mv /usr/bin/gocompat_linux_amd64 /usr/bin/gocompat

