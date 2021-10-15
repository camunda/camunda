# Welcome

Welcome to the Zeebe Benchmark folder :wave:

## Introduction

Make sure you have access to our Google Cloud environment. Ask the team or SRE for help, if necessary.

### Requirements

In order to setup a benchmark you need to have several tools on your machine installed.

Follow these guide's to install each of them:

 * gcloud https://cloud.google.com/sdk/install
 * Kubectl https://kubernetes.io/de/docs/tasks/tools/install-kubectl/
 * Helm 3.*  https://helm.sh/docs/intro/install/
 * docker https://docs.docker.com/install/
 * kubens/kubectx https://github.com/ahmetb/kubectx
 * OPTIONAL go https://golang.org/doc/install

Some of the necessary steps you need to do are:

```sh
## Init gcloud
gcloud init
gcloud config set project zeebe-io
gcloud container clusters get-credentials zeebe-cluster --zone europe-west1-b --project zeebe-io

## to use google cloud docker registry
gcloud auth configure-docker

## install kubectl via gcloud cli
gcloud components install kubectl

## install helm
curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/master/scripts/get-helm-3
chmod 700 get_helm.sh
./get_helm.sh

## add zeebe as helm repo
helm version
helm repo add zeebe https://helm.camunda.io
helm repo add stable https://charts.helm.sh/stable
helm repo update

## install kubens
curl -LO https://raw.githubusercontent.com/ahmetb/kubectx/master/kubens
install kubens /usr/local/bin/
```

## Best Practices Windows

 Running the benchmarks on Windows is possible with the help of the [Windows Subsystem for Linux](https://docs.microsoft.com/en-us/windows/wsl/install-win10).
The setup changes slightly compared to the Linux setup.

These are the components to install on Windows:
* Docker

These are the components to install within the WSL:
* gcloud https://cloud.google.com/sdk/install?hl=de
* Kubectl https://kubernetes.io/de/docs/tasks/tools/install-kubectl/
* Helm 3.*  https://helm.sh/docs/intro/install/
* kubens/kubectx https://github.com/ahmetb/kubectx

When following the instructions above, execute all commands that deal with Docker in a Windows shell, and exeucte all other commands in the WSL shell.


## What's next?

 * [Read the docs](docs/README.md)
 * [Run a Benchmark](setup/README.md)
 * [Change the Project](project/README.md)
