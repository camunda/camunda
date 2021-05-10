# Setup a Benchmark

Welcome to the setup a benchmark. :wave:

This document describes the steps to setup and run a benchmark. It is target at a Linux system.

## Requirements

Make sure you have the following installed: docker, gcloud, kubectl, kubens and helm!

## How to setup a Benchmark namespace

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

## How to configure a Benchmark

The benchmark configuration is completely done via the `zeebe-values.yaml` file.
If there is a property missing which you want to change please open an issue in https://github.com/zeebe-io/zeebe-cluster-helm

### Use different Zeebe Snapshot

If you want to use your own or a different Zeebe snapshot then you could do the following.

**Build the docker image:**
```bash
# builds the dist with zbctl packed
clients/go/cmd/zbctl/build.sh && mvn clean install -T1C -DskipTests -pl dist -am
# builds the a new zeebe docker image
docker build --build-arg DISTBALL=dist/target/camunda-cloud-zeebe-*.tar.gz -t gcr.io/zeebe-io/zeebe:SNAPSHOT-$(date +%Y-%m-%d)-$(git rev-parse --short=8 HEAD) --target app .
# pushes the image to our docker registry
docker push gcr.io/zeebe-io/zeebe:SNAPSHOT-$(date +%Y-%m-%d)-$(git rev-parse --short=8 HEAD)
```

Change the `zeebe-values.yaml` file in order to use the new created image.

The changes should look similar to this:
```yaml
image:
  repository: gcr.io/zeebe-io/zeebe
  tag: <TAG>
  pullPolicy: Always
```

## How to run a Benchmark

After you setup your benchmark namespace and made changes to your configuration.
You can start your benchmark just with `make clean all`.

This will deploy the `zeebe-cluster` and `elastic` helm charts.
Furthermore the starters and workers should be started.

## How to clean up a Benchmark

After you're done with your benchmark you should remove the remaining namespace.
In order to do this easily, just run:

```
./deleteBenchmark.sh my-benchmark-name
```

This will switch to the default namespace, delete the given namespace and delete the corresponding folder.

## How to run a Benchmark in Camunda Cloud

_This targets only internal use cases and is not available for external parties._

We currently waiting until https://github.com/camunda-cloud/enhancements/blob/master/CEP017-programmatic-access-to-console.md is implemented, until this is done we need to do some steps manually.

### Setup Cloud Cluster

 * Go to https://console.cloud.ultrawombat.com
 * Login with your credentials, reach out to cloud team if you haven't one
 * Create a new cluster, as region choose 'new chaos'. This means the cluster will be created in 'ultrachaos' gke cluster
 * Create new client for the cluster
 * copy the credentials

### Setup Cloud Benchmark

 * Create a new cloud benchmark in our benchmark folder, via `./newCloudBenchmark`. This will create a new namespace in our k8 cluster, such that we can deploy our starters and workers. They will connect to the camunda cloud cluster after we added the correct credentials.
 * Edit the `cloudcredentials.yaml` file, replace the old/default values with your client credentials. **NOTE: Please make sure that you're not pushing your credentials to the repository!**
 * Deploy everything you need, e. g. run `make clean all` to deploy the secret, worker and starter.
 * To check the metrics for your benchmark, use https://grafana-ultrachaos.internal.ultrawombat.com/?orgId=1

