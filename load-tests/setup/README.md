# Manual set up a load test

Welcome to the manual setup of a load test. :wave:

There are two targets to run a load test against:

* [Self-Managed Zeebe Cluster](#load-testing-self-managed-zeebe-cluster)
* [Camunda Cloud Cluster](#load-testing-camunda-cloud-saas)

All guides are targeted at a Linux system.

## Requirements

To set up a load test from your local machine, you need several tools installed.

Follow these guides to install each of them:

* tsh (Teleport CLI) https://goteleport.com/docs/installation/
* Kubectl https://kubernetes.io/de/docs/tasks/tools/install-kubectl/
* Helm 3.*  https://helm.sh/docs/intro/install/
* docker https://docs.docker.com/install/
* kubens/kubectx https://github.com/ahmetb/kubectx
* OPTIONAL go https://golang.org/doc/install

For detailed instructions on accessing the benchmark cluster, see the [benchmark cluster access guide](https://github.com/camunda/infra-core/blob/stage/docs/kubernetes-cluster/benchmark-cluster-access.md).

Some of the necessary steps you need to take are:

```sh
## Authenticate to the benchmark cluster via Teleport
tsh login --proxy=camunda.teleport.sh:443
tsh kube login camunda-benchmark-prod

## Log in to the Harbor container registry
## Zeebe team members have push access by default.
## If you don't have access, request it in the #ask-infra Slack channel.
docker login registry.camunda.cloud

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

### Best Practices Windows

Running the load tests with Windows is possible, with the help of the [Windows Subsystem for Linux](https://docs.microsoft.com/en-us/windows/wsl/install-win10).
The setup changes slightly compared to the Linux setup.

These are the components to install on Windows:
* Docker

These are the components to install within the WSL:
* tsh (Teleport CLI) https://goteleport.com/docs/installation/
* Kubectl https://kubernetes.io/de/docs/tasks/tools/install-kubectl/
* Helm 3.*  https://helm.sh/docs/intro/install/
* kubens/kubectx https://github.com/ahmetb/kubectx

When following the instructions above, execute all commands that deal with Docker in a Windows shell, and execute all other commands in the WSL shell.

## Load testing Self-Managed Zeebe Cluster

### Default deployment

By default, a load test deploys the full Camunda Platform, including:

* **Orchestration cluster** (Gateway, Webapps incl. Identity, Operate, Tasklist and Zeebe brokers as Camunda application)
* **Elasticsearch** as secondary storage
* **Optimize** with history cleanup (1-day TTL)
* **Connectors** with OIDC authentication
* **Identity + Keycloak** for OIDC-based authentication

All components are configured in `camunda-platform-values.yaml`.

### How to set up a load test namespace

If you run `newBenchmark.sh` without arguments, it will display the following help message.

```sh
Usage: newBenchmark.sh <namespace> [secondaryStorage] [ttl_days] [enable_optimize]

Arguments:
  namespace          Base namespace name. Will be prefixed with "c8-" if missing.
  secondaryStorage   Optional. One of: elasticsearch, opensearch, postgresql, none. Default: elasticsearch.
  ttl_days           Optional. Positive integer for namespace TTL in days. Default: 1.
  enable_optimize    Optional. true|false to enable Optimize. Default: false.

Options:
  -h, --help         Show this help message.

Examples:
  ./newBenchmark.sh demo
  ./newBenchmark.sh perf opensearch 3 true
```

As you can see, you can create a test (namespace) by passing a name; other parameters are optional. Like, secondary storage, TTL, and whether Optimize should be enabled.

Example:

```sh
. ./newBenchmark.sh my-load-test-name
```

This will source and run the `newBenchmark.sh` script, which means it will create a new Kubernetes namespace. Furthermore, a new folder will be created with the given name.
If you used `.` before `./newBenchmark.sh`, the script will change your directory after running, so you can directly start to configure your load test.

#### Secondary storage options

You can specify a secondary storage type as the second argument:

```
. ./newBenchmark.sh my-load-test-name elasticsearch  # Default - uses Elasticsearch
. ./newBenchmark.sh my-load-test-name opensearch     # Uses OpenSearch
. ./newBenchmark.sh my-load-test-name postgresql     # Uses PostgreSQL (RDBMS)
. ./newBenchmark.sh my-load-test-name none           # No secondary storage
```

The `none` option runs load tests without any secondary storage, which disables Camunda exporters. This is useful for testing the core orchestration engine performance in isolation.

#### Disabling Optimize

Optimize is enabled by default. To disable it, pass `false` as the fourth argument:

```
. ./newLoadTest.sh my-load-test-name elasticsearch 1 false
```

In the GitHub workflow, set the `enable-optimize` input to `false`.

### How to configure a load test

The load test configuration is done via the `camunda-platform-values.yaml` file (copied to your namespace folder).
You can also modify the Makefile to pass additional Helm arguments if needed.

#### Use different Camunda Snapshot

If you want to use your own or a different Camunda snapshot, then you could do the following.

**Build the Docker image:**

```bash
# builds the dist
mvn clean install -T1C -DskipTests -pl dist -am
# builds the a new camunda docker image
docker build --build-arg DISTBALL=dist/target/camunda-zeebe-*.tar.gz -t registry.camunda.cloud/team-zeebe/camunda:SNAPSHOT-$(date +%Y-%m-%d)-$(git rev-parse --short=8 HEAD) --target app -f camunda.Dockerfile .
# pushes the image to our container registry (requires docker login to registry.camunda.cloud)
# Zeebe team members have push access by default. If you don't have access, request it in #ask-infra.
docker push registry.camunda.cloud/team-zeebe/camunda:SNAPSHOT-$(date +%Y-%m-%d)-$(git rev-parse --short=8 HEAD)
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

After you set up your load test namespace and make configuration changes, you can start your load test.

To install/upgrade both the [Camunda Platform Helm](https://github.com/camunda/camunda-platform-helm) and the [Load test Helm chart](https://github.com/camunda/camunda-load-tests-helm):

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

This will deploy the full Camunda Platform (including `orchestration cluster`, `elasticsearch`, `optimize`, `connectors`, `identity` and `keycloak`) and load test applications (e.g. `starter` and `worker`).

### Running specific scenarios

By default, `make install` uses the load test chart's own defaults (no workload profile applied).
To run a specific workload profile, use one of the named targets:

```sh
make latency   # 1 instance/s, 1 worker — low-throughput, useful for latency measurements
make typical   # 50 instances/s, 6 workers, typical_process BPMN
make realistic # Realistic multi-instance benchmark (values from camunda-load-tests-helm)
make max       # 300 instances/s — maximum stress, also disables consistency check overhead
make archiver  # Multi-instance archiver scenario (no workers)
```

You can also pass `scenario=` directly to combine with additional overrides:

```sh
make install scenario=max additional_platform_configuration="--set orchestration.resources.limits.memory=4Gi"
```

For stable (non-spot) VMs, use `install-stable` with the `scenario=` variable:

```sh
make install-stable scenario=max
```

To inspect the resolved flags without running any Helm commands:

```sh
make print-scenario scenario=max
```

To render Helm templates for inspection:

```sh
make template scenario=max          # renders platform manifests
make template-load-test scenario=max  # renders load test manifests
```

### How to clean up a load test

After you're done with your load test, you should remove the remaining namespace.
In order to do this easily, just run:

```
./deleteBenchmark.sh my-load-test-name
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

The `clean` job works regardless and cleans up both the platform and load-test deployments.

## Load testing Camunda SaaS

_You need a Kubernetes Cluster at your disposal to run the load test itself, which then connects to your Camunda SaaS Cluster._

```sh
./newCloudBenchmark.sh <namespace>
```

Similar to the `newBenchmark.sh`, it will create a new Kubernetes namespace and a new folder with the given name.
Afterwards, we can deploy our load test applications (via the [Load Test Helm Chart](https://github.com/camunda/camunda-load-tests-helm)).

Before doing that, you need to provide the Camunda SaaS credentials for the cluster you want to test. The `newCloudBenchmark.sh` script has already created a `credentials.txt` file inside the newly created namespace folder.

Download the Camunda SaaS credentials (the file containing the required environment variables) and either copy its contents into the generated `credentials.txt` file or replace `credentials.txt` with the downloaded file (keeping the filename `credentials.txt`).

Once `credentials.txt` contains the correct SaaS environment variables, you can run the following Makefile command from inside the namespace folder:

```sh
make install-load-test
```

### Running specific scenarios

By default, we will run an artificial load against the configured SaaS cluster. If you want to change this to some more realistic or typical workload, you can use the following targets.

```sh
# Run typical workload, with 10 tasks 
make typical

# Run a realistic workload with multi-instance call activities, etc.
make realistic
```

