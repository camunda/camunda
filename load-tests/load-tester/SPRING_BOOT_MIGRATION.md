# Load Tester — Spring Boot Migration Guide

This document explains how the load-tester app was migrated from a standalone HOCON-based
Java application to Spring Boot. For each area it describes how things worked before, what
changed, and why.

---

## 1. High-Level Architecture

### Before (HOCON — `main` branch)

The load tester was a plain Java application with no framework:

- **Typesafe Config** (`application.conf`) provided HOCON-based configuration with built-in
  env-var overrides via the `${?ENV_VAR}` syntax.
- **`App.java`** was the abstract base class shared by `Starter` and `Worker`. It handled:
  client creation, auth, Prometheus HTTP server, JVM metrics, gRPC interceptor, topology
  printing, and payload reading.
- **Two separate `main()` methods**: `Starter.main()` and `Worker.main()` each called
  `App.createApp(...)` which loaded config, started monitoring, and called `run()`.
- The Jib Docker build produced two images — one per main class.

```
Starter.main()  ──┐
                   ├── App.createApp(factory)
Worker.main()  ───┘       │
                           ├── AppConfigLoader.load()   →  application.conf → AppCfg POJO
                           ├── app.startMonitoringServer() → Prometheus HTTPServer on :9600
                           └── app.run()                →  Starter/Worker logic
```

### After (Spring Boot — this branch)

The load tester is a Spring Boot application that delegates most infrastructure to the
framework:

- **`camunda-spring-boot-starter`** auto-configures the `CamundaClient`, `@JobWorker`
  subscriptions, and Spring property binding.
- **Spring profiles** (`starter` / `worker`) activate the correct component at runtime.
- **One main class** (`LoadTesterApplication`) with `@SpringBootApplication`.
- **Spring Boot Actuator** serves health checks and Prometheus metrics.
- The Jib Docker build still produces two images, but both share the same main class and
  differ only in `--spring.profiles.active=<role>`.

```
LoadTesterApplication.main()
  │
  ├── Spring Boot auto-configuration
  │     ├── CamundaClient bean (auth, addresses, worker defaults)
  │     ├── MeterRegistry (Micrometer + Prometheus)
  │     └── Actuator endpoints (/health, /actuator/prometheus)
  │
  ├── @Profile("starter") → Starter (CommandLineRunner)
  └── @Profile("worker")  → Worker (@JobWorker handler)
```

### Side-by-Side Comparison

| Aspect | Old (HOCON) | New (Spring Boot) |
|--------|-------------|-------------------|
| Framework | None (plain Java) | Spring Boot 4.x + `camunda-spring-boot-starter` |
| Configuration | `application.conf` (Typesafe HOCON) | `application.yaml` + `@ConfigurationProperties` |
| Config loading | `ConfigFactory.load()` → `ConfigBeanFactory.create()` | Spring relaxed binding + env var placeholders |
| Client creation | Manual `CamundaClientBuilder` in `App.newClientBuilder()` | Auto-configured by starter; injected as a bean |
| Auth setup | Manual `CredentialsProvider` switch on `AuthType` | `camunda.client.auth.*` properties, handled by starter |
| Entry point | `Starter.main()` / `Worker.main()` → `App.createApp()` | Single `LoadTesterApplication` + Spring profiles |
| Role selection | Baked into Docker image (different main class) | Runtime profile: `--spring.profiles.active=starter` |
| Job subscription | `client.newWorker().jobType(t).handler(h).open()` | `@JobWorker(autoComplete = false)` annotation |
| Metrics server | Custom `HTTPServer` on `/metrics` (port 9600) | Spring Boot Actuator on `/actuator/prometheus` (port 9600) |
| JVM metrics | Manually registered (`ClassLoaderMetrics`, `JvmGcMetrics`, etc.) | Auto-registered by Actuator |
| Logging | Log4j2 (`log4j2.xml`) | Logback (Spring Boot default) |
| Dependency injection | None — manual wiring in constructors | Spring `@Component` + constructor injection |
| Shutdown | `Runtime.addShutdownHook(...)` | `@PreDestroy` lifecycle |

---

## 2. Configuration

This is the biggest change. The HOCON file is gone, replaced by a YAML config plus the
standard `camunda-spring-boot-starter` property namespace.

### Before: HOCON (`application.conf`)

All config lived under a single `app {}` block. Typesafe Config deserialized it to POJOs:

