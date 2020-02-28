# Welcome

Welcome to the Zeebe Benchmark folder :wave:

## Introduction

Make sure you have access to our Google Cloud environment. Ask the team or SRE for help, if necessary.

### Requirements

In order to setup a benchmark you need to have the google cloud cli (gcloud), kubectl and helm on
your machine installed.

Follow these guide's to install each of them:

 * https://cloud.google.com/sdk/docs/
 * https://cloud.google.com/kubernetes-engine/docs/how-to/cluster-access-for-kubectl
 * https://helm.sh/docs/intro/install/

Some of the necessary steps you need to do are:

```
gcloud components install kubectl

gcloud init
gcloud config set project zeebe-io
gcloud container clusters get-credentials default --region europe-west1-b
gcloud auth configure-docker
```

## What's next?

 * [Read the docs](docs/README.md)
 * [Run a Benchmark](setup/README.md)
 * [Change the Project](project/README.md)
