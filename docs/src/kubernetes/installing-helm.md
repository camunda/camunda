## Zeebe Helm Charts

[Helm](https://github.com/helm/helm) is a package manager for Kubernetes resources. Helm allows us to install a set of components by just referencing a packange name and it allows us to override configurations to accomodate these packages to different scenarios. Helm also provide dependency management between  charts, meaning that charts can depend on other charts allowing us to aggregate a set of components together that can be installed with a single command. 


As part of the Zeebe project, we are providing 3 Zeebe Helm Charts: 
- **Zeebe Cluster**: Deploys a Zeebe Cluster with 3 brokers using the camunda/zeebe docker image. This Chart depends on ElasticSearch Helm Chart and optionally on Kibana Helm Chart. This chart is hosted in the following repository, where you can find more information about its configuration: [http://github.com/zeebe-io/zeebe-cluster-helm/](http://github.com/zeebe-io/zeebe-cluster-helm/)
- **Zeebe Operate**: Deploys Zeebe Operate which conects to an existing ElasticSearch. This chart source code can be located here: [http://github.com/zeebe-io/zeebe-operate-helm/](http://github.com/zeebe-io/zeebe-operate-helm/)
- **Zeebe Full** (Parent): Deploys a Zeebe Cluster + Operate + Ingress Controller. This parent chart can be located here: [http://github.com/zeebe-io/zeebe-full-helm/](http://github.com/zeebe-io/zeebe-full-helm/)

![Charts](/kubernetes/zeebe-helm-charts.png)

### Initializing Helm in your Cluster

You need to have `kubectl` already configured against a Kubernetes Cluster to install Helm (server side/tiller) into your cluster. 
> Here you can download the [helm-service-account-role.yaml](/kubernetes/helm-service-account-role.yaml) file

You also need to have the `helm` CLI tool installed as listed in the prerequisites section.

```
> kubectl apply -f helm-service-account-role.yaml
> helm init --service-account helm --upgrade 
```

This install Helm server side components in your cluster and it will enable the `helm` cli tool to install Helm Charts into your environment. 


### Add Zeebe Helm Repository

The next step is to add the Zeebe official Helm Chart repository to your installation. Once this is done, Helm will be able to fetch and install Charts hostested in [http://helm.zeebe.io](http://helm.zeebe.io).
```
> helm repo add zeebe https://helm.zeebe.io
> helm repo update
```

Once this is done, we are ready to install any of the Helm Charts hosted in the official Zeebe Helm Chart repo. 


### Install Zeebe Full Helm Chart (Zeebe Cluster + Operate + Ingress Controller)

In this section we are going to install all the available Zeebe components inside a Kubernetes Cluster. Notice that this Kubernetes cluster can have already running services and Zeebe is going to installed just as another set of services. 

```
> helm install --name <RELEASE NAME> zeebe/zeebe-full
```

> Note: change <RELEASE NAME> with a name of your choice
> Notice that you can add the `-n` flag to specify in which Kubernetes namespace the components should be installed.

Installing all the components in a cluster requires all the Docker images to be downloaded to the remote cluster, depending on which Cloud Provider you are using, the amount of time that it will take to fetch all the images will vary. 

If you are using [Kubernetes KIND](https://github.com/kubernetes-sigs/kind) add `-f kind-values.yaml`
> The `kind-values.yaml` file can be [downloaded here](/kubernetes/kind-values.yaml).
```
> helm install --name <RELEASE NAME> zeebe/zeebe-full -f kind-values.yaml
```

This will deploy the same components but with a set of parameters tailored to a local environment setup. 
> Note that all the Docker images will be downloaded to your local KIND cluster, so it might take some time for the services to get started. 

You can check the progress of your deployment by checking if the Kubernetes PODs are up and running with:
```
> kubectl get pods
```

which returns something like: 
```
NAME                                                   READY   STATUS    RESTARTS   AGE
elasticsearch-master-0                                 1/1     Running   0          4m6s
elasticsearch-master-1                                 1/1     Running   0          4m6s
elasticsearch-master-2                                 1/1     Running   0          4m6s
<RELEASE NAME>-nginx-ingress-controller-5cf6dd7894-kc25s      1/1     Running   0          4m6s
<RELEASE NAME>-nginx-ingress-default-backend-f5454db5-j9vh6   1/1     Running   0          4m6s
<RELEASE NAME>-operate-5d4867d6d-h9zqw                        1/1     Running   0          4m6s
<RELEASE NAME>-zeebe-0                                        1/1     Running   0          4m6s
<RELEASE NAME>-zeebe-1                                        1/1     Running   0          4m6s
<RELEASE NAME>-zeebe-2                                        1/1     Running   0          4m6s
```

Check that each Pod has at least 1/1 running instances. You can always tail the logs of these pods by running:
```
> kubectl logs -f <POD NAME> 
```

In order to interact with the services inside the cluster you need to use `port-forward` to route traffic from your environment to the cluster. 
```
> kubectl port-forward svc/<RELEASE NAME>-zeebe 26500:26500
```

Now you can connect and execute operations against your newly created Zeebe cluster. 

> Notice that you need to keep `port-forward` running to be able to communicate with the remote cluster.

> Notice that accessing directly to the Zeebe Cluster using `kubectl port-forward` is recommended for development purposes. By default the Zeebe Helm Charts are not exposing the Zeebe Cluster via Ingress. If you want to uze `zbctl` or a local client/worker from outside the Kubernetes Cluster, you rely on `kubectl port-forward` to the Zeebe Cluster to communicate.