```
application.conf → ConfigFactory.load() → ConfigBeanFactory.create(config, AppCfg.class)
                                             ├── AppCfg (root)
                                             │    ├── StarterCfg
                                             │    ├── WorkerCfg
                                             │    └── AuthCfg
```

Env var overrides used the HOCON `${?VAR}` pattern:

```hocon
app {
  brokerUrl = "http://localhost:26500"
  brokerUrl = ${?ZEEBE_GRPC_ADDRESS}        # overridden if env var is set

  worker {
    jobType = "benchmark-task"
    threads = 10                            # execution threads for job handlers
    capacity = 30                           # maxJobsActive
    completionDelay = 300ms
    timeout = 0                             # 0 means completionDelay * 6
  }
}
```

Every property — connection, auth, starter, worker — was in a single flat namespace under
`app {}` and read by a single config POJO hierarchy (`AppCfg` → `StarterCfg` / `WorkerCfg`).

### After: Spring Boot YAML (`application.yaml`)

Config is split across **two namespaces** with different owners:

| Namespace | Owner | Purpose |
|-----------|-------|---------|
| `camunda.client.*` | `camunda-spring-boot-starter` | Client connection, auth, worker defaults (type, capacity, polling, streaming, timeout). Auto-applied to `@JobWorker`. |
| `load-tester.*` | This app (`@ConfigurationProperties`) | App-specific settings the starter doesn't handle: rate, BPMN path, completion delay, message publishing, payload. |

The config flow is:

```
Environment variable (e.g. LOAD_TESTER_STARTER_RATE=500)
  ↓  Spring relaxed binding
application.yaml placeholder: rate: ${LOAD_TESTER_STARTER_RATE:300}
  ↓  @ConfigurationProperties(prefix = "load-tester")
LoadTesterProperties → StarterProperties.rate = 500
  ↓  Constructor injection
Starter.java uses the value
```

#### Why are worker properties split?

In the old app, everything was under `app.worker.*`. In the new app, the
`camunda-spring-boot-starter` handles job activation natively — it reads
`camunda.client.worker.defaults.*` and applies it to every `@JobWorker`. So properties that
control **how jobs are fetched** (type, capacity, polling, streaming, timeout) go to the
starter namespace, while properties that control **what the handler does with the job**
(completion delay, message publishing, payload) stay in `load-tester.worker.*`.

```yaml
# Starter-managed: controls @JobWorker subscription
camunda.client.worker.defaults:
  type: benchmark-task          # ← was app.worker.jobType
  max-jobs-active: 30           # ← was app.worker.capacity
  poll-interval: 1s             # ← was app.worker.pollingDelay

# App-managed: controls handler behavior
load-tester.worker:
  completion-delay: 300ms       # ← was app.worker.completionDelay
  send-message: false           # ← was app.worker.sendMessage
  payload-path: bpmn/big.json   # ← was app.worker.payloadPath
```

### Property Mapping Tables

#### Client / Connection

| Old HOCON (`app.*`) | New Spring Boot | Helm env var |
|---------------------|-----------------|--------------|
| `brokerUrl` | `camunda.client.grpc-address` | `ZEEBE_GRPC_ADDRESS` |
| `brokerRestUrl` | `camunda.client.rest-address` | `ZEEBE_REST_ADDRESS` |
| `preferRest` | `camunda.client.prefer-rest-over-grpc` | — |
| `auth.type` (NONE/BASIC/OAUTH) | `camunda.client.auth.method` (none/basic/oidc) | `ZEEBE_AUTH_METHOD` |
| `auth.oauth.clientId` | `camunda.client.auth.client-id` | `ZEEBE_CLIENT_ID` |
| `auth.oauth.clientSecret` | `camunda.client.auth.client-secret` | `ZEEBE_CLIENT_SECRET` |
| `auth.oauth.audience` | `camunda.client.auth.audience` | `ZEEBE_TOKEN_AUDIENCE` |
| `auth.oauth.authzUrl` | `camunda.client.auth.token-url` | `ZEEBE_AUTHORIZATION_SERVER_URL` |

#### Starter

