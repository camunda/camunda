# Setup a load test

Welcome to the setup of a load test. :wave:

There are two ways to run a load test:

* [Self-Managed Zeebe Cluster](#load-testing-self-managed-zeebe-cluster)
* [Camunda Cloud Cluster](#load-testing-camunda-cloud-saas)

All guides are targeted at a Linux system.

## Requirements

Make sure you have the following installed: docker, gcloud, kubectl, kubens and helm

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

The load test configuration is completely done via the `values.yaml` file.
If there is a property missing that you want to change please open an issue at https://github.com/camunda/zeebe-load-test-helm

#### Use different Zeebe Snapshot

If you want to use your own or a different Zeebe snapshot then you could do the following.

**Build the docker image:**

```bash
# builds the dist
mvn clean install -T1C -DskipTests -pl dist -am
# builds the a new zeebe docker image
docker build --build-arg DISTBALL=dist/target/camunda-zeebe-*.tar.gz -t gcr.io/zeebe-io/zeebe:SNAPSHOT-$(date +%Y-%m-%d)-$(git rev-parse --short=8 HEAD) --target app .
# pushes the image to our docker registry
docker push gcr.io/zeebe-io/zeebe:SNAPSHOT-$(date +%Y-%m-%d)-$(git rev-parse --short=8 HEAD)
```

Change the `values.yaml` file to use the newly created image.

The changes should look similar to this:

```yaml
camunda-platform:
  zeebe:
    image:
      repository: gcr.io/zeebe-io/zeebe
      tag: <TAG>
      pullPolicy: Always
```

### How to run a load test

After you have setup your load test namespace and make changes to your configuration.
You can start your load test just with `make load-test`.

This will deploy the `zeebe`, `zeebe-gateway`, `elastic` and load tests applications (including `starter` and `worker`).

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

To do this, simply pass the copied `values-stable.yaml` file as an additional argument to
Helm. For example:

```shell
helm install myRelease zeebe-benchmark/zeebe-benchmark -f values.yaml -f values-stable.yaml
```

You can also use the `*-stable` Makefile jobs, namely:

```shell
# Deploys the Zeebe pods on stable nodes
make load-test-stable
# Spits out the rendered templates targeting stable nodes
make template-stable
# Updates a load test targeting stable nodes
make update-stable
```

The `clean` job works regardless.

## Load testing Camunda Cloud SaaS

_You need a Kubernetes Cluster at your disposal to run the load test itself, which then connects to your Camunda Cloud Cluster._

Follow the guide [here](https://github.com/camunda/zeebe-benchmark-helm/blob/main/charts/zeebe-benchmark/README.md#running-against-saas).
Possible future extension point: Use https://docs.camunda.io/docs/apis-clients/cloud-console-api-reference/ to create clusters automatically, at the moment you need to create them manually.

### Setup Cloud Cluster

* Go to Camunda Cloud Console and login with your credentials (reach out to #cloud if you haven't one)
* Create a new cluster
* Create new API credentials for that cluster

### Setup Cloud load test

* Create a new cloud load test in our load test folder, via `./newCloudLoadTest`. This will create a new namespace in our k8 cluster, such that we can deploy our starters and workers. They will connect to the camunda cloud cluster after we added the correct credentials.
* Edit the `cloudcredentials.yaml` file, replace the old/default values with your client credentials. **NOTE: Please make sure that you're not pushing your credentials to the repository!** https://github.com/camunda/camunda/blob/main/zeebe/load-tests/setup/cloud-default/cloudcredentials.yaml contains an example.
* Deploy everything you need, e. g. run `make clean all` to deploy the secret, worker and starter. **Alternatively**, you can also manually provision the resources:
* `make secret worker starter`

