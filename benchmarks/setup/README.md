# Setup a Benchmark

Welcome to the setup a benchmark. :wave:

There are two ways to run a benchmark:

* [Self-Managed Zeebe Cluster](#benchmarking-self-managed-zeebe-cluster)
* [Camunda Cloud Cluster](#benchmarking-camunda-cloud-saas)

All guides are targeted at a Linux systems.

## Requirements

Make sure you have the following installed: docker, gcloud, kubectl, kubens and helm

## Benchmarking Self-Managed Zeebe Cluster

### How to setup a Benchmark namespace

Just run the `newBenchmark.sh` with your preferred new namespace name.

Like:

```
. ./newBenchmark.sh my-benchmark-name
```

This will source and run the `newBenchmark.sh` script, which means it will
create a new k8 namespace and switch to it via `kubens`. Furthermore a new folder
will be created with the given name. If you used `.` before `./newBenchmark.sh`
the script will also change your directory after running, so you can directly start
to configure your benchmark.

### How to configure a Benchmark

The benchmark configuration is completely done via the `zeebe-values.yaml` file.
If there is a property missing which you want to change please open an issue in https://github.com/camunda/camunda-cloud-helm

#### Use different Zeebe Snapshot

If you want to use your own or a different Zeebe snapshot then you could do the following.

**Build the docker image:**

```bash
# builds the dist with zbctl packed
clients/go/cmd/zbctl/build.sh && mvn clean install -T1C -DskipTests -pl dist -am
# builds the a new zeebe docker image
docker build --build-arg DISTBALL=dist/target/camunda-zeebe-*.tar.gz -t gcr.io/zeebe-io/zeebe:SNAPSHOT-$(date +%Y-%m-%d)-$(git rev-parse --short=8 HEAD) --target app .
# pushes the image to our docker registry
docker push gcr.io/zeebe-io/zeebe:SNAPSHOT-$(date +%Y-%m-%d)-$(git rev-parse --short=8 HEAD)
```

Change the `zeebe-values.yaml` file in order to use the new created image.

The changes should look similar to this:

```yaml
global
  image:
    repository: gcr.io/zeebe-io/zeebe
    tag: <TAG>
    pullPolicy: Always
```

### How to run a Benchmark

After you setup your benchmark namespace and made changes to your configuration.
You can start your benchmark just with `make clean zeebe starter worker`.

This will deploy the `zeebe`, `zeebe-gateway` and `elastic` helm charts.
Furthermore the starters and workers should be started.

### How to clean up a Benchmark

After you're done with your benchmark you should remove the remaining namespace.
In order to do this easily, just run:

```
./deleteBenchmark.sh my-benchmark-name
```

This will switch to the default namespace, delete the given namespace and delete the corresponding folder.

## Running on stable/non-spot VMs

You may sometimes want to run benchmarks on non-spot (or non-preemptible) VMs, for example, if you
want to test for slow memory leaks.

In order to do this, simply pass the copied `values-stable.yaml` file as an additional argument to
Helm. For example:

```shell
helm install myRelease zeebe-benchmark/zeebe-benchmark -f values.yaml -f values-stable.yaml
```

You can also use the `*-stable` Makefile jobs, namely:

```shell
# Deploys the Zeebe pods on stable nodes
make benchmark-stable
# Spits out the rendered templates targeting stable nodes
make template-stable
# Updates a benchmark targeting stable nodes
make update-stable
```

The `clean` job works regardless.

## Benchmarking Camunda Cloud SaaS

_You need a Kubernetes Cluster at your disposal to run the benchmark itself, which then connects to your Camunda Cloud Cluster._

Possible future extension point: Use https://docs.camunda.io/docs/apis-clients/cloud-console-api-reference/ to create clusters automatically, at the moment you need to create them manually.

### Setup Cloud Cluster

* Go to Camunda Cloud Console and login with your credentials (reach out to #cloud if you haven't one)
* Create a new cluster
* Create new API credentials for that cluster

### Setup Cloud Benchmark

* Create a new cloud benchmark in our benchmark folder, via `./newCloudBenchmark`. This will create a new namespace in our k8 cluster, such that we can deploy our starters and workers. They will connect to the camunda cloud cluster after we added the correct credentials.
* Edit the `cloudcredentials.yaml` file, replace the old/default values with your client credentials. **NOTE: Please make sure that you're not pushing your credentials to the repository!** https://github.com/camunda/zeebe/blob/main/benchmarks/setup/cloud-default/cloudcredentials.yaml contains an example.
* Deploy everything you need, e. g. run `make clean all` to deploy the secret, worker and starter. **Alternatively**, you can also manually provision the resources:
* `kubectl apply -f cloudcredentials.yaml`
* `kubectl apply -f worker.yaml`
* `kubectl apply -f starter.yaml`

### Use Grafana to Inspect Cloud Metrics (Camunda Internals Only)

* If you use the region 'new chaos' when creating a cluster, it will be created in 'ultrachaos' gke cluster
* This allows to check the metrics for your benchmark via https://grafana-ultrachaos.internal.ultrawombat.com/?orgId=1
* If you use the region "integration worker' when creating a cluster, it you can check for metrics via https://grafana-worker-1.internal.ultrawombat.com/