| Old HOCON (`app.starter.*`) | New Spring Boot (`load-tester.starter.*`) | Helm env var |
|-----------------------------|-------------------------------------------|--------------|
| `processId` | `process-id` | `LOAD_TESTER_STARTER_PROCESS_ID` |
| `rate` | `rate` | `LOAD_TESTER_STARTER_RATE` |
| `rateDuration` | `rate-duration` | `LOAD_TESTER_STARTER_RATE_DURATION` |
| `threads` | `threads` | `LOAD_TESTER_STARTER_THREADS` |
| `bpmnXmlPath` | `bpmn-xml-path` | `LOAD_TESTER_STARTER_BPMN_XML_PATH` |
| `extraBpmnModels` | `extra-bpmn-models` | `LOAD_TESTER_STARTER_EXTRA_BPMN_MODELS_*` |
| `businessKey` | `business-key` | `LOAD_TESTER_STARTER_BUSINESS_KEY` |
| `payloadPath` | `payload-path` | `LOAD_TESTER_STARTER_PAYLOAD_PATH` |
| `withResults` | `with-results` | `LOAD_TESTER_STARTER_WITH_RESULTS` |
| `durationLimit` | `duration-limit` | `LOAD_TESTER_STARTER_DURATION_LIMIT` |
| `startViaMessage` | `start-via-message` | `LOAD_TESTER_STARTER_START_VIA_MESSAGE` |

#### Worker — framework-level (job activation)

| Old HOCON (`app.worker.*`) | New Spring Boot (`camunda.client.worker.defaults.*`) | Helm env var |
|----------------------------|------------------------------------------------------|--------------|
| `jobType` | `type` | `LOAD_TESTER_WORKER_JOB_TYPE` |
| `workerName` | `name` | `LOAD_TESTER_WORKER_WORKER_NAME` |
| `capacity` | `max-jobs-active` | `LOAD_TESTER_WORKER_CAPACITY` |
| `pollingDelay` | `poll-interval` | `LOAD_TESTER_WORKER_POLLING_DELAY` |
| `streamEnabled` | `stream-enabled` | — |
| `timeout` | `timeout` | — (static: 1800ms) |
| `threads` | `camunda.client.execution-threads` | `LOAD_TESTER_WORKER_THREADS` |

#### Worker — application-level (handler behavior)

| Old HOCON (`app.worker.*`) | New Spring Boot (`load-tester.worker.*`) | Helm env var |
|----------------------------|------------------------------------------|--------------|
| `completionDelay` | `completion-delay` | `LOAD_TESTER_WORKER_COMPLETION_DELAY` |
| `payloadPath` | `payload-path` | `LOAD_TESTER_WORKER_PAYLOAD_PATH` |
| `sendMessage` | `send-message` | `LOAD_TESTER_WORKER_SEND_MESSAGE` |
| `messageName` | `message-name` | `LOAD_TESTER_WORKER_MESSAGE_NAME` |
| `correlationKeyVariableName` | `correlation-key-variable-name` | `LOAD_TESTER_WORKER_CORRELATION_KEY_VARIABLE_NAME` |

#### General

| Old HOCON (`app.*`) | New Spring Boot | Helm env var |
|---------------------|-----------------|--------------|
| `monitorDataAvailability` | `load-tester.monitor-data-availability` | `LOAD_TESTER_MONITOR_DATA_AVAILABILITY` |
| `performReadBenchmarks` | `load-tester.perform-read-benchmarks` | `LOAD_TESTER_PERFORM_READ_BENCHMARKS` |
| `disabledQueries` | `load-tester.disabled-queries` | `ZEEBE_DISABLED_QUERIES` |
| `monitoringPort` | `server.port` | — (static: 9600) |

---

## 3. Client Creation & Authentication

### Before

`App.newClientBuilder()` manually constructed a `CamundaClientBuilder`:

```java
// In App.java (shared base class)
protected CamundaClientBuilder newClientBuilder() {
    final CamundaClientBuilder builder = CamundaClient.newClientBuilder()
        .grpcAddress(URI.create(config.getBrokerUrl()))
        .restAddress(URI.create(config.getBrokerRestUrl()))
        .preferRestOverGrpc(config.isPreferRest())
        .withProperties(System.getProperties())
        .withInterceptors(monitoringInterceptor);

    // Manual auth switch: NONE → NoopCredentialsProvider, BASIC → BasicAuth, OAUTH → OAuth2
    final var credentialsProvider = switch (auth.getType()) {
        case NONE  -> new NoopCredentialsProvider();
        case BASIC -> CredentialsProvider.newBasicAuthCredentialsProviderBuilder()...build();
        case OAUTH -> CredentialsProvider.newCredentialsProviderBuilder()...build();
    };
    return builder.credentialsProvider(credentialsProvider);
}

// In Worker.java
private CamundaClient createCamundaClient() {
    return newClientBuilder()
        .numJobWorkerExecutionThreads(workerCfg.getThreads())   // 10 threads
        .defaultJobWorkerName(workerCfg.getWorkerName())
        .defaultJobTimeout(timeout)
        .defaultJobWorkerMaxJobsActive(workerCfg.getCapacity())
        .defaultJobPollInterval(workerCfg.getPollingDelay())
        .build();
}

// In Starter.java
private CamundaClient createCamundaClient() {
    return newClientBuilder()
        .numJobWorkerExecutionThreads(0)  // Starter has no workers
        .build();
}
```

