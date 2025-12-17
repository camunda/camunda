# Manual set up a load test

Welcome to the manual set up of a load test. :wave:

There are two targets to run a load test against:

* [Self-Managed Camunda Platform](#load-testing-self-managed-camunda-platform)
* [Camunda Cloud Cluster](#load-testing-camunda-cloud-saas)

All guides are targeted at a Linux system.

## Requirements

Make sure you have the following installed: docker, gcloud, kubectl, and helm

## Load testing Self-Managed Camunda Platform

### How to set up a load test namespace

Run the `newLoadTest.sh` script with your preferred namespace name:

```bash
./newLoadTest.sh my-load-test-name [author] [ttl-days]
```

**Parameters:**
- `namespace` (required): The name of the load test namespace
- `author` (optional): Your username (defaults to `whoami`)
- `ttl-days` (optional): Number of days before namespace deletion (defaults to 7)

**What it does:**
1. Creates a new Kubernetes namespace
2. Labels the namespace with creator and deadline date
3. Creates a new folder with the namespace name
4. Copies the `default` configuration template to the new folder
5. Copies `camunda-platform-values.yaml` to the new folder
6. Updates the Makefile with the namespace name
7. Adds required Helm repositories

### How to configure a load test

The load test configuration is done via the `camunda-platform-values.yaml` file in your namespace folder. You can modify this file to customize:
- Resource requests and limits
- Cluster size and partitions
- Elasticsearch configuration
- Other platform settings

If there is a property missing that you want to change, please open an issue at https://github.com/camunda/camunda-platform-helm

#### Use different Docker Image

If you want to use your own or a different Docker image:

**Build the docker image:**

```bash
# builds the dist
./mvnw clean install -T1C -DskipTests -pl dist -am
# builds the a new docker image
docker build --build-arg DISTBALL=dist/target/camunda-zeebe-*.tar.gz -t gcr.io/zeebe-io/zeebe:SNAPSHOT-$(date +%Y-%m-%d)-$(git rev-parse --short=8 HEAD) --target app -f camunda.Dockerfile .
# pushes the image to our docker registry
docker push gcr.io/zeebe-io/zeebe:SNAPSHOT-$(date +%Y-%m-%d)-$(git rev-parse --short=8 HEAD)
```

Then set the image tag when deploying:

```bash
make deploy IMAGE_TAG=SNAPSHOT-2024-01-15-abc12345
```

### How to run a load test

After you have set up your load test namespace and made changes to your configuration, deploy the load test:

```bash
cd my-load-test-name
make deploy
```

This will:
1. Install the Camunda Platform helm chart
2. Install the load test helm chart (starter, worker)
3. Set up a leader balancer cronjob

**Available make targets:**
- `make deploy` - Deploy both platform and load test
- `make deploy-stable` - Deploy on stable (non-spot) VMs
- `make install-platform` - Install only the Camunda Platform
- `make install-load-test` - Install only the load test components
- `make update-platform` - Update the platform helm release
- `make update-load-test` - Update the load test helm release
- `make clean` - Remove all resources

### How to clean up a load test

After you're done with your load test, remove the namespace and folder:

```bash
./deleteLoadTest.sh my-load-test-name
```

This will delete the namespace and the corresponding folder.

## Running on stable/non-spot VMs

You may sometimes want to run load tests on non-spot (or non-preemptible) VMs, for example, if you
want to test for slow memory leaks.

To do this, use the `deploy-stable` target:

```shell
make deploy-stable
```

Or install the platform with the stable values file:

```shell
make install-platform-stable
```

The `values-stable.yaml` file configures the deployment to use stable VMs with appropriate tolerations.

## Load testing Camunda Cloud SaaS

_You need a Kubernetes Cluster at your disposal to run the load test itself, which then connects to your Camunda Cloud Cluster._

Follow the guide [here](https://github.com/camunda/camunda-load-tests-helm).
