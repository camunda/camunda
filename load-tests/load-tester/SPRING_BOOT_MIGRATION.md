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
  │     └── Actuator endpoints (/health, /metrics — remapped from /actuator/*)
  │
  ├── ConnectionMonitor (shared @Component — owns the `app.connected` gauge
  │                      and the topology-retry loop used by both roles)
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
| Metrics server | Custom `HTTPServer` on `/metrics` (port 9600) | Spring Boot Actuator on `/metrics` (port 9600, remapped from `/actuator/prometheus`) |
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

`@Component @Profile("starter") implements CommandLineRunner`. Spring handles lifecycle.
Topology retry + the `app.connected` gauge were extracted into a shared
`ConnectionMonitor` component so both Starter and Worker reuse the same code:

```java
@Component
@Profile("starter")
public class Starter implements CommandLineRunner {
    public Starter(CamundaClient client, LoadTesterProperties properties,
                   MeterRegistry registry, PayloadReader payloadReader,
                   ConnectionMonitor connectionMonitor) {
        // All dependencies injected by Spring
    }

    @Override
    public void run(String... args) {
        connectionMonitor.awaitAndPrintTopology();   // shared retry + gauge flip
        deployProcess();                              // same retry loop as main
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

`@Component @Profile("worker")` with `@JobWorker(autoComplete = false)`. The `ConnectionMonitor`
component is injected and called in `@PostConstruct`, so `app.connected` is flipped to 1
**before** the `camunda-spring-boot-starter` opens the job stream:

```java
@Component
@Profile("worker")
public class Worker {
    public Worker(CamundaClient client, LoadTesterProperties properties,
                  PayloadReader payloadReader,
                  ConnectionMonitor connectionMonitor) { /* ... */ }

    @PostConstruct
    void awaitTopologyAndLogConfig() {
        connectionMonitor.awaitAndPrintTopology();   // shared retry + gauge flip
        LOGGER.info("Worker config: completionDelay={}, sendMessage={}, ...", /* ... */);
    }

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

Metrics are served at `http://localhost:9600/metrics` (Actuator's Prometheus endpoint is
remapped in `application.yaml` via `management.endpoints.web.base-path: /` +
`path-mapping.prometheus: metrics` — health then lives at `/health`). This matches the
old HOCON app's path so the Helm `ServiceMonitor` keeps scraping without a chart change.

**What was removed:** ~30 lines of manual Prometheus HTTP server setup and JVM metric
registration. The `PrometheusRenameFilter` and gRPC interceptor are kept because Actuator
doesn't auto-configure them.

**Application-level metrics are unchanged:**
- `app.connected` (Gauge) — 1 when topology retrieved. Registered by the shared
  `ConnectionMonitor` bean (owns the `AtomicInteger` behind it) so both starter and worker
  pods publish the same metric without duplicating code.
- `starter.response.latency` (Timer) — create-instance request/response time
- `starter.data.availability.latency` (Timer, per partition) — time until instance is queryable
- `starter.data.availability.query.duration` (Timer) — search query duration
- `starter.read.benchmark` (Timer, per query) — read benchmark query latency

**Verifying parity with `main`:** grep `/metrics` output from a HOCON pod and a Spring Boot
pod, compare families:

```bash
kubectl exec -n <ns> <pod> -- wget -qO- http://localhost:9600/metrics \
  | grep '^# HELP' | awk '{print $3}' | sort -u
```

All five JVM binders that were manually registered in the old `App.java`
(`ClassLoaderMetrics`, `JvmMemoryMetrics`, `JvmGcMetrics`, `ProcessorMetrics`,
`JvmThreadMetrics`) are produced by Actuator auto-config. 0 regressions; Actuator adds
~29 extra metric families on top (uptime, fd count, executor stats, HTTP endpoint
latency, logback events).

> **Dashboard note:** Actuator is remapped in `application.yaml` so Prometheus is
> served at `/metrics` (not `/actuator/prometheus`) and health at `/health`. This
> keeps the existing Helm-provided `ServiceMonitor` (which scrapes `path: /metrics`)
> working without any chart change — Grafana dashboards and scrape configs do not
> need updating. Metric names are unchanged thanks to `PrometheusRenameFilter`.

---

## 7. Logging

### Before

Log4j2 with `log4j2.xml`:

```xml
<Appenders>
  <Console name="Console" target="SYSTEM_OUT">
    <PatternLayout pattern="%d{HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n"/>
  </Console>
</Appenders>
<Loggers>
  <Root level="${env:LOG_LEVEL:-WARN}">
    <AppenderRef ref="Console" />
  </Root>
  <Logger name="io.camunda" level="${env:CAMUNDA_LOG_LEVEL:-INFO}" />
</Loggers>
```

### After

Spring Boot Logback (no custom config file — Spring Boot's bundled `base.xml` wires up a
`CONSOLE` appender writing to stdout, which is what Kubernetes tails via `kubectl logs`).
Only the root level is configured in `application.yaml`:

```yaml
logging:
  level:
    root: ${LOG_LEVEL:INFO}
```

### How env-var overrides reach the loggers

