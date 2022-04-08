#!/bin/bash -xue

# update apt repositories and install missing utilities
# add GitHub packages Debian repository for gh
curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg | dd of=/usr/share/keyrings/githubcli-archive-keyring.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" | \
      tee /etc/apt/sources.list.d/github-cli.list > /dev/null
apt update
apt install -y gpg gh

# remove origin and use GitHub App (reflected on filesystem and globally active)
git remote remove origin
git remote add origin "https://${GITHUB_TOKEN_USR}:${GITHUB_TOKEN_PSW}@github.com/camunda/zeebe.git"

# configure Jenkins GitHub user for Maven container
git config --global user.email "ci@camunda.com"
git config --global user.name "zeebe[bot]"

# setup maven central gpg keys
gpg -q --allow-secret-key-import --import --no-tty --batch --yes "${GPG_SEC_KEY}"
gpg -q --import --no-tty --batch --yes "${GPG_PUB_KEY}"
rm "${GPG_SEC_KEY}" "${GPG_PUB_KEY}"
