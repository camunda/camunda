#!/bin/bash -xue

GOCOMPAT_VERSION="v0.2.0"

if [[ "$RELEASE_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  curl -sL https://github.com/smola/gocompat/releases/download/${GOCOMPAT_VERSION}/gocompat_linux_amd64.tar.gz | tar xzvf - -C /usr/bin gocompat_linux_amd64
  mv /usr/bin/gocompat_linux_amd64 /usr/bin/gocompat
  chmod +x /usr/bin/gocompat

  cd ${GOPATH}/src/github.com/camunda-cloud/zeebe/clients/go
  gocompat save ./...

  git commit -am "chore(project): update go versions"
  git push origin ${RELEASE_BRANCH}
else
  echo "Skipping updating the compat version as $RELEASE_VERSION is not a stable version"
fi