The Helm chart sets two env vars from the same user-facing value (so the same Helm knob
works for log4j2 on `main` and for Spring Boot here):

| Env var | Read by | Effect |
|---|---|---|
| `LOG_LEVEL` | Our `${LOG_LEVEL:INFO}` placeholder | Sets `logging.level.root` — framework noise (Apache HTTP, Netty, gRPC, Spring) |
| `LOGGING_LEVEL_IO_CAMUNDA_ZEEBE` | Spring Boot's native relaxed binding on `logging.level.*` | Sets `logging.level.io.camunda.zeebe` — Camunda app code |

We deliberately do **not** bridge `CAMUNDA_LOG_LEVEL` — nothing in the Helm chart sets
it; it was a placeholder name invented by the old `log4j2.xml` itself. Adding a
`${CAMUNDA_LOG_LEVEL:…}` line would be misleading plumbing with no source.

### What about the console appender and log format?

Spring Boot's default console appender writes to stdout with ISO-8601 timestamps and a
thread/logger pattern that is functionally equivalent to the old log4j2 pattern, just
cosmetically different. Grafana/Loki/kubectl all work identically because they tail
lines, not formats. No custom `PatternLayout` needed.

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
    <jvmFlags>
      <jvmFlag>-XX:MaxRAMPercentage=60</jvmFlag>
      <jvmFlag>-XX:MaxMetaspaceSize=128m</jvmFlag>
      <jvmFlag>-XX:MaxDirectMemorySize=64m</jvmFlag>
    </jvmFlags>
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
    <jvmFlags>
      <jvmFlag>-XX:MaxRAMPercentage=60</jvmFlag>
      <jvmFlag>-XX:MaxMetaspaceSize=128m</jvmFlag>
      <jvmFlag>-XX:MaxDirectMemorySize=64m</jvmFlag>
    </jvmFlags>
  </container>
  <to><image>gcr.io/zeebe-io/worker:SNAPSHOT</image></to>
</profile>
```

Both images use Eclipse Temurin JDK 21 and produce the same image names:
`gcr.io/zeebe-io/starter:SNAPSHOT` and `gcr.io/zeebe-io/worker:SNAPSHOT`.

### Memory footprint (why the JVM flags matter)

Helm allocates `256Mi` per worker pod — the limit was tuned for the lean HOCON build.
The Spring Boot build carries ~60 MiB of extra baseline (Netty + Actuator + Spring
auto-config + Jackson + Micrometer), leaving almost no headroom. The three JVM flags:

| Flag | Purpose |
|------|---------|
| `-XX:MaxRAMPercentage=60` | Caps `Xmx` at ~150 MiB on a 256 Mi container, reserving ~100 MiB for Metaspace + Netty direct buffers + thread stacks + JVM native. |
| `-XX:MaxMetaspaceSize=128m` | Bounds Spring's reflection-driven class growth so it can't silently consume the reserve. |
| `-XX:MaxDirectMemorySize=64m` | Bounds the Netty/gRPC off-heap pool (defaults to "up to heap" — can eat our savings under streaming load). |

Without these caps we have seen the `dispute_process_request_proof_from_vendor` worker
OOM-killed under the realistic scenario (`bankCustomerComplaintDisputeHandling.bpmn` —
a `sendMessage`-true task inside a 50-way multi-instance loop). The plain HOCON worker
coexists on the same 256 Mi budget because it runs no servlet container and no Spring
framework.

Note: `spring-boot-starter-webflux` (Netty) is used instead of
`spring-boot-starter-web` (Tomcat) for the same reason — Netty's footprint is tens of
MiB smaller and the app has no servlet-specific code (no `@Controller`,
`HttpServletRequest`, etc.). Only `/actuator/health` and `/actuator/prometheus` are
exposed; both work identically on WebFlux.

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
| `CAMUNDA_LOG_LEVEL` | Placeholder named by the old `log4j2.xml`; nothing in the Helm chart actually sets it |

**`LOG_LEVEL` is NOT dead** — `application.yaml` bridges it via `${LOG_LEVEL:INFO}` on
`logging.level.root`, so the Helm-provided value continues to control framework log
volume. `LOGGING_LEVEL_IO_CAMUNDA_ZEEBE` is read by Spring Boot natively.

These dead entries can be cleaned up in a future Helm chart update but cause no issues.

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

### Quirk 2: Execution threads must be profile-scoped

**Problem A (worker pod):** the Spring Boot starter's default for `camunda.client.execution-threads`
is 1, which is far too low for a load tester whose handler blocks with `Thread.sleep(completionDelay)`
(~3.3 jobs/sec max with 300 ms delay).

**Problem B (starter pod):** on `main`, `Starter.createCamundaClient()` explicitly set
`numJobWorkerExecutionThreads(0)` so the starter wouldn't hold a job-worker thread pool
it never uses. Applying the same global value to both roles in Spring Boot would
regress this — the starter pod would sit idle with 10 allocated threads.

**Fix:** `application.yaml` uses profile-scoped config:

```yaml
# Base — starter pod
camunda:
  client:
    execution-threads: 0

---
spring.config.activate.on-profile: worker
camunda:
  client:
    execution-threads: ${LOAD_TESTER_WORKER_THREADS:10}
