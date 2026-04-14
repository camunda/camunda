# Load Tester - Spring Boot Migration Guide

This document explains how the load-tester app was migrated from a standalone HOCON-based
Java application to Spring Boot, covering each component and how it maps to the old architecture.

## Architecture Overview

| Aspect | Old (HOCON / main branch) | New (Spring Boot) |
|--------|---------------------------|-------------------|
| Framework | Standalone Java app | Spring Boot 4.x with `camunda-spring-boot-starter` |
| Configuration | Typesafe Config (`application.conf`) | Spring Boot YAML (`application.yaml`) |
| Client creation | Manual `CamundaClientBuilder` in `App.java` | Auto-configured by `camunda-spring-boot-starter` |
| Entry point | Separate `Starter.main()` / `Worker.main()` | Single `LoadTesterApplication` with Spring profiles |
| Job subscription | Manual `client.newWorker().jobType(type).handler(...)` | `@JobWorker` annotation |
| Metrics endpoint | Custom Prometheus HTTPServer on `/metrics` | Spring Boot Actuator on `/actuator/prometheus` |
| Logging | Log4j2 (`log4j2.xml`) | Logback (Spring Boot default) |
| Dependency injection | None (manual wiring) | Spring auto-wiring |

## Components

### LoadTesterApplication

**Old:** Two separate main classes (`Starter.java`, `Worker.java`) each extending `App.java`.
`App.java` handled monitoring server setup, client creation, auth, and payload reading.

**New:** Single `LoadTesterApplication.java` annotated with `@SpringBootApplication`.
Spring profiles (`starter` / `worker`) activate the appropriate component. The Docker images
pass `--spring.profiles.active=starter` or `--spring.profiles.active=worker` via Jib.

```
LoadTesterApplication
  ├── @Profile("starter") → Starter.java (CommandLineRunner)
  └── @Profile("worker")  → Worker.java (@JobWorker)
```

### Starter

