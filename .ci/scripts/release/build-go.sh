#!/bin/bash -xue

export CGO_ENABLED=0

ORG_DIR=${GOPATH}/src/github.com/zeebe-io
GOBINDATA_VERSION="3.1.3"

mkdir -p ${ORG_DIR}
ln -s ${PWD} ${ORG_DIR}/zeebe

# update go client API
if [[ "$RELEASE_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  gocompat save --path=clients/go/.gocompat.json clients/go/...
  git commit -am "chore(project): update Go client API data"
else
  echo "Version $RELEASE_VERSION"
fi

curl -sL https://github.com/go-bindata/go-bindata/archive/v${GOBINDATA_VERSION}.tar.gz | tar xz 
cd go-bindata-${GOBINDATA_VERSION}
go install ./...
 
cd ${ORG_DIR}/zeebe/clients/go/internal/embedded 
echo ${RELEASE_VERSION} > data/VERSION 
go-bindata -pkg embedded -o embedded.go -prefix data data/ 

# configure Jenkins GitHub user
git config --global user.email "ci@camunda.com"
git config --global user.name "camunda-jenkins"

# trust github ssh key
mkdir ~/.ssh/
ssh-keyscan github.com >> ~/.ssh/known_hosts

git commit -am "chore(project): update go embedded version data"

cd ${ORG_DIR}/zeebe/clients/go/cmd/zbctl
./build.sh
