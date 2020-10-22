#!/bin/bash -xue

# configure Jenkins GitHub user
git config --global user.email "ci@camunda.com"
git config --global user.name "camunda-jenkins"

# trust github ssh key
mkdir -p ~/.ssh/
ssh-keyscan github.com >> ~/.ssh/known_hosts

# setup maven central gpg keys
gpg -q --allow-secret-key-import --import --no-tty --batch --yes ${GPG_SEC_KEY}
gpg -q --import --no-tty --batch --yes ${GPG_PUB_KEY}
rm ${GPG_SEC_KEY} ${GPG_PUB_KEY}
