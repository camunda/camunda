#!/bin/bash -xue

# update apt repositories and install missing utilities
apt update
apt install -y gpg

# remove origin and use GitHub App (reflected on filesystem and globally active)
git remote remove origin
git remote add origin https://${GITHUB_TOKEN_USR}:${GITHUB_TOKEN_PSW}@github.com/camunda-cloud/zeebe.git

# configure Jenkins GitHub user for Maven container
git config --global user.email "ci@camunda.com"
git config --global user.name "${GITHUB_TOKEN_USR}"

# setup maven central gpg keys
gpg -q --allow-secret-key-import --import --no-tty --batch --yes ${GPG_SEC_KEY}
gpg -q --import --no-tty --batch --yes ${GPG_PUB_KEY}
rm ${GPG_SEC_KEY} ${GPG_PUB_KEY}
