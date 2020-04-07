# Setup a GCloud Cluster

As first you have to create a new cluster in the Google cloud console.
https://cloud.google.com/kubernetes-engine/docs/how-to/creating-a-cluster?hl=de
If you want to switch between clusters easily install `kubectx`.
On creating the new cluster and node pool make sure you are using `europe-west1-b` as zone.
Otherwise the cluster can be a bit limited due to our billing settings.

After the google cloud cluster is set up. You need to do some more initial steps to get our benchmarks
running.


## Setup Tiller Service Account

```
kubectl apply -f tiller-rbac-config.yaml
```

## Init helm

```
helm init --service-account tiller
```

## Setup SSD Storage class

```
kubectl apply -f ssd-storageclass.yaml
```
