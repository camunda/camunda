# Load Tests

This directory contains the benchmark tooling for Camunda 8: a Spring Boot load-testing application, Helm values for cluster setup, and shell scripts / Makefiles for deploying load tests to Kubernetes.

## Load Tester Application (`load-tester/`)

A Spring Boot application (`camunda-spring-boot-starter`) with a single main class `io.camunda.zeebe.LoadTesterApplication`. Behaviour is selected at runtime via **Spring profiles**:

| Profile | Activated by | Component | What it does |
|---------|-------------|-----------|--------------|
| `starter` | `--spring.profiles.active=starter` | `Starter` (`CommandLineRunner`) | Deploys BPMN processes, creates instances at a configurable rate |
| `worker` | `--spring.profiles.active=worker` | `Worker` (`@JobWorker`) | Completes jobs with configurable delay and optional message publishing |

Both profiles produce **separate Docker images** (one for starter, one for worker) via Jib Maven profiles. This is required for backwards compatibility with existing Helm charts and load test deployments.

### Key classes

| Class | Purpose |
|-------|---------|
| `LoadTesterApplication` | `@SpringBootApplication` entry point |
| `Starter` | `@Profile("starter")` — deploys processes, creates instances, records latency |
| `Worker` | `@Profile("worker")` — `@JobWorker` handler, completes jobs |
| `LoadTesterProperties` | `@ConfigurationProperties(prefix = "load-tester")` — benchmark config |
| `StarterProperties` | Nested config for starter (rate, process ID, payload, etc.) |
| `WorkerProperties` | Nested config for worker (job type, threads, delays, etc.) |
| `PayloadReader` | Reads JSON payload from classpath or filesystem |
| `StarterLatencyMetricsDoc` | Prometheus metric definitions |
| `ProcessInstanceStartMeter` | Tracks time from creation to queryability |
| `DataReadMeter` / `DataReadMeterQueryProvider` | Periodic read-benchmark queries |
| `ResponseChecker` | Drains async job-completion futures, logs errors |

## Configuration

Two configuration namespaces in `application.yaml`:

### `camunda.client.*` — Connection and auth (from camunda-spring-boot-starter)

```yaml
camunda:
  client:
    mode: self-managed
    grpc-address: ${ZEEBE_GRPC_ADDRESS:http://localhost:26500}
    rest-address: ${ZEEBE_REST_ADDRESS:http://localhost:8080}
    auth:
      method: ${ZEEBE_AUTH_METHOD:none}    # none, basic, or oidc
      username / password / client-id / client-secret / audience / token-url
```

### `load-tester.*` — Benchmark-specific settings

```yaml
load-tester:
  monitor-data-availability: true
  monitor-data-availability-interval: 250ms
  perform-read-benchmarks: false
  starter:
    process-id: benchmark          # BPMN process ID to start
    rate: 300                      # instances per second
    threads: 2
    bpmn-xml-path: bpmn/one_task.bpmn
    extra-bpmn-models: []
    payload-path: bpmn/big_payload.json
    with-results: false
    duration-limit: 0              # seconds, 0 = infinite
    start-via-message: false
  worker:
    job-type: benchmark-task
    completion-delay: 300ms
    capacity: 30
    stream-enabled: true
    send-message: false
```

Override any property via environment variables using relaxed binding, e.g. `LOAD_TESTER_STARTER_RATE=500`.

## Build and Run

```bash
# Build the module (from repo root)
./mvnw -am -pl load-tests/load-tester package -DskipTests -DskipChecks

# Build Docker images via Jib (two separate images for backwards compatibility)
./mvnw -pl load-tests/load-tester jib:build -Pstarter   # gcr.io/zeebe-io/starter:SNAPSHOT
./mvnw -pl load-tests/load-tester jib:build -Pworker    # gcr.io/zeebe-io/worker:SNAPSHOT

# Run locally with Spring Boot
./mvnw -pl load-tests/load-tester spring-boot:run -Dspring-boot.run.profiles=starter
./mvnw -pl load-tests/load-tester spring-boot:run -Dspring-boot.run.profiles=worker
```

## Tests

```bash
# Run load-tester unit tests
./mvnw verify -pl load-tests/load-tester -DskipChecks
```

Test classes: `ConfigTest`, `ProcessInstanceStartMeterTest`, `DataReadMeterTest`.

## Deployment (`setup/`)

### Creating a new load test

```bash
cd load-tests/setup
./newLoadTest.sh <namespace> [secondaryStorage] [ttl_days] [enable_optimize]
# Example: ./newLoadTest.sh perf opensearch 3 true
```

- `secondaryStorage`: `elasticsearch` (default), `opensearch`, `postgresql`, `none`
- Creates a namespaced folder under `setup/` with a configured Makefile

### Makefile targets (in `setup/default/Makefile`)

| Target | Purpose |
|--------|---------|
| `install` | Full install: storage + credentials + platform + load test + ES exporter |
| `install-stable` | Same but on stable (non-preemptible) VMs |
| `install-platform` | Helm upgrade/install the Camunda Platform chart |
| `install-load-test` | Helm upgrade/install the load test chart |
| `install-storage` | Install PostgreSQL or OpenSearch if needed |
| `create-credentials` | Create `camunda-credentials` K8s secret |
| `setup-leader-balancer` | Cronjob to rebalance partition leaders every 10 min |
| `clean` | Uninstall all Helm releases and delete PVCs |

Customise via `additional_platform_configuration` and `additional_load_test_configuration` Make variables.

### Helm values files (in `load-tests/`)

| File | Backend |
|------|---------|
| `camunda-platform-values.yaml` | Elasticsearch (default) |
| `camunda-platform-values-opensearch.yaml` | OpenSearch |
| `camunda-platform-values-rdbms.yaml` | PostgreSQL |
| `camunda-platform-no-secondary-storage.yaml` | No secondary storage |
| `camunda-platform-values-optimize.yaml` | Optimize component |

### Other scripts

- `deleteLoadTest.sh <namespace>` — tears down a load test namespace
- `createCredsLoadTest.sh` — creates K8s secrets for Camunda credentials
- `newCloudLoadTest.sh` — variant for Camunda Cloud environments

## Metrics

Three Prometheus timer metrics exposed on the actuator endpoint (`/actuator/prometheus`, port 9600):

| Metric name | Tags | Description |
|-------------|------|-------------|
| `starter.response.latency` | — | Request/response time for creating instances |
| `starter.data.availability.latency` | `partition` | Time from creation to queryability |
| `starter.read.benchmark` | `query` | Latency of read benchmark queries |