Each sub-class configured the client differently (Starter with 0 threads, Worker with 10).

### After

The `camunda-spring-boot-starter` auto-configures a `CamundaClient` bean from
`camunda.client.*` properties. No manual builder code:

```yaml
# application.yaml — this is all the config needed
camunda:
  client:
    mode: self-managed
    grpc-address: ${ZEEBE_GRPC_ADDRESS:http://localhost:26500}
    rest-address: ${ZEEBE_REST_ADDRESS:http://localhost:8080}
    execution-threads: ${LOAD_TESTER_WORKER_THREADS:10}
    auth:
      method: ${ZEEBE_AUTH_METHOD:oidc}
      client-id: ${ZEEBE_CLIENT_ID:orchestration}
      client-secret: ${ZEEBE_CLIENT_SECRET:demo}
      audience: ${ZEEBE_TOKEN_AUDIENCE:orchestration-api}
      token-url: ${ZEEBE_AUTHORIZATION_SERVER_URL:...}
```

Both Starter and Worker inject the same auto-configured `CamundaClient`:

```java
public Starter(final CamundaClient client, ...) { ... }   // injected by Spring
public Worker(final CamundaClient client, ...) { ... }     // same bean
```

**What was removed:** ~60 lines of manual client builder code (`App.newClientBuilder()`,
per-role `createCamundaClient()`, credentials provider switch, credentials cache management).

---

## 4. Starter Component

### Before

`Starter extends App implements Runnable`. The `main()` method called `App.createApp()`
which bootstrapped monitoring, loaded config, and called `run()`:

```java
public class Starter extends App {
    public static void main(String[] args) { createApp(Starter::new); }

    @Override
    public void run() {
        CamundaClient client = createCamundaClient();  // manual
        printTopology(client);
        deployProcess(client, starterCfg);
        scheduleProcessInstanceCreation(client, ...);
        countDownLatch.await();
    }
}
```

### After

`@Component @Profile("starter") implements CommandLineRunner`. Spring handles lifecycle:

```java
@Component
@Profile("starter")
public class Starter implements CommandLineRunner {
    public Starter(CamundaClient client, LoadTesterProperties properties,
                   MeterRegistry registry, PayloadReader payloadReader) {
        // All dependencies injected by Spring
    }

    @Override
    public void run(String... args) {
        printTopology();            // same logic, uses injected client
        deployProcess();            // same retry loop
        scheduleProcessInstanceCreation();
        countDownLatch.await();
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdown();  // replaces Runtime.addShutdownHook()
    }
}
```

**Behavior is identical** — the same rate-based scheduling, topology printing, deploy
retries, three start modes (normal / with-results / via-message), data availability
monitoring, and read benchmarks. Only the infrastructure around it changed.

---

## 5. Worker Component

### Before

`Worker extends App implements Runnable`. Manually opened a job worker subscription:

```java
public class Worker extends App {
    public static void main(String[] args) { createApp(Worker::new); }

    @Override
    public void run() {
        CamundaClient client = createCamundaClient();  // 10 execution threads
        printTopology(client);

        // Manual subscription
        JobWorker worker = client.newWorker()
            .jobType(workerCfg.getJobType())
            .handler(handleJob(client, variables, completionDelay, requestFutures))
            .streamEnabled(isStreamEnabled)
            .metrics(metrics)
            .open();

        ResponseChecker responseChecker = new ResponseChecker(requestFutures);
        responseChecker.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            worker.close();
            client.close();
            responseChecker.close();
        }));
    }
}
```

