# Manual set up a load test

Welcome to the manual set up of a load test. :wave:

There are two targets to run a load test against:

* [Self-Managed Zeebe Cluster](#load-testing-self-managed-zeebe-cluster)
* [Camunda Cloud Cluster](#load-testing-camunda-cloud-saas)

All guides are targeted at a Linux system.

## Requirements

Make sure you have the following installed:

* gcloud https://cloud.google.com/sdk/install
* Kubectl https://kubernetes.io/de/docs/tasks/tools/install-kubectl/
* Helm 3.*  https://helm.sh/docs/intro/install/
* docker https://docs.docker.com/install/
* kubens/kubectx https://github.com/ahmetb/kubectx
* OPTIONAL go https://golang.org/doc/install

### Initial Setup

Some initial setup steps are required to connect to the Zeebe GKE cluster:

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

### Windows

Running the load tests with Windows is possible, with the help of the [Windows Subsystem for Linux](https://docs.microsoft.com/en-us/windows/wsl/install-win10).
The setup changes slightly compared to the Linux setup.

These are the components to install on Windows:
* Docker

These are the components to install within the WSL:
* gcloud https://cloud.google.com/sdk/install?hl=de
* Kubectl https://kubernetes.io/de/docs/tasks/tools/install-kubectl/
* Helm 3.*  https://helm.sh/docs/intro/install/
* kubens/kubectx https://github.com/ahmetb/kubectx

When following the instructions above, execute all commands that deal with Docker in a Windows shell, and execute all other commands in the WSL shell.

## Load testing Self-Managed Zeebe Cluster

### How to set up a load test namespace

Just run the `newLoadTest.sh` with your preferred new namespace name.

Like:

```
. ./newLoadTest.sh my-load-test-name
```

This will source and run the `newLoadTest.sh` script, which means it will
create a new k8 namespace and switch to it via `kubens`. Furthermore, a new folder
will be created with the given name. If you used `.` before `./newLoadTest.sh`
the script will also change your directory after running, so you can directly start
to configure your load test.

### How to configure a load test

The load test configuration is done via the `camunda-platform-values.yaml` file (copied to your namespace folder).
You can also modify the Makefile to pass additional helm arguments if needed.

#### Use different Camunda Snapshot

If you want to use your own or a different Camunda snapshot then you could do the following.

**Build the docker image:**

```bash
# builds the dist
mvn clean install -T1C -DskipTests -pl dist -am
# builds the a new camunda docker image
docker build --build-arg DISTBALL=dist/target/camunda-zeebe-*.tar.gz -t gcr.io/zeebe-io/zeebe:SNAPSHOT-$(date +%Y-%m-%d)-$(git rev-parse --short=8 HEAD) --target app -f camunda.Dockerfile .
# pushes the image to our docker registry
docker push gcr.io/zeebe-io/zeebe:SNAPSHOT-$(date +%Y-%m-%d)-$(git rev-parse --short=8 HEAD)
```

Update the `camunda-platform-values.yaml` file in your namespace folder and set the newly created image tag.

The changes should look similar to this:

```yaml
global:
  image:
    tag: SNAPSHOT-2024-01-15-abcd1234
orchestration:
  image:
    tag: SNAPSHOT-2024-01-15-abcd1234
```

### How to run a load test

After you have set up your load test namespace and made changes to your configuration, you can start your load test.

To install/upgrade both the Camunda Platform and the load test:

```shell
make install
```

Or install/upgrade them separately:

```shell
# Install/upgrade the Camunda Platform (includes leader balancer cronjob)
make install-platform

# Install/upgrade the load test (starter, worker, etc.)
make install-load-test
```

The Camunda Platform deployment automatically sets up a leader balancing cronjob that runs every 10 minutes to rebalance cluster leaders.

This will deploy the Camunda Platform (including `orchestration cluster`, `elastic`) and load test applications (e.g. `starter` and `worker`).

### How to clean up a load test

After you're done with your load test you should remove the remaining namespace.
In order to do this easily, just run:

```
./deleteLoadTest.sh my-load-test-name
```

This will switch to the default namespace, delete the given namespace, and delete the corresponding folder.

## Running on stable/non-spot VMs

You may sometimes want to run load tests on non-spot (or non-preemptible) VMs, for example, if you
want to test for slow memory leaks.

To do this, use the `install-platform-stable` Makefile target:

```shell
# Deploy the Camunda Platform on stable nodes
make install-platform-stable

# Then deploy the load test normally
make install-load-test
```

You can also use the `template-stable` job to generate templates:

```shell
# Spits out the rendered templates targeting stable nodes
make template-stable
```

The `clean` job works regardless and will clean up both the platform and load test deployments.

## Installing via Helm chart directly

As an alternative to the `newLoadTest.sh` script, you can install the load test using the [camunda-load-tests](https://github.com/camunda/camunda-load-tests-helm) Helm chart directly. The chart repository contains a [detailed guide](https://github.com/camunda/camunda-load-tests-helm/blob/main/charts/camunda-load-tests/README.md).

```shell
# Add the load test chart to the local repository
helm repo add camunda-load-tests https://camunda.github.io/camunda-load-tests-helm/
# Install a new Helm Chart release to the current namespace
helm install this-is-a-load-test camunda-load-tests/camunda-load-tests
```

To apply configuration changes, either edit the existing [values](https://github.com/camunda/camunda-load-tests-helm/blob/main/charts/camunda-load-tests/values.yaml) file in the repository (and apply them via `-f`) or set configurations via the `--set` flag. For more information, see also the [related Helm documentation](https://helm.sh/docs/chart_template_guide/values_files/).

## Load testing Camunda Cloud SaaS

_You need a Kubernetes Cluster at your disposal to run the load test itself, which then connects to your Camunda Cloud Cluster._

Follow the guide [here](https://github.com/camunda/zeebe-benchmark-helm/blob/main/charts/zeebe-benchmark/README.md#running-against-saas).
