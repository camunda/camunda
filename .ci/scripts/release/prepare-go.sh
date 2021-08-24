#!/bin/sh -eux

# configure Jenkins GitHub user for GO container
git config --global user.email "ci@camunda.com"
git config --global user.name "${GITHUB_TOKEN_USR}"

GOCOMPAT_VERSION="v0.2.0"

curl -sL https://github.com/smola/gocompat/releases/download/${GOCOMPAT_VERSION}/gocompat_linux_amd64.tar.gz | tar xzvf - -C /usr/bin gocompat_linux_amd64
mv /usr/bin/gocompat_linux_amd64 /usr/bin/gocompat

