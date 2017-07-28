#!/bin/bash -xue

MDBOOK_VERSION=0.0.21

# go to docs folder
cd docs/

# dowload mdbook
curl -sL https://github.com/azerupi/mdBook/releases/download/${MDBOOK_VERSION}/mdBook-${MDBOOK_VERSION}-x86_64-unknown-linux-gnu.tar.gz | tar xzvf -

# build docs
./mdbook build

# upload
rsync -azv --delete-after "book/" jenkins_docs_zeebe_io@vm29.camunda.com:"/var/www/camunda/docs.zeebe.io/" -e "ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no"