The `handler(handleJob(...))` returned a `JobHandler` lambda that:
1. Optionally published a message (for realistic workflows).
2. Called `jobClient.newCompleteCommand(job.getKey()).variables(variables)`.
3. Applied `completionDelay` via `Thread.sleep()`.
4. Added the response future to a queue for `ResponseChecker` to monitor.

### After

`@Component @Profile("worker")` with `@JobWorker(autoComplete = false)`:

```java
@Component
@Profile("worker")
public class Worker {

    @PostConstruct
    void printTopology() { /* same retry loop */ }

    @JobWorker(autoComplete = false)
    public void handleJob(JobClient jobClient, ActivatedJob job) {
        // Same logic — message publishing, complete command, delay, ResponseChecker
    }
}
```

**Key differences:**

1. **No manual subscription** — `@JobWorker` replaces `client.newWorker().jobType().handler().open()`.
2. **Job type resolution** — comes from `camunda.client.worker.defaults.type` (bridged from
   `LOAD_TESTER_WORKER_JOB_TYPE` env var). When Helm sets `LOAD_TESTER_WORKER_JOB_TYPE=refunding`,
   the `@JobWorker` subscribes to `refunding`.
3. **Thread pool** — was `numJobWorkerExecutionThreads(10)`, now `camunda.client.execution-threads: 10`.
4. **Lifecycle** — `@PostConstruct` replaces the `run()` init sequence; Spring handles shutdown.

**Handler behavior is identical** — completion delay, message publishing, ResponseChecker
queue pattern, error handling — all preserved without changes.

---

## 6. Metrics & Monitoring

### Before

`App.startMonitoringServer()` manually set up everything:

```java
// Create registry
registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
registry.config().meterFilter(new PrometheusRenameFilter());

// Start custom HTTP server
HTTPServer.builder()
    .port(config.getMonitoringPort())  // 9600
    .registry(registry.getPrometheusRegistry())
    .buildAndStart();

// Create gRPC interceptor
monitoringInterceptor = new MetricCollectingClientInterceptor(registry);

// Register JVM metrics
new ClassLoaderMetrics().bindTo(registry);
new JvmMemoryMetrics().bindTo(registry);
new JvmGcMetrics().bindTo(registry);
new ProcessorMetrics().bindTo(registry);
new JvmThreadMetrics().bindTo(registry);
```

Metrics were served at `http://localhost:9600/metrics`.

### After

Spring Boot Actuator auto-configures `MeterRegistry`, JVM metrics, and the HTTP endpoint.
`MetricsConfiguration.java` adds only what Actuator doesn't provide:

```java
@Configuration
public class MetricsConfiguration {
    @PostConstruct
    void applyPrometheusRenameFilter() {
        registry.config().meterFilter(new PrometheusRenameFilter());
    }

    @Bean
    public ClientInterceptor grpcMetricsInterceptor() {
        return new MetricCollectingClientInterceptor(registry);
    }
}
```

Metrics are served at `http://localhost:9600/actuator/prometheus`.

**What was removed:** ~30 lines of manual Prometheus HTTP server setup and JVM metric
registration. The `PrometheusRenameFilter` and gRPC interceptor are kept because Actuator
doesn't auto-configure them.

**Application-level metrics are unchanged:**
- `app.connected` (Gauge) — 1 when topology retrieved
- `starter.response.latency` (Timer) — create-instance request/response time
- `starter.data.availability.latency` (Timer, per partition) — time until instance is queryable
- `starter.data.availability.query.duration` (Timer) — search query duration
- `starter.read.benchmark` (Timer, per query) — read benchmark query latency

> **Dashboard note:** Grafana dashboards that scrape `/metrics` need to be updated to
> `/actuator/prometheus`. The metric names themselves are unchanged thanks to
> `PrometheusRenameFilter`.

---

## 7. Logging

### Before

Log4j2 with `log4j2.xml`:

```xml
<Root level="${env:LOG_LEVEL:-WARN}">
  <AppenderRef ref="Console" />
</Root>
<Logger name="io.camunda" level="${env:CAMUNDA_LOG_LEVEL:-INFO}" />
```

### After

Spring Boot Logback (no custom config file). Controlled via standard Spring Boot properties:

```yaml
logging:
  level:
    # Suppress noisy HTTP retry messages during gateway startup
    org.apache.hc.client5.http.impl.async.AsyncHttpRequestRetryExec: WARN
```

