# Setup a development cluster

> There are a few additional steps that need to be done when setting this up with a cloud provider, such as GCP.
> There is a GCP specific section at the end to help you do so.

In order to be able to properly test in a clustered setup, the development cluster requires
certain tools to be installed in it.

> The following assumes that the cluster was already created and configured locally, e.g. there
> a valid Kubernetes context configuration on your local machine

To set up a development cluster, first ensure you are using the right Kubernetes context pointing to
the cluster you want to set up.

> Note that unless otherwise stated, you only need to set up a cluster for development once

## Requirements

The same requirements apply here as the general project (e.g. Helm v3.x.x).

It's a good idea to update your Helm charts before proceeding.

```sh
helm repo update
```

## Monitoring

To monitor your cluster, we will set up a the [Prometheus Operator helm chart](https://github.com/helm/charts/tree/master/stable/prometheus-operator),
which will install the following components the operator and Grafana. There are some pre-requisites for Grafana first.

By default, Grafana is setup for Github OAuth, allowing users from the zeebe-io, camunda, and camunda-cloud organizations to login as editors. If you want to use it, you will need to deploy a secret which contains your Github OAuth Application's client ID and client secret, e.g.:

```yaml
---
apiVersion: v1
kind: Secret
metadata:
  name: auth-github-oauth
type: Opaque
stringData:
  client_id: <ID>
  client_secret: <SECRET>
```

Save this to a file `oauth-secret.yml`, and apply via `kubectl apply -f oauth-secret.yml`.

Additionally, the admin user will require a pre-defined secret for the username and password. Note that once this is set during the first run, changes to the secret will _not_ update the password - to do that, you need to either use the `grafana-cli` or the do it via the web interface.

Here's an example of a secret you would need to deploy:

```yaml
---
apiVersion: v1
kind: Secret
metadata:
  name: grafana-admin-password
type: Opaque
stringData:
  admin-user: <ADMIN USER>
  admin-password: <SUPER STRONG PASSWORD>
```

Save this to `admin.yml` and apply via `kubectl apply -f admin.yml`.

Once these secrets exist, you can now install the operator. To do so, run:

```sh
helm install metrics stable/prometheus-operator --atomic -f prometheus-operator-values.yml
```

If you want to set up an ingress for Grafana (e.g. public endpoint), you can run

```sh
kubectl apply -f grafana-load-balancer.yml
```

You can then obtain your Grafana URL by checking the ingress' external IP, e.g.

```sh
kubectl get svc metrics-grafana-loadbalancer -o "custom-columns=ip:status.loadBalancer.ingress[0].ip"
```

If you don't need an ingress, you can simply proxy the Grafana port so it's available locally:

```sh
kubectl port-forward svc/metrics-grafana-loadbalancer :80
```

## Linkerd

A lightweight alternative to Istio, you can also set up linkerd. In order to enable linkerd, it's a good idea to install the [linkerd CLI](https://linkerd.io/2/getting-started/#step-1-install-the-cli).

> If you're not sure if linkerd is already installed on your cluster, you can simply verify the installation first

To install Istio on your cluster, run:

```sh
kubectl apply -f linkerd-manifest.yml -f linkerd-service-monitor.yml
```

Then once finished, verify that the installation is correct:

```sh
linkerd check
```

# Google Cloud Platform

There are a few additional steps that need to be done when setting this up with a cloud provider, such as GCP.

As first you have to [create a new cluster in the Google cloud console](https://cloud.google.com/kubernetes-engine/docs/how-to/creating-a-cluster?hl=de). 

> Remember from the top level readme, to switch easily between clusters/namespaces, make sure to install kubectx and kubens.

On creating the new cluster and node pool make sure you are using `europe-west1-b` as zone. Otherwise the cluster can be a bit limited due to our billing settings.

## Setup SSD Storage class

After the GCP cluster is set up, make sure to create the SSD storage class, which will give you access to more performant persistent disks.

```
kubectl apply -f ssd-storageclass.yaml
```
