# Zeebe Development Kubernetes

Since [Kubernetes](https://kubernetes.io) is Zeebe's recommended platform, it becomes necessary for our end to end tests to run against a Kubernetes deployment, both locally (for smoke tests) and in the cloud. In order to do so, the following repository contains templates, scripts, and application manifests to speed our development workflow and allow us to test Zeebe in an as-close-to-production setup as possible.

> Everything here should de deployable within a certified k8s distribution, but as it's somewhat expensive to spin up a large cluster, you will probably be testing in the cloud most of the time.

- [Requirements](#requirements)
- [Directory layout](#directory-layout)
- [Deploying](#deploying-an-application)
- [Monitoring](#monitoring-an-application)
- [Linkerd](#linkerd)
- [Google Cloud](#google-cloud)
- [Links](#useful-links)

## Requirements

The project requires the following applications to be installed locally on your machine:

- [gcloud]() - required to create resources in our cloud, see [Google Cloud](#google-cloud)
- [kubectl](https://kubernetes.io/docs/tasks/tools/install-kubectl/) - CLI to manage k8s resources

> In order to use `kustomize`, make sure to install `kubectl >= 1.14` which integrates `kustomize` into `kubectl`, or you will have to install `kustomize` as a separate tool.

> You can alternatively install kubectl as a `gcloud` component by running the following: `gcloud components install kubectl`

The following are optional dependencies which can help debug or help your day to day workflow:

- [kubectx and kubens](https://github.com/ahmetb/kubectx) - quickly change context/namespace
- [linkerd](https://linkerd.io) - visualize network traffic across your pods from the command line (and more)
- [kind](https://kind.sigs.k8s.io/) - for testing locally; a lightweight [minikube](https://github.com/kubernetes/minikube) alternative which the Kubernetes projects uses for its own integration tests

## Directory layout

The directory is structured loosely against the [kubectl book recommendations](https://kubectl.docs.kubernetes.io/pages/app_composition_and_deployment/structure_directories.html): one `bases/` folder to reusable generic templates, and one folder per environment, e.g. `test/`, `staging/`, etc.

> As of now, we only target the `test/` environment, but in the long-run we should add a `staging` environment to run long-running clusters and perform chaos testing.

Under `bases/` you will find top-level reusable templates. Each of these is a `kustomize` application base which defines sane defaults for any component. For example, `bases/zeebe-broker` defines a default template for the Zeebe Broker service, including the service, some configuration files, and the broker stateful set. As it is a top-level base, it does not make sense to include the number of partitions here (which is application dependent), but setting some labels or specifying the node ID is always going to be same.

Applications will then glue together various components (either using one of the base or a one-off definition); applications meant to be deployable in multiple environments should therefore also have a reusable base.

> There are currently no applications meant to be deployed in multiple environments, but whoever creates the first one should set that up.

To see an example of deployable kustomize application, you can browse to `test/zeebe-bench`, our benchmark application.

## Deploying an application

Applications can be deployed by browsing to their respective folder under the environment you wish to deploy into, e.g. `test/zeebe-bench`.

> You can preview the resources that will be created by running `kubectl kustomize .`; the output of this may be large, so you'll probably want to pipe this to your favorite tool, e.g. `vi` or `less`.

Once there, you can deploy the application by running:

```sh
kubectl apply -k .
```

To delete the application, run:

```sh
kubectl delete -k .
```

> This will not delete your persistent volume claims (e.g. the broker creates one per replica)

## Monitoring an application

You can monitor the status of your application by running:

```sh
kubectl get -k .
```

`kubectl` does not support watching more than one resource type unfortunately, but you can do it yourself with standard `watch`:

```sh
watch -n1 'kubectl get -k .`
```

## Linkerd

[linkerd](https://linkerd.io) is a [service mesh](https://buoyant.io/2017/04/25/whats-a-service-mesh-and-why-do-i-need-one/). It consists of a control plane (the linkerd operator and related resources) once in the cluster, under the `linkerd` namespace, and a data plane, which is injected into the applications that are to be "meshed".

> In order for your services to be meshed, you have to inject linkerd in them, as [described here](https://linkerd.io/2/tasks/adding-your-service/). You can alternatively check out the `test/zeebe-bench` application for a concrete example, specifically `test/zeebe-bench/kustomization.yaml`.

By injecting linkerd into your service, each pod now will have at least two containers: your expected containers (e.g. `zeebe-broker`) and the `linkerd-proxy` container. 

> Since pods now contain more than one container, when tailing the logs of a pod you will now need to specify the container, e.g. `kubectl logs -f zeebe-broker-0 -c broker`

Assuming you've installed linkerd locally, you can then visualize the topology of your application using the dashboard `linkerd dashboard &`, [tap](https://linkerd.io/2/reference/cli/tap/) into the traffic, get network [stats](https://linkerd.io/2/reference/cli/stat/) about specific pods (or split it into [routes](https://linkerd.io/2/reference/cli/routes/)), check the [top](https://linkerd.io/2/reference/cli/top/) routes, etc.

> Linkerd simplifies fault-injection for us (see [the docs](https://linkerd.io/2/tasks/fault-injection/)), but it requires us to have a compatible, configurable backend, something that's easy when you use gRPC or something but harder to do with custom protocols.

Finally, linkerd provides transparent, automatic encryption between nodes, which takes us one step further to allow us to easily test TLS.

## Google Cloud

In order to create resources in the cloud, you will need to setup the [Google Cloud SDK]. Once installed, you'll need to authenticate and setup the correct k8s context, which you can do by running the following:

```
gcloud init
gcloud config set project zeebe-io
gcloud container clusters get-credentials default --region europe-west1-b
gcloud auth configure-docker
```

> This will require you to enter your Google Account credentials once; make sure to use one which has access to our Google Cloud project!

## Useful links

- [kubectl book](https://kubectl.docs.kubernetes.io/)
- [kubectl cheat sheet](https://kubernetes.io/docs/reference/kubectl/cheatsheet/)