The Helm chart sets `LOGGING_LEVEL_IO_CAMUNDA_ZEEBE=INFO` for the `io.camunda.zeebe` package.

**Dead env vars:** `LOG_LEVEL` and `CAMUNDA_LOG_LEVEL` from Log4j2 are no longer effective.
The Helm chart already sets both old and new env vars.

---

## 8. Docker Images

### Before

Separate main classes, configured via Maven profiles:

```xml
<profile>
  <id>starter</id>
  <container>
    <mainClass>io.camunda.zeebe.Starter</mainClass>     <!-- Starter's own main() -->
  </container>
  <to><image>gcr.io/zeebe-io/starter:SNAPSHOT</image></to>
</profile>

<profile>
  <id>worker</id>
  <container>
    <mainClass>io.camunda.zeebe.Worker</mainClass>       <!-- Worker's own main() -->
  </container>
  <to><image>gcr.io/zeebe-io/worker:SNAPSHOT</image></to>
</profile>
```

### After

Same main class, different Spring profiles passed as container args:

```xml
<profile>
  <id>starter</id>
  <container>
    <mainClass>io.camunda.zeebe.LoadTesterApplication</mainClass>
    <args>
      <arg>--spring.profiles.active=starter</arg>
    </args>
  </container>
  <to><image>gcr.io/zeebe-io/starter:SNAPSHOT</image></to>
</profile>

<profile>
  <id>worker</id>
  <container>
    <mainClass>io.camunda.zeebe.LoadTesterApplication</mainClass>
    <args>
      <arg>--spring.profiles.active=worker</arg>
    </args>
  </container>
  <to><image>gcr.io/zeebe-io/worker:SNAPSHOT</image></to>
</profile>
```

Both images use Eclipse Temurin JDK 21 and produce the same image names:
`gcr.io/zeebe-io/starter:SNAPSHOT` and `gcr.io/zeebe-io/worker:SNAPSHOT`.

---

## 9. Helm Chart Compatibility

