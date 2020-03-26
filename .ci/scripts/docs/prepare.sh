#!/bin/sh -eux

apt-get update
apt-get install -y curl rsync openssh-client python3 git

MDBOOK_VERSION="v0.3.6"
LINKCHECK_VERSION="v0.5.0"

curl -sL https://github.com/rust-lang/mdBook/releases/download/${MDBOOK_VERSION}/mdbook-${MDBOOK_VERSION}-x86_64-unknown-linux-gnu.tar.gz | tar xzvf - -C /usr/bin mdbook
curl -sL https://github.com/Michael-F-Bryan/mdbook-linkcheck/releases/download/${LINKCHECK_VERSION}/mdbook-linkcheck-${LINKCHECK_VERSION}-x86_64-unknown-linux-gnu.tar.gz | tar xzvf - -C /usr/bin mdbook-linkcheck
