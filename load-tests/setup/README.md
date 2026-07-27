# Manual set up a load test

Welcome to the manual setup of a load test. :wave:

There are two targets to run a load test against:

* [Self-Managed Zeebe Cluster](#load-testing-self-managed-zeebe-cluster)
* [Camunda SaaS Cluster](#load-testing-camunda-cloud-saas)

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
tsh login --proxy=camunda.teleport.sh:443 --auth=okta
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
* **Elasticsearch** as secondary storage, deployed as an [ECK](https://www.elastic.co/docs/deploy-manage/deploy/cloud-on-k8s)-managed cluster via the `load-test-setup` chart
* **Optimize** with history cleanup (1-day TTL)
* **Connectors** with OIDC authentication
* **Identity + Keycloak** for OIDC-based authentication

Shared baseline platform config lives in `camunda-platform-values-defaults.yaml`; storage-specific overrides live in `camunda-platform-values-${secondaryStorage}.yaml`.

### How to set up a load test namespace

The root `newLoadTest.sh` is a **version dispatcher** — it forwards to a version-specific script
under `setup/<version>/newLoadTest.sh`, defaulting to `main`. Each stable version has its own
subfolder (`stable-87`, `stable-88`, …) so all version setups live on `main` without backports.

Run `./newLoadTest.sh --help` to see currently available versions. To target a specific version:

```sh
./newLoadTest.sh --target-version stable-89 my-test   # stable/8.9 setup
./newLoadTest.sh --target-version stable-89 -h        # version-specific help
```

The rest of this section documents the `main` setup.

Running `newLoadTest.sh` without arguments shows the `main` help:

```sh
Usage: newLoadTest.sh <namespace> [secondaryStorage] [ttl_days] [enable_optimize]

Arguments:
  namespace          Base namespace name. Will be prefixed with "c8-" if missing.
  secondaryStorage   Optional. One of: elasticsearch, opensearch, postgresql, mysql, mariadb, mssql, oracle, none. Default: elasticsearch.
  ttl_days           Optional. Positive integer for namespace TTL in days. Default: 1.
  enable_optimize    Optional. true|false to enable Optimize. Default: true.

Options:
  -h, --help         Show this help message.

Examples:
  ./newLoadTest.sh demo
  ./newLoadTest.sh perf opensearch 3 true
```

As you can see, you can create a test (namespace) by passing a name; other parameters are optional. Like, secondary storage, TTL, and whether Optimize should be enabled.

Example:

```sh
. ./newLoadTest.sh my-load-test-name
```

This will source and run the `newLoadTest.sh` script. A new folder is created with the given name, containing a rendered Makefile, Helm values, and a local "load-test-setup" Helm chart under `charts/load-test-setup/`.

The template files live under `setup/main/`:

- `Makefile` — rendered into the namespace folder with placeholders substituted.
- `values/` — Helm values files. All installs start from
  `camunda-platform-values-defaults.yaml` (shared baseline platform config)
  and layer `camunda-platform-values-${secondaryStorage}.yaml` on top. RDBMS
  storages additionally apply `camunda-platform-values-rdbms.yaml` between
  them. For example, `elasticsearch` copies
  `camunda-platform-values-defaults.yaml`,
  `camunda-platform-values-elasticsearch.yaml`,
  `camunda-platform-override-values.yaml`, `load-test-values.yaml`,
  `values-stable.yaml`, and `prometheus-elasticsearch-exporter-values.yaml`.
- `databases/` — raw Kubernetes manifests for MSSQL and Oracle (no public
  Helm chart). Copied into the namespace folder only when the matching
  storage is chosen.
- `charts/load-test-setup/` — The "load-test-setup" Helm Chart. Always copied and rendered
  with random secrets.

**A namespace is bound to its storage choice at bootstrap.** To try a different
storage, create a new namespace via `./newLoadTest.sh <new-name> <newStorage>`.

The load-test-setup chart owns all the resources deployed for a single load tests:
* the namespace (labels, AZ pinning, TTL annotation)
* the `camunda-credentials` secret
* the leader-balancer cronjob
* the chaos-killer cronjob (optional, disabled by default)
* the Elasticsearch ECK custom resource (optional, disabled by default but auto-enabled when using the `elasticsearch` secondary storage, or by Optimize, when the secondary storage is not directly usable by Optimize)

It is parameterized by a values file baked at scaffold time:

* `load-test-setup-values.yaml`: namespace name, author label, `deadlineDate`, topology zone.

Reruns `make install-load-test-setup` after a TTL deletion reinstall the same chart, so the orchestration OIDC secret stays in sync with `load-test-values.yaml` and you don't lose credentials.

If you used `.` before `./newLoadTest.sh`, the script will change your directory after running, so you can directly start to configure your load test.

#### Secondary storage options

You can specify a secondary storage type as the second argument:

```sh
. ./newLoadTest.sh my-load-test-name elasticsearch  # Default - uses Elasticsearch
. ./newLoadTest.sh my-load-test-name opensearch     # Uses OpenSearch
. ./newLoadTest.sh my-load-test-name postgresql     # Uses PostgreSQL (RDBMS)
. ./newLoadTest.sh my-load-test-name mysql          # Uses MySQL (RDBMS)
. ./newLoadTest.sh my-load-test-name mariadb        # Uses MariaDB (RDBMS)
. ./newLoadTest.sh my-load-test-name mssql          # Uses Microsoft SQL Server (RDBMS)
. ./newLoadTest.sh my-load-test-name oracle         # Uses Oracle (RDBMS)
. ./newLoadTest.sh my-load-test-name none           # No secondary storage
```

The `none` option runs load tests without any secondary storage, which disables Camunda exporters. This is useful for testing the core orchestration engine performance in isolation.

#### ECK Elasticsearch

Elasticsearch is deployed via the [Elastic Cloud on Kubernetes (ECK) operator](https://www.elastic.co/docs/deploy-manage/deploy/cloud-on-k8s). ECK manages the full lifecycle of the Elasticsearch cluster — node creation, configuration, scaling, and rolling upgrades — through a dedicated `Elasticsearch` [custom resource (CRD)](https://www.elastic.co/guide/en/cloud-on-k8s/current/k8s-api-reference.html). When configured with `elasticsearch.enabled=true`, the `load-test-setup` chart will deploy this custom resource.

> [!TIP]
> Reference:
> * [Deploy an Elasticsearch cluster](https://www.elastic.co/docs/deploy-manage/deploy/cloud-on-k8s/elasticsearch-deployment-quickstart)
> * [Elasticsearch configuration](https://www.elastic.co/docs/deploy-manage/deploy/cloud-on-k8s/elasticsearch-configuration)
> * [`Elasticsearch` custom resource](https://doc.crds.dev/github.com/elastic/cloud-on-k8s/elasticsearch.k8s.elastic.co/Elasticsearch/v1) ([official doc](https://www.elastic.co/docs/reference/cloud-on-k8s/api-reference/3_4_1#elasticsearchk8selasticcov1))

The ECK operator creates a Kubernetes service named `elasticsearch-es-http` for HTTP access to the cluster. All components that connect to Elasticsearch (Camunda, Optimize, the metrics exporter) must use this service name.

The default ECK Elasticsearch configuration (defined in `charts/load-test-setup/values.yaml`) is:

|     Setting      |               Default                |
|------------------|--------------------------------------|
| Version          | 8.18.0                               |
| Node count       | 3                                    |
| CPU per node     | 7 cores                              |
| Memory per node  | 8 Gi                                 |
| JVM heap         | 3 Gi                                 |
| Storage per node | 512 Gi (`benchmark-ssd-zonal-v1`)    |
| TLS              | Disabled (HTTP API)                  |
| Authentication   | Anonymous superuser (load-test only) |

##### Local modifications

All Elasticsearch configuration options (version, node count, resources, storage, …) are exposed as values of the `load-test-setup` Helm chart — see `charts/load-test-setup/values.yaml` for the full list. The values file for your specific load test (`load-test-setup-values.yaml`) is generated by `./newLoadTest.sh` and lives at the root of your namespace folder. **This is the preferred place for local modifications** — edit `load-test-setup-values.yaml` and run `make install-load-test-setup` to apply.

To override defaults at install time without editing the file (e.g. during a one-off test), pass them via `additional_load_test_setup_configuration`:

```sh
make install additional_load_test_setup_configuration="--set elasticsearch.version=8.17.0 --set elasticsearch.count=1"
```

##### Live modifications inside Kubernetes

> [!Important]
>
> Because the Elasticsearch cluster is managed by the ECK operator, **live modifications must be made through the `Elasticsearch` custom resource**, not through the underlying StatefulSet. Changes applied directly to the StatefulSet will be overwritten by the operator on its next reconciliation loop.
>
> To inspect the current custom resource:
>
> ```sh
> kubectl -n <namespace> get elasticsearch elasticsearch -o yaml
> ```
>
> To edit the Elasticsearch cluster, edit the custom resource instead:
>
> ```sh
> kubectl -n <namespace> edit elasticsearch elasticsearch
> ```
>
> See the [reference links](#eck-elasticsearch) for more details about the available options.

#### Disabling Optimize

Optimize is enabled by default. To disable it, pass `false` as the fourth argument:

```
. ./newLoadTest.sh my-load-test-name elasticsearch 1 false
```

In the GitHub workflow, set the `enable-optimize` input to `false`.

### How to configure a load test

Shared baseline configuration is in `camunda-platform-values-defaults.yaml`; storage-specific overrides are in `camunda-platform-values-${secondaryStorage}.yaml` (both copied to your namespace folder).
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

Update the `camunda-platform-values-defaults.yaml` file in your namespace folder and set the newly created image tag.

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

`make install` first runs `check-deadline` (fails fast if today ≥ the baked `deadlineDate` so we don't deploy into a namespace the TTL cleanup workflow is about to delete), then `install-load-test-setup` which `helm upgrade --install`s the local load-test-setup chart. The load-test-setup release creates the namespace, the `camunda-credentials` secret, the leader-balancer cronjob, and the Optimize indices cleanup job in a single, idempotent Helm release — so reruns after a TTL deletion reapply the same credentials and the orchestration OIDC secret stays in sync with `load-test-values.yaml`.

Or install/upgrade them separately:

```shell
# Install/upgrade the load-test-setup chart (namespace, credentials secret, leader balancer cronjob, cleanup job)
make install-load-test-setup

# Install/upgrade the Camunda Platform
make install-platform

# Install/upgrade the load test (starter, worker, etc.)
make install-load-test
```

To bump the deadline of an existing namespace without re-scaffolding, edit `deadlineDate` in `load-test-setup-values.yaml` (single source of truth — `check-deadline` reads it directly) and reinstall the load-test-setup chart:

```sh
make install-load-test-setup
```

The load-test-setup chart deploys a leader balancing cronjob that runs every 10 minutes to rebalance cluster leaders.

#### Optional chaos-killer

The load-test-setup chart can optionally deploy a chaos-killer CronJob. When enabled, it lists all
pods in the namespace, expands a space-separated list of shell glob patterns, and deletes **one
random matching pod per run**.

- Default schedule: hourly (`0 * * * *`)
- Default target patterns: `camunda-* elastic-* postgresql-*`
- Default state: disabled

Enable via the `chaos` variable or the convenience shortcuts:

```sh
make install chaos=true     # enable chaos with any install target
make install-chaos          # shorthand for the above
make install-stable-chaos   # stable VMs + chaos
```

To override the default schedule or target patterns, pass them via `additional_load_test_setup_configuration`:

```sh
make install chaos=true \
  additional_load_test_setup_configuration="--set chaosKiller.schedule='*/15 * * * *' \
  --set chaosKiller.targetPatterns='camunda-* elastic-* postgresql-*'"
```

To preview the rendered manifests before installing:

```sh
make template-load-test-setup-chaos
```

In the GitHub workflow, set the `enable-chaos` input to `true`.

#### Optional second physical tenant

A load test can exercise **two physical tenants** (`default` and `testfoo`) on a single cluster that
share one RDBMS, isolated by table prefix (`DEFAULT_` / `TESTFOO_`). This validates physical-tenant
isolation and independent load per tenant.

Because physical tenants are isolated by RDBMS table prefix, this only works with an rdbms
`secondary_storage` (`postgresql`, `mysql`, `mariadb`, `mssql`, `oracle`); any other value fails fast.

```sh
make install physical_tenants=true secondary_storage=postgresql scenario=typical
```

When enabled, the Makefile:

- Applies `camunda-platform-two-physical-tenants-shared-rdbms.yaml` (a copy of
  `camunda-platform-values-rdbms.yaml` that also declares the `testfoo` tenant: its `TESTFOO_` prefix,
  OIDC provider assignment, and orchestration-client authorizations) instead of the plain rdbms values.
- Clones the generated `load-test-credentials` secret into `load-test-credentials-testfoo`, overriding
  only the REST address to the tenant path `http://camunda:8080/physical-tenants/testfoo`.
- Renders the `starter`/`worker` from the same chart, values, scenario and **image** as the default
  tester, renames them to `starter-testfoo`/`worker-testfoo`, and applies them. The testfoo tester uses
  REST (`global.preferRest.enabled=true`) because gRPC only routes to the default physical tenant.

A second Helm release is not used because the `camunda-load-tests` subchart hardcodes the
`starter`/`worker` resource names, which would collide in the same namespace.

In the GitHub workflow, set the `physical-tenants` input to `true` (with an rdbms
`secondary-storage-type`).

Verify both tenants receive writes:

```sh
kubectl exec -n <namespace> postgresql-0 -- env PGPASSWORD=camunda \
  psql -U camunda -d camunda -c "
SELECT 'default' AS tenant, COUNT(*) FROM default_process_instance
UNION ALL SELECT 'testfoo', COUNT(*) FROM testfoo_process_instance;"
```

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

### Accessing Services

Benchmark clusters have authentication enabled. Logging into Operate, Tasklist and Admin webapps requires both Camunda and Keycloak reachable locally so that the SSO redirect works. Keycloak itself runs in the shared `keycloak-operator` namespace, as a Service named after the load test namespace, not inside `<namespace>` (see [Keycloak in the load test setup chart README](charts/load-test-setup/README.md#keycloak)).

1. Port-forward both Camunda and Keycloak:

```sh
kubectl -n <namespace> port-forward svc/camunda 8080:8080 &
kubectl -n keycloak-operator port-forward svc/<namespace> 18080:18080 &
wait
```

2. Get the `demo` user password:

```sh
kubectl -n <namespace> get secret camunda-credentials -o jsonpath="{.data.identity-firstuser-password}" | base64 --decode
```

3. Open <http://localhost:8080> and log in with user `demo` and the password from the previous step.

#### Keycloak admin credentials

The Keycloak admin console user is `admin` (or whatever `keycloak.adminUser.username` is set to). Its password is generated the same way as every other credential in this chart (see [Where credentials come from](#where-credentials-come-from)) and stored under the `identity-keycloak-admin-password` key:

```sh
kubectl -n <namespace> get secret camunda-credentials -o jsonpath="{.data.identity-keycloak-admin-password}" | base64 --decode
```

#### Using c8ctl (CLI access)

To use [c8ctl](https://github.com/camunda/c8ctl) against the cluster, port-forward the REST gateway and Keycloak, then export the credentials from the namespace secrets:

```sh
export NAMESPACE="..."
kubectl -n $NAMESPACE port-forward svc/camunda-gateway 8080:8080 &
kubectl -n keycloak-operator port-forward svc/$NAMESPACE 18080:18080 &

export CAMUNDA_BASE_URL=http://localhost:8080
export CAMUNDA_OAUTH_URL=http://localhost:18080/auth/realms/camunda-platform/protocol/openid-connect/token
export CAMUNDA_CLIENT_ID=orchestration
export CAMUNDA_CLIENT_SECRET=$(kubectl -n $NAMESPACE get secret camunda-credentials \
  -o jsonpath='{.data.orchestration-security-authentication-oidc-secret}' | base64 --decode)
export CAMUNDA_TOKEN_AUDIENCE=orchestration-api
```

Verify the connection:

```sh
c8 list pd
```

### How to clean up a load test

When you're done, run `make clean` from inside the namespace folder:

```sh
cd c8-my-load-test-name
make clean
```

This uninstalls the Helm releases (Camunda Platform + load test + Elasticsearch exporter + load-test-setup), removes any secondary-storage chart/PVCs, and finally `kubectl delete namespace --ignore-not-found --wait` to drop the namespace itself. The namespace delete waits for finalization (can take a few minutes for a full load test) so that an immediate `make install` afterwards doesn't race a still-terminating namespace.

`make clean` also explicitly deletes the Keycloak resources `keycloak-operator` namespace since they
don't live in the namespace being torn down.
If you ever delete a load test namespace by hand (`kubectl delete namespace` directly, without `make
clean`), you must also delete those separately, or they leak forever — see [Cleanup in the load test
setup chart README](charts/load-test-setup/README.md#cleanup).

The local namespace folder is left in place — keep it if you may want to recreate the namespace later (`make install` will reinstall the load-test-setup chart, which recreates the namespace and credentials secret), or `rm -rf c8-my-load-test-name` from `load-tests/setup/` if you're truly done.

## Running on stable VMs

By default, load tests deploy onto preemptible (spot) GKE nodes — defined inline
in `camunda-platform-values-defaults.yaml`. To deploy onto the stable (non-spot) nodepool
instead, use `make install-stable`. This applies `values-stable.yaml` on top of
the platform values, swapping the orchestration `nodeSelector` and tolerations
to target the `benchmark-n2-standard-4-stable` nodepool.

```sh
# Deploy the Camunda Platform on stable nodes
make install-stable

# Spits out the rendered templates targeting stable nodes
make template-stable
```

Stable VMs are useful for observing long-term behaviour (memory leaks, drift
under steady load) that preemptible VMs don't expose because they're cycled
out by GCP every ~24h.

The `clean` job works regardless and cleans up both the platform and load-test deployments.

## Helm Chart Version Management

The Camunda Platform Helm Chart version pinned in each versioned setup folder is tracked and
automatically updated by [Renovate](https://docs.renovatebot.com/). A custom regex manager in
`.github/renovate.json` reads a structured annotation comment immediately before the
`camunda_platform_helm_chart_version` assignment in each `newLoadTest.sh`.

### Annotation format

```sh
# renovate: version=<tag-prefix>
camunda_platform_helm_chart_version="<chart-version>"
```

The `version=` value must match the GitHub tag prefix used in the
[`camunda/camunda-platform-helm`](https://github.com/camunda/camunda-platform-helm) repository.
Tags follow the pattern `<tag-prefix>-<chart-version>`, for example
`camunda-platform-8.9-14.4.1`.

### Update rules

- **`main`**: Renovate opens PRs for all version types (including pre-releases such as `-alpha`).
- **`stable/*` branches**: Renovate opens PRs for patch updates only.

In any case, Renovate will try to keep the Camunda Platform Helm Chart to the
latest available version for the related Camunda version.

### Adding a new versioned folder

When adding a versioned setup folder for a new stable branch (e.g. `stable-8X/`):

1. Add the annotation comment immediately above the `camunda_platform_helm_chart_version`
   assignment in the new `newLoadTest.sh`:

   ```sh
   # renovate: version=camunda-platform-8.X
   camunda_platform_helm_chart_version="a.b.c"
   ```
2. The `camunda_platform_helm_chart_version` value should match the actual Helm
   Chart version for that specific Camunda version.

There should not be any need to update `.github/renovate.json` when a new version is added.

## Load testing Camunda SaaS

_You need a Kubernetes Cluster at your disposal to run the load test itself, which then connects to your Camunda SaaS Cluster._

```sh
./newCloudLoadTest.sh <namespace>
```

Similar to the `newLoadTest.sh`, it will create a new Kubernetes namespace and a new folder with the given name.
Afterwards, we can deploy our load test applications (via the [Load Test Helm Chart](https://github.com/camunda/camunda-load-tests-helm)).

Before doing that, you need to provide the Camunda SaaS credentials for the cluster you want to test. The `newCloudLoadTest.sh` script has already created a `credentials.txt` file inside the newly created namespace folder.

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

