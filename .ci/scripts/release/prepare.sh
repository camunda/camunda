#!/bin/bash -xue

# update apt repositories and install missing utilities
# add GitHub packages Debian repository for gh

apt update
apt install -y gpg

# install using apt fails due to https://github.com/cli/cli/issues/6175 Workaround is to get it from git release
curl -L https://github.com/cli/cli/releases/download/v2.14.7/gh_2.14.7_linux_amd64.deb --output /tmp/gh.deb
dpkg -i /tmp/gh.deb

# remove origin and use GitHub App (reflected on filesystem and globally active)
git remote remove origin
git remote add origin "https://${GITHUB_TOKEN_USR}:${GITHUB_TOKEN_PSW}@github.com/camunda/zeebe.git"

# configure Jenkins GitHub user for Maven container
git config --global user.email "ci@camunda.com"
git config --global user.name "ci.automation[bot]"

# setup maven central gpg keys
gpg -q --allow-secret-key-import --import --no-tty --batch --yes "${GPG_SEC_KEY}"
gpg -q --import --no-tty --batch --yes "${GPG_PUB_KEY}"
rm "${GPG_SEC_KEY}" "${GPG_PUB_KEY}"
