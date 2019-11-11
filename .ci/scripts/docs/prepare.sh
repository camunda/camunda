#!/bin/sh -eux

MDBOOK_VERSION="v0.3.4"
LINKCHECK_VERSION="v0.5.0"

wget -O - https://github.com/rust-lang/mdBook/releases/download/${MDBOOK_VERSION}/mdbook-${MDBOOK_VERSION}-x86_64-unknown-linux-gnu.tar.gz | tar xzvf - -C /usr/bin mdbook
wget -O - https://github.com/Michael-F-Bryan/mdbook-linkcheck/releases/download/${LINKCHECK_VERSION}/mdbook-linkcheck-${LINKCHECK_VERSION}-x86_64-unknown-linux-gnu.tar.gz | tar xzvf - -C /usr/bin mdbook-linkcheck

apk add --no-cache rsync openssh