```

**Why `0` is safe on JDK 21:** empirically verified — `Executors.newScheduledThreadPool(0)`
does execute scheduled tasks. The `ScheduledThreadPoolExecutor` spins up one on-demand
thread via the standard `ThreadPoolExecutor` worker-creation path. Scheduled work
serializes through that one thread (since `ScheduledThreadPoolExecutor` ignores
`maxPoolSize`), but that's plenty for the starter's occasional async-command retries.
The "corePoolSize=0 hangs forever" folklore refers to a pre-JDK-8022027 bug (Java 7u39
and earlier); no longer applies.

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
| `spring-boot-starter-webflux` | Lightweight Netty HTTP server for Actuator endpoints |
| `camunda-process-test-java` (test) | `CamundaContainer` for integration tests |
| `spring-boot-starter-test` (test) | JUnit 5 + Spring test utilities |

---

## 12. Tests

### Before

No tests existed for the old HOCON-based load tester.

### After

The migration added a full test suite.

**Unit tests (20 tests, all via `./mvnw test`):**
- `ConfigTest` — verifies `@ConfigurationProperties` binding of defaults, plus a nested
  `ExtraBpmnModelsFromPropertiesTest` that exercises the indexed-list Spring property
  override (a separate `@Nested` class because it needs a different Spring context
  with different properties — the bound properties are fixed at context-creation time).
- `ConnectionMonitorTest` — unit tests for the shared `ConnectionMonitor` component:
  happy-path topology success, retry-on-transient-failure path, and the invariant that
  `app.connected` is registered at 0 before `awaitAndPrintTopology()` runs.
- `ProcessInstanceStartMeterTest` — data availability metric recording.
- `DataReadMeterTest` / `DataReadMeterQueryProviderTest` — read benchmark logic.

**Integration tests (2 tests, using Testcontainers + `CamundaContainer`):**
- `StarterWorkerIT` — activates both `starter` and `worker` profiles in the same Spring
  context. Starter deploys the BPMN and creates a handful of instances; Worker
  subscribes via `@JobWorker` and completes them. The test polls the broker's REST
  API (via `client.newProcessInstanceSearchRequest()`) with Awaitility until at least
  one instance reaches `COMPLETED`. This replaces the earlier separate `StarterIT`
  + `WorkerIT` pair, which had a weaker metric-count assertion and a time-coupled
  starter lifecycle.
- `DataAvailabilityIT` — measures data availability latency end-to-end.

Tests use two Spring profiles: `it` (enables the Camunda client with auth disabled and
forces gRPC to sidestep a REST compatibility gap in the `CamundaContainer` snapshot
image) and the role profile (`starter` / `worker`).

### Why the unit tests don't need `camunda.client.enabled=false` on every `@SpringBootTest`

Two independent mechanisms disable the auto-configured `CamundaClient` for unit tests,
either of which is sufficient:

1. `src/test/resources/application.yaml` sets `camunda.client.enabled: false` (loaded
   automatically on the test classpath). This is the safety net.
2. `@SpringBootTest(classes = ConfigTest.TestConfig.class)` — because `TestConfig` is a
   plain `@Configuration` (not `@SpringBootApplication`/`@SpringBootConfiguration`),
   Spring Boot skips auto-configuration entirely. The `camunda-spring-boot-starter`'s
   auto-config classes are never imported, so there's nothing to disable.

The flag is kept in the test YAML as defensive layering (in case someone later changes
`classes = ...` to a `@SpringBootApplication`), but is deliberately *not* repeated on
every `@SpringBootTest` annotation — that would be redundant noise.

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
      ConnectionMonitor.java            # Shared topology-retry + app.connected gauge
      StarterLatencyMetricsDoc.java     # Starter metric names (response, availability, read)
      ProcessInstanceStartMeter.java    # Data availability tracking
    read/
      DataReadMeter.java                # Periodic read benchmark executor
      DataReadMeterQueryProvider.java   # Default read query definitions
    util/
      PayloadReader.java                # JSON payload file reader
  src/main/resources/
    application.yaml                    # Spring Boot config (replaces application.conf)
                                        # Includes a worker-profile override for
                                        # camunda.client.execution-threads
    bpmn/                               # BPMN processes and payloads (unchanged)
  src/test/
    java/io/camunda/zeebe/
      config/ConfigTest.java            # Config binding unit tests (+ @Nested override test)
      metrics/ConnectionMonitorTest.java            # Unit tests for shared ConnectionMonitor
      metrics/ProcessInstanceStartMeterTest.java    # Data availability meter
      read/DataReadMeterTest.java                   # Read benchmark meter
      read/DataReadMeterQueryProviderTest.java      # Read-benchmark query defaults
      it/StarterWorkerIT.java           # End-to-end IT: starter + worker together,
                                        # asserts via REST ProcessInstance search
      it/DataAvailabilityIT.java        # Data availability IT
      it/CamundaContainerProvider.java  # Shared Testcontainers utility
    resources/
      application.yaml                  # Test config (disables CamundaClient safety net)
      application-it.yaml              # IT config (enables client, uses gRPC)
```