**Old:** `Starter extends App implements Runnable`. Called `createCamundaClient()` manually
with `numJobWorkerExecutionThreads(0)` (starter doesn't run workers). Used `App.readVariables()`
for payloads.

**New:** `@Component @Profile("starter") implements CommandLineRunner`. Gets `CamundaClient`
auto-injected by Spring. Uses `PayloadReader` (a `@Component`) for payloads.

**Behavior preserved:**
- `run()` flow: print topology -> deploy process -> schedule instance creation -> await latch
- Three start modes: normal, with-results, via-message
- `ProcessInstanceStartMeter` for data availability monitoring
- `DataReadMeter` for read benchmark queries
- Deploy retry logic (infinite loop with throttled logging)
- Error handling: `RESOURCE_EXHAUSTED` silent, auth errors → `System.exit(1)`

### Worker

**Old:** `Worker extends App implements Runnable`. Manually opened a job worker:
```java
client.newWorker()
    .jobType(workerCfg.getJobType())  // from config
    .handler(handleJob(...))
    .streamEnabled(isStreamEnabled)
    .metrics(metrics)
    .open();
```

**New:** `@Component @Profile("worker")` with `@JobWorker(autoComplete = false)`:
```java
@JobWorker(autoComplete = false)
public void handleJob(final JobClient jobClient, final ActivatedJob job) { ... }
```

The job type is resolved by the Spring Boot starter from
`camunda.client.worker.defaults.type`, which is bridged to the Helm chart env var:
```yaml
type: ${LOAD_TESTER_WORKER_JOB_TYPE:benchmark-task}
```

This means when the Helm chart sets `LOAD_TESTER_WORKER_JOB_TYPE=refunding` for a realistic
worker deployment, the `@JobWorker` subscribes to `refunding`.

**Behavior preserved:**
- Completion delay logic (sleep until elapsed >= completionDelay)
- Optional message publishing on job completion
- `ResponseChecker` thread for async error monitoring
- Topology printing in `@PostConstruct`

### Configuration

**Old:** HOCON `application.conf` with prefix `app {}`. Properties loaded via
`ConfigFactory.load()` and deserialized to `AppCfg` / `StarterCfg` / `WorkerCfg` beans.

**New:** Spring Boot `application.yaml` with two property namespaces:

1. **`camunda.client.*`** - Auto-configured by `camunda-spring-boot-starter`. Controls
   the `CamundaClient` bean (addresses, auth, worker defaults like type/name/capacity/timeout).
2. **`load-tester.*`** - Custom `@ConfigurationProperties` for load-tester-specific settings
   not handled by the starter (rate, BPMN path, completion delay, message config, etc.).

#### Property Mapping

##### Client / Connection

| Old HOCON | New Spring Boot | Env var (unchanged) |
|-----------|-----------------|---------------------|
| `app.brokerUrl` | `camunda.client.grpc-address` | `ZEEBE_GRPC_ADDRESS` |
| `app.brokerRestUrl` | `camunda.client.rest-address` | `ZEEBE_REST_ADDRESS` |
| `app.preferRest` | `camunda.client.prefer-rest-over-grpc` | `CAMUNDA_CLIENT_PREFER_REST_OVER_GRPC` |
| `app.auth.type` (NONE/BASIC/OAUTH) | `camunda.client.auth.method` (none/basic/oidc) | `CAMUNDA_CLIENT_AUTH_METHOD` |
| `app.auth.oauth.clientId` | `camunda.client.auth.client-id` | `ZEEBE_CLIENT_ID` |
| `app.auth.oauth.clientSecret` | `camunda.client.auth.client-secret` | `ZEEBE_CLIENT_SECRET` |
| `app.auth.oauth.audience` | `camunda.client.auth.audience` | `ZEEBE_TOKEN_AUDIENCE` |
| `app.auth.oauth.authzUrl` | `camunda.client.auth.token-url` | `ZEEBE_AUTHORIZATION_SERVER_URL` |

##### Starter

| Old HOCON | New Spring Boot | Env var |
|-----------|-----------------|---------|
| `app.starter.processId` | `load-tester.starter.process-id` | `LOAD_TESTER_STARTER_PROCESS_ID` |
| `app.starter.rate` | `load-tester.starter.rate` | `LOAD_TESTER_STARTER_RATE` |
| `app.starter.rateDuration` | `load-tester.starter.rate-duration` | `LOAD_TESTER_STARTER_RATE_DURATION` |
| `app.starter.threads` | `load-tester.starter.threads` | `LOAD_TESTER_STARTER_THREADS` |
| `app.starter.bpmnXmlPath` | `load-tester.starter.bpmn-xml-path` | `LOAD_TESTER_STARTER_BPMN_XML_PATH` |
| `app.starter.extraBpmnModels` | `load-tester.starter.extra-bpmn-models` | `LOAD_TESTER_STARTER_EXTRA_BPMN_MODELS` |
| `app.starter.businessKey` | `load-tester.starter.business-key` | `LOAD_TESTER_STARTER_BUSINESS_KEY` |
| `app.starter.payloadPath` | `load-tester.starter.payload-path` | `LOAD_TESTER_STARTER_PAYLOAD_PATH` |
| `app.starter.withResults` | `load-tester.starter.with-results` | `LOAD_TESTER_STARTER_WITH_RESULTS` |
| `app.starter.durationLimit` | `load-tester.starter.duration-limit` | `LOAD_TESTER_STARTER_DURATION_LIMIT` |
| `app.starter.startViaMessage` | `load-tester.starter.start-via-message` | `LOAD_TESTER_STARTER_START_VIA_MESSAGE` |

##### Worker

Worker properties are split across two namespaces:

**Framework-level properties** (via `camunda.client.worker.defaults.*`):

These control how the Spring Boot starter creates the job worker subscription (job activation).
They are applied automatically by the `camunda-spring-boot-starter` to the `@JobWorker` method.
`Worker.java` never reads these directly.

| Old HOCON | New Spring Boot | Helm env var (bridged) |
|-----------|-----------------|------------------------|
| `app.worker.jobType` | `camunda.client.worker.defaults.type` | `LOAD_TESTER_WORKER_JOB_TYPE` |
| `app.worker.workerName` | `camunda.client.worker.defaults.name` | `LOAD_TESTER_WORKER_WORKER_NAME` |
| `app.worker.capacity` | `camunda.client.worker.defaults.max-jobs-active` | `LOAD_TESTER_WORKER_CAPACITY` |
| `app.worker.pollingDelay` | `camunda.client.worker.defaults.poll-interval` | `LOAD_TESTER_WORKER_POLLING_DELAY` |
| `app.worker.streamEnabled` | `camunda.client.worker.defaults.stream-enabled` | - |
| `app.worker.timeout` | `camunda.client.worker.defaults.timeout` | - (static: 1800ms) |

**Application-level properties** (via `load-tester.worker.*`):

These control what `Worker.java` does **after** receiving a job (completion delay, message
publishing, payload). They are custom `@ConfigurationProperties` read directly by the handler.

| Old HOCON | New Spring Boot | Env var |
|-----------|-----------------|---------|
| `app.worker.completionDelay` | `load-tester.worker.completion-delay` | `LOAD_TESTER_WORKER_COMPLETION_DELAY` |
| `app.worker.payloadPath` | `load-tester.worker.payload-path` | `LOAD_TESTER_WORKER_PAYLOAD_PATH` |
| `app.worker.sendMessage` | `load-tester.worker.send-message` | `LOAD_TESTER_WORKER_SEND_MESSAGE` |
| `app.worker.messageName` | `load-tester.worker.message-name` | `LOAD_TESTER_WORKER_MESSAGE_NAME` |
| `app.worker.correlationKeyVariableName` | `load-tester.worker.correlation-key-variable-name` | `LOAD_TESTER_WORKER_CORRELATION_KEY_VARIABLE_NAME` |

**Why the split?** The `camunda-spring-boot-starter` natively handles job type, capacity,
polling, streaming, and timeout through its `@JobWorker` integration. Duplicating them in
custom `WorkerProperties` would be redundant. Only properties the starter doesn't know about
(completion delay, message publishing, payload) remain as `load-tester.worker.*` properties.

All env vars are bridged explicitly via `${ENV_VAR:default}` placeholders in `application.yaml`
for both namespaces.

##### General

| Old HOCON | New Spring Boot | Env var |
|-----------|-----------------|---------|
| `app.monitorDataAvailability` | `load-tester.monitor-data-availability` | `LOAD_TESTER_MONITOR_DATA_AVAILABILITY` |
| `app.performReadBenchmarks` | `load-tester.perform-read-benchmarks` | `LOAD_TESTER_PERFORM_READ_BENCHMARKS` |
| `app.disabledQueries` | `load-tester.disabled-queries` | `ZEEBE_DISABLED_QUERIES` |
| `app.monitoringPort` | `server.port` | - (static: 9600) |

### Metrics

**Old:** `App.java` manually created a `PrometheusMeterRegistry`, registered JVM metrics,
applied `PrometheusRenameFilter`, created `MetricCollectingClientInterceptor`, and started
a custom `HTTPServer` on port 9600 serving at `/metrics`.

**New:** Spring Boot Actuator auto-configures `MeterRegistry` and JVM metrics.
`MetricsConfiguration.java` adds the two things not auto-provided:
- `MetricCollectingClientInterceptor` bean (gRPC-level metrics, picked up by the starter)
- `PrometheusRenameFilter` (Prometheus naming conventions for dashboard compatibility)

Metrics are served at `/actuator/prometheus` on port 9600.

**Application-level metrics (unchanged):**
- `app.connected` (Gauge) - 1 when topology retrieved, 0 otherwise
- `starter.response.latency` (Timer) - create-instance request/response time
- `starter.data.availability.latency` (Timer, per partition) - time until instance is queryable
- `starter.data.availability.query.duration` (Timer) - search query duration
- `starter.read.benchmark` (Timer, per query) - read benchmark query latency

### Logging

**Old:** Log4j2 with `log4j2.xml`:
- Root logger: `${env:LOG_LEVEL:-WARN}`
- `io.camunda` logger: `${env:CAMUNDA_LOG_LEVEL:-INFO}`

**New:** Spring Boot Logback (no custom config file). Controlled via:
- `LOGGING_LEVEL_IO_CAMUNDA_ZEEBE=INFO` (set by Helm chart)
- `logging.level.org.apache.hc.client5.http.impl.async.AsyncHttpRequestRetryExec: WARN`
  (suppresses noisy 503 retry messages during gateway startup)

The old `LOG_LEVEL` and `CAMUNDA_LOG_LEVEL` env vars are no longer effective. The Helm chart
already sets both old and new env vars for backward compatibility.

### Docker Images

**Old:** Separate main classes per profile:
- `starter` profile: `mainClass=io.camunda.zeebe.Starter`
- `worker` profile: `mainClass=io.camunda.zeebe.Worker`

**New:** Same main class, different Spring profiles:
- `starter` profile: `mainClass=io.camunda.zeebe.LoadTesterApplication`, args: `--spring.profiles.active=starter`
- `worker` profile: `mainClass=io.camunda.zeebe.LoadTesterApplication`, args: `--spring.profiles.active=worker`

Both use Jib Maven Plugin with Eclipse Temurin JDK 21 base image, producing:
- `gcr.io/zeebe-io/starter:SNAPSHOT`
- `gcr.io/zeebe-io/worker:SNAPSHOT`

## Helm Chart Compatibility

The migration requires **no Helm chart changes**. The existing `camunda-load-tests-helm` chart
works with both old and new app versions because:

1. **Env vars are bridged** - `application.yaml` uses `${LOAD_TESTER_WORKER_JOB_TYPE:default}`
   placeholders to map Helm-provided env vars into `camunda.client.worker.defaults.*` properties.

2. **Dual env vars** - The Helm chart already sets both HOCON-style (`JDK_JAVA_OPTIONS` with
   `-Dapp.*`) and Spring Boot-style (`LOAD_TESTER_*`, `CAMUNDA_CLIENT_*`) env vars.

3. **HOCON args are ignored** - The old `-Dconfig.override_with_env_vars=true` and `-Dapp.*`
   JVM args in `JDK_JAVA_OPTIONS` have no effect (no Typesafe Config on classpath) but are harmless.

### Env vars that are now dead (set by Helm but ignored)

| Env var | Reason |
|---------|--------|
| `JDK_JAVA_OPTIONS` `-Dapp.*` flags | No HOCON library on classpath |
| `-Dconfig.override_with_env_vars=true` | Typesafe Config flag, no effect |
| `CONFIG_FORCE_app_*` | HOCON env override convention |
| `LOG_LEVEL` | Old log4j2 env var, Spring Boot uses `LOGGING_LEVEL_*` |
| `LOAD_TESTER_WORKER_THREADS` | Was dead config, not wired to Spring Boot starter |

These can be cleaned up in a future Helm chart update but cause no issues.

## File Structure

```
load-tester/
  src/main/java/io/camunda/zeebe/
    LoadTesterApplication.java          # Spring Boot entry point
    config/
      LoadTesterProperties.java         # @ConfigurationProperties(prefix = "load-tester")
      StarterProperties.java            # Starter-specific config
      WorkerProperties.java             # Worker app-level config (completionDelay, payload, message)
      MetricsConfiguration.java         # gRPC interceptor + PrometheusRenameFilter
    starter/
      Starter.java                      # @Profile("starter") CommandLineRunner
    worker/
      Worker.java                       # @Profile("worker") @JobWorker handler
      ResponseChecker.java              # Async response error monitor thread
    metrics/
      Clock.java                        # Testable time abstraction
      AppMetricsDoc.java                # Shared metric docs (CONNECTED gauge)
      StarterLatencyMetricsDoc.java     # Starter metric docs (response, availability, read)
      ProcessInstanceStartMeter.java    # Data availability tracking
    read/
      DataReadMeter.java                # Periodic read benchmark executor
      DataReadMeterQueryProvider.java   # Default read query definitions
    util/
      PayloadReader.java                # JSON payload file reader
  src/main/resources/
    application.yaml                    # Spring Boot config (replaces application.conf)
    bpmn/                               # BPMN processes and payloads (unchanged)
  src/test/
    java/                               # Unit + integration tests
    resources/
      application.yaml                  # Test config (disables CamundaClient)
      application-it.yaml               # Integration test config
```
