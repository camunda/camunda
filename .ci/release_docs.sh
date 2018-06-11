#!/bin/bash -xue

MDBOOK_VERSION=v0.1.5

# go to docs folder
cd docs/

# dowload mdbook
#curl -sL https://github.com/rust-lang-nursery/mdBook/releases/download/${MDBOOK_VERSION}/mdbook-${MDBOOK_VERSION}-x86_64-unknown-linux-gnu.tar.gz | tar xzvf -
curl -o mdbook -sL https://github.com/zeebe-io/mdBook/releases/download/zeebe-io/mdbook
chmod +x mdbook

# build docs
./mdbook build

# upload
rsync -azv --delete-after "book/" jenkins_docs_zeebe_io@vm29.camunda.com:"/var/www/camunda/docs.zeebe.io/" -e "ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no"
