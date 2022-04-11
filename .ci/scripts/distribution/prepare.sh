#!/bin/sh -eux

apt-get -qq update
apt-get install --no-install-recommends -qq -y jq libatomic1

useradd -u 1000 -m jenkins

chmod -R a+x .

# remove origin and use GitHub App (reflected on filesystem and globally active)
git remote remove origin
git remote add origin "https://${GITHUB_TOKEN_USR}[bot]:${GITHUB_TOKEN_PSW}@github.com/camunda/zeebe.git"

# configure Jenkins GitHub user for Maven container
git config --global user.email "ci@camunda.com"
git config --global user.name "${GITHUB_TOKEN_USR}[bot]"

echo "hello" >> some-test-file
git add some-test-file
git commit -m"test ignore"
git push --set-upstream origin 9069-bot-git-name

