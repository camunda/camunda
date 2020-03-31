## Prerequisites
In order to use Kubernetes you need to have the following tools installed in your local environment: 
- `kubectl`: Kubernetes Control CLI tool: installed and connected to your cluster
- `helm`: Kubernetes Helm CLI tool

You also need a Kubernetes Cluster, here you have several options:
  - Local for Development you can use: [Kubernetes KIND](https://github.com/kubernetes-sigs/kind), Minikube, MicroK8s
  - Remote: Google GKE, Azure AKS, Amazon EKS, etc.

> Notice that you can use free trials from different cloud providers to create Kubernetes Cluster to test Zeebe in the cloud. 

Optional tools related to [Zeebe](http://zeebe.io)
- Zeebe Modeler: to model/modify business processes [Zeebe Modeler Releases](https://github.com/zeebe-io/zeebe-modeler/releases)
- Zeebe CTL(`zbctl`): command line tool to interact with a Zeebe Cluster (local/remote). You can get the `zbctl` tool from the official [Zeebe Release Page](https://github.com/zeebe-io/zeebe/releases)


