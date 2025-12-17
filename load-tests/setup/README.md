# Manual set up a load test

Welcome to the manual set up of a load test. :wave:

There are two targets to run a load test against:

* [Self-Managed Camunda Cluster](#load-testing-self-managed-camunda-cluster)
* [Camunda Cloud Cluster](#load-testing-camunda-cloud-saas)

All guides are targeted at a Linux system.

## Requirements

Make sure you have the following installed: docker, gcloud, kubectl, and helm

## Load testing Self-Managed Camunda Cluster

### How to set up a load test namespace

Just run the `newLoadTest.sh` with your preferred new namespace name.

Like:

```
./newLoadTest.sh my-load-test-name
```

This will:
1. Create a new Kubernetes namespace with the given name
2. Label the namespace with the current user as creator
3. Label the namespace with a deadline (default 7 days, configurable via `TTL_DAYS` environment variable)
4. Create a new folder with the given name containing:
   - A `Makefile` configured for your namespace
   - A `camunda-platform-values.yaml` file for customizing the Camunda Platform deployment
   - A `values-stable.yaml` file for running on stable (non-spot) VMs

Example with custom TTL:
```
TTL_DAYS=14 ./newLoadTest.sh my-load-test-name
```

### How to configure a load test

The Camunda Platform configuration is done via the `camunda-platform-values.yaml` file in your load test folder.
This file controls settings for the orchestration cluster, Elasticsearch, and other components.

#### Use different Camunda image

If you want to use your own or a different Camunda image, you can modify the `camunda-platform-values.yaml` file.

**Build the docker image:**

```bash
# builds the dist
./mvnw clean install -T1C -DskipTests -pl dist -am
# builds a new Camunda docker image
docker build --build-arg DISTBALL=dist/target/camunda-zeebe-*.tar.gz -t gcr.io/zeebe-io/zeebe:SNAPSHOT-$(date +%Y-%m-%d)-$(git rev-parse --short=8 HEAD) --target app -f camunda.Dockerfile .
# pushes the image to our docker registry
docker push gcr.io/zeebe-io/zeebe:SNAPSHOT-$(date +%Y-%m-%d)-$(git rev-parse --short=8 HEAD)
```

Update the `camunda-platform-values.yaml` file to use the newly created image:

```yaml
global:
  image:
    tag: SNAPSHOT-2024-01-15-abc12345

orchestration:
  image:
    registry: gcr.io
    repository: zeebe-io/zeebe
    tag: SNAPSHOT-2024-01-15-abc12345
```

### How to run a load test

After you have set up your load test namespace and configured it, you can deploy the load test.

Available Makefile targets:

```shell
# Install both Camunda Platform and load test components
make install

# Install only the Camunda Platform
make install-platform

# Install Camunda Platform on stable (non-spot) VMs
make install-platform-stable

# Install only the load test components (starter, worker, etc.)
make install-load-test

# Update the Camunda Platform deployment
make update-platform

# Update the load test deployment
make update-load-test

# Clean up everything (uninstall both platform and load test)
make clean
```

The `install` target will:
1. Deploy the Camunda Platform (orchestration cluster, Elasticsearch, etc.)
2. Deploy the load test applications (starter, worker)
3. Set up a leader balancing cronjob

### How to clean up a load test

After you're done with your load test you should remove the namespace and folder.

To clean up the deployed resources:
```shell
cd my-load-test-name
make clean
```

To remove the namespace and folder completely:
```shell
./deleteLoadTest.sh my-load-test-name
```

This will switch to the default namespace, delete the given namespace, and delete the corresponding folder.

## Running on stable/non-spot VMs

You may sometimes want to run load tests on non-spot (or non-preemptible) VMs, for example, if you
want to test for slow memory leaks.

To do this, use the `install-platform-stable` target:

```shell
make install-platform-stable
```

This will use the `values-stable.yaml` file in addition to `camunda-platform-values.yaml` to configure
the deployment for stable VMs.

## Load testing Camunda Cloud SaaS

_You need a Kubernetes Cluster at your disposal to run the load test itself, which then connects to your Camunda Cloud Cluster._

Follow the guide [here](https://github.com/camunda/zeebe-benchmark-helm/blob/main/charts/zeebe-benchmark/README.md#running-against-saas).
