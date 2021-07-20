#!/bin/bash -xeu

export GITHUB_TOKEN=${GITHUB_TOKEN_PSW}
export GITHUB_ORG=camunda-cloud
export GITHUB_REPO=zeebe

curl -sL  https://github.com/github-release/github-release/releases/download/v0.10.0/linux-amd64-github-release.bz2 | bzip2 -fd - > github-release
chmod +x github-release
GITHUB_RELEASE=${PWD}/github-release

${GITHUB_RELEASE} release --user ${GITHUB_ORG} --repo ${GITHUB_REPO} --tag ${RELEASE_VERSION} --draft --name "Zeebe ${RELEASE_VERSION}" --description ""

git tag clients/go/v${RELEASE_VERSION}
git push origin clients/go/v${RELEASE_VERSION}

function upload {
  pushd ${1}

  local artifact=${2}
  local checksum=${artifact}.sha1sum

  sha1sum ${artifact} > ${checksum}

  ${GITHUB_RELEASE} upload --user ${GITHUB_ORG} --repo ${GITHUB_REPO} --tag ${RELEASE_VERSION} --name "${artifact}" --file "${artifact}"
  ${GITHUB_RELEASE} upload --user ${GITHUB_ORG} --repo ${GITHUB_REPO} --tag ${RELEASE_VERSION} --name "${checksum}" --file "${checksum}"

  popd
}

upload dist/target camunda-cloud-zeebe-${RELEASE_VERSION}.tar.gz
upload dist/target camunda-cloud-zeebe-${RELEASE_VERSION}.zip
upload clients/go/cmd/zbctl/dist zbctl
upload clients/go/cmd/zbctl/dist zbctl.exe
upload clients/go/cmd/zbctl/dist zbctl.darwin
