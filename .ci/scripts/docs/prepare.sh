#!/bin/sh -eux

wget https://github.com/zeebe-io/mdBook/releases/download/zeebe-io/mdbook

chmod +x mdbook

apk add --no-cache rsync openssh
