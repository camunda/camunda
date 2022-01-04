#!/bin/bash -xue

if [[ ! "$RELEASE_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Skipping updating the compat version as ${RELEASE_VERSION} is not a stable version"
  exit 0
fi

pushd clients/go
"${GOPATH}/bin/gocompat" save ./...

git commit -am "build(project): update go versions"
git push origin "${RELEASE_BRANCH}"
