#!/bin/sh -eux

wget -sL https://github.com/rust-lang/mdBook/releases/download/v0.3.4/mdbook-v0.3.4-x86_64-unknown-linux-gnu.tar.gz | tar xzvf -

chmod +x mdbook

apk add --no-cache rsync openssh
