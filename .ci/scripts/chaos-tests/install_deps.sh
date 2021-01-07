#!/bin/sh

set -ex
apk --no-cache add bash make curl openssl diffutils jq

pip install chaostoolkit
chaos --version

kubectl_version=$(curl -s https://storage.googleapis.com/kubernetes-release/release/stable.txt)
curl -LO https://storage.googleapis.com/kubernetes-release/release/${kubectl_version}/bin/linux/amd64/kubectl
install kubectl /usr/local/bin/
kubectl version --client

curl -LO https://raw.githubusercontent.com/ahmetb/kubectx/master/kubens
install kubens /usr/local/bin/
