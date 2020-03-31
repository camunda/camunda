# Kubernetes 

This folder contains information and can be seen as a quick reference in order to use kubernetes 
or kubectl.

## Create a namespace and switch to it

To ease the use of namespaces I recommend installing `kubens`, see
https://github.com/ahmetb/kubectx#installation for instructions.

```
kubectl create namespace $USER
kubens $USER
```

## Restart a pod

To restart a pod, just delete it.

```bash
kubectl delete <pod>
```

Be aware if this pod is a statefulset and has PVC, then this is not deleted!

## Kustomize

The [kustomize](kubernetes/kustomize/README.md) section contains several information how to use and setup kustomize
with kubectl.