The migration requires **no Helm chart changes**. The existing
[camunda-load-tests-helm](https://github.com/camunda/camunda-load-tests-helm) chart works
with both old and new app versions because:

1. **Env vars are bridged** — `application.yaml` uses `${LOAD_TESTER_WORKER_JOB_TYPE:default}`
   placeholders to map Helm-provided env vars into `camunda.client.worker.defaults.*`
   properties.

2. **Dual env vars** — The Helm chart already sets both HOCON-style (`JDK_JAVA_OPTIONS` with
   `-Dapp.*`) and Spring Boot-style (`LOAD_TESTER_*`, `CAMUNDA_CLIENT_*`) env vars.

3. **HOCON args are ignored** — The old `-Dconfig.override_with_env_vars=true` and `-Dapp.*`
   JVM args in `JDK_JAVA_OPTIONS` have no effect (no Typesafe Config on classpath) but are
   harmless.

### Dead env vars (set by Helm but now ignored)

| Env var | Reason |
|---------|--------|
| `JDK_JAVA_OPTIONS` `-Dapp.*` flags | No HOCON library on classpath |
| `-Dconfig.override_with_env_vars=true` | Typesafe Config flag, no effect |
| `CONFIG_FORCE_app_*` | HOCON env override convention |
| `LOG_LEVEL` / `CAMUNDA_LOG_LEVEL` | Log4j2 env vars; Spring Boot uses `LOGGING_LEVEL_*` |

These can be cleaned up in a future Helm chart update but cause no issues.

---

## 10. Known Quirks & Workarounds

### Quirk 1: `extraBpmnModels` env var binding

**Problem:** Spring Boot's relaxed binding cannot map indexed env vars like
`LOAD_TESTER_STARTER_EXTRA_BPMN_MODELS_0_` to the `extra-bpmn-models` list property.
The underscore in `LOAD_TESTER` is ambiguous — Spring can't tell if it's `load.tester` or
`load-tester`, so indexed list binding fails silently.

**Workaround:** `StarterProperties.getExtraBpmnModels()` falls back to reading
`System.getenv()` directly when the Spring-bound list is empty:

```java
public List<String> getExtraBpmnModels() {
    if (extraBpmnModels.isEmpty()) {
        List<String> fromEnv = new ArrayList<>();
        for (int i = 0; ; i++) {
            String val = System.getenv("LOAD_TESTER_STARTER_EXTRA_BPMN_MODELS_" + i + "_");
            if (val == null || val.isBlank()) break;
            fromEnv.add(val);
        }
        if (!fromEnv.isEmpty()) extraBpmnModels = fromEnv;
    }
    return extraBpmnModels;
}
```

This is needed for realistic benchmarks where the Helm chart sets DMN/BPMN models via
indexed env vars.

### Quirk 2: Execution threads default is 1

**Problem:** The old app used `numJobWorkerExecutionThreads(10)` explicitly. The Spring Boot
starter's default is **1 thread**, which is far too low for a load tester whose handler
blocks with `Thread.sleep(completionDelay)`.

**Fix:** `application.yaml` bridges the Helm env var:

```yaml
camunda.client.execution-threads: ${LOAD_TESTER_WORKER_THREADS:10}
```

Without this, the worker can only process ~3.3 jobs/sec (1 thread ÷ 300ms sleep).

### Quirk 3: Worker timeout is static

**Problem:** The old app computed the job timeout dynamically:
`timeout = (worker.timeout != 0) ? worker.timeout : completionDelay * 6`. Spring Boot YAML
cannot express this conditional, so the timeout is hardcoded to `1800ms` (300ms × 6).

**Impact:** If you change `completionDelay`, you must manually update
`camunda.client.worker.defaults.timeout` to match.

---

## 11. Dependencies

### Removed

| Dependency | Purpose |
|------------|---------|
| `com.typesafe:config` | HOCON configuration loading |
| `org.apache.logging.log4j:log4j-*` | Log4j2 logging |
| `io.prometheus:prometheus-metrics-exporter-httpserver` | Custom Prometheus HTTP server |

### Added

| Dependency | Purpose |
|------------|---------|
| `camunda-spring-boot-starter` | Auto-configures `CamundaClient`, `@JobWorker`, Spring properties |
| `spring-boot-starter-actuator` | Health checks, Prometheus metrics endpoint |
| `spring-boot-starter-web` | Embedded HTTP server for Actuator |
| `camunda-process-test-java` (test) | `CamundaContainer` for integration tests |
| `spring-boot-starter-test` (test) | JUnit 5 + Spring test utilities |

---

## 12. Tests

### Before

No tests existed for the old HOCON-based load tester.

### After

The migration added a full test suite:

**Unit tests (18 tests):**
- `ConfigTest` — verifies `@ConfigurationProperties` binding and env var overrides.
- `ProcessInstanceStartMeterTest` — data availability metric recording.
- `DataReadMeterTest` / `DataReadMeterQueryProviderTest` — read benchmark logic.

**Integration tests (3 tests, using Testcontainers + `CamundaContainer`):**
- `StarterIT` — deploys a process and creates instances via the starter.
- `WorkerIT` — deploys a process, creates an instance, and verifies the `@JobWorker`
  completes the job.
- `DataAvailabilityIT` — measures data availability latency end-to-end.

Tests use two Spring profiles: `it` (enables the Camunda client with auth disabled) and
the role profile (`starter` / `worker`).

---

## 13. File Structure

```
load-tester/
  src/main/java/io/camunda/zeebe/
    LoadTesterApplication.java          # @SpringBootApplication entry point
    config/
      LoadTesterProperties.java         # @ConfigurationProperties(prefix = "load-tester")
      StarterProperties.java            # Starter config (rate, BPMN paths, etc.)
      WorkerProperties.java             # Worker handler config (delay, payload, message)
      MetricsConfiguration.java         # gRPC interceptor + PrometheusRenameFilter
    starter/
      Starter.java                      # @Profile("starter") CommandLineRunner
    worker/
      Worker.java                       # @Profile("worker") @JobWorker handler
      ResponseChecker.java              # Async response error monitor thread
    metrics/
      Clock.java                        # Testable time abstraction
      AppMetricsDoc.java                # Shared metric names (CONNECTED gauge)
      StarterLatencyMetricsDoc.java     # Starter metric names (response, availability, read)
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
    java/io/camunda/zeebe/
      config/ConfigTest.java            # Config binding unit tests
      it/StarterIT.java                 # Starter integration test
      it/WorkerIT.java                  # Worker integration test
      it/DataAvailabilityIT.java        # Data availability integration test
      it/CamundaContainerProvider.java  # Shared Testcontainers utility
    resources/
      application.yaml                  # Test config (disables CamundaClient)
      application-it.yaml              # IT config (enables client, uses gRPC)
```
