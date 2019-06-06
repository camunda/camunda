#!/bin/bash -xeu

cd dist/target

export GITHUB_TOKEN=${GITHUB_TOKEN_PSW}

# create checksum files
sha1sum zeebe-distribution-${RELEASE_VERSION}.tar.gz > zeebe-distribution-${RELEASE_VERSION}.tar.gz.sha1sum
sha1sum zeebe-distribution-${RELEASE_VERSION}.zip > zeebe-distribution-${RELEASE_VERSION}.zip.sha1sum

# do github release
curl -sL https://github.com/aktau/github-release/releases/download/v0.7.2/linux-amd64-github-release.tar.bz2 | tar xjvf - --strip 3

./github-release release --user zeebe-io --repo zeebe --tag ${RELEASE_VERSION} --draft --name "Zeebe ${RELEASE_VERSION}" --description ""

for f in zeebe-distribution-${RELEASE_VERSION}.{tar.gz,zip}{,.sha1sum}; do
    ./github-release upload --user zeebe-io --repo zeebe --tag ${RELEASE_VERSION} --name "${f}" --file "${f}"
done
