# Camunda Load Tester

This project contains code for the starter and worker, which are used during our benchmarks.
It is a Spring Boot application using the `camunda-spring-boot-starter`.

## Running locally

The application uses Spring profiles to select the role:

```bash
# Run as starter (creates process instances)
java -jar target/camunda-load-tester-*.jar --spring.profiles.active=starter

# Run as worker (completes jobs)
java -jar target/camunda-load-tester-*.jar --spring.profiles.active=worker
```

Configuration is in `src/main/resources/application.yaml`. Override any property via environment
variables (e.g. `CAMUNDA_CLIENT_GRPC_ADDRESS`, `LOAD_TESTER_STARTER_RATE`) or Spring Boot
`--property=value` arguments.

## Camunda clients

The starter uses two clients:

1. The default client (`loaderClient`) only generates load on the cluster.
2. Every read query (data availability checks and read benchmarks) must use the
   `queryCamundaClient` client (`queryClient`), so that queries do not share
   connections or threads with the load.

## Build docker images for benchmark application

To build the docker images for the load test application, run the following command:

```bash
./mvnw -am -pl load-tests/load-tester package -DskipTests -DskipChecks
./mvnw -pl load-tests/load-tester jib:build -Pstarter
./mvnw -pl load-tests/load-tester jib:build -Pworker
```

## Health probes

Both roles serve `/health/readiness` and `/health/liveness` on port 9600 (no `/actuator` prefix).
The readiness group contains `readinessState` and `connectionReady`. Neither probe group contains
the `camundaClient` indicator of the aggregate `/health` endpoint, which sends a status request to
the cluster.

### Starter

- **Readiness:** ready once the cluster topology was retrieved and the benchmark process was
  deployed. The web server, and with it both health endpoints, starts only after that. After that,
  a cluster outage keeps it `UP`.
- **Liveness:** DOWN when no start request succeeded for
  `load-tester.starter.liveness-max-no-success-age` (default 2 minutes). The clock starts once
  connected, and the age has to exceed the interval between two requests. Backpressure answers
  (gRPC `RESOURCE_EXHAUSTED`, HTTP 429) count as success, every other failure does not. This covers
  a cluster outage longer than the window and a stopped request scheduler. The liveness group
  contains `starterLiveness`.
- **Not covered:** a client that cannot reach the cluster after the initial connection, other than
  through `starterLiveness`.

The `camundaClient` indicator uses the client that sends the load. At 300 starts per second it waits
behind the pending requests and can take more than 10 seconds, so `/health` can be slow.

### Worker

- **Readiness:** ready once the cluster topology was retrieved. The topology request retries every
  error, so a pod that cannot authenticate stays not ready. After that, a cluster outage keeps it
  `UP`.
- **Liveness:** none. A worker without jobs cannot be told apart from one without load.
- **Not covered:** a client that cannot reach the cluster after the initial connection, and a
  worker whose job stream stopped.

### Probe configuration

The probes can be configured as below:

```yaml
startupProbe:
  httpGet: { path: /health/readiness, port: http }
  periodSeconds: 10
  failureThreshold: 30
readinessProbe:
  httpGet: { path: /health/readiness, port: http }
  periodSeconds: 10
# starter only
livenessProbe:
  httpGet: { path: /health/liveness, port: http }
  periodSeconds: 10
  failureThreshold: 3
```

## Shutdown

On SIGTERM the starter stops creating process instances and waits for the start requests that are
still pending before it exits. Spring limits the wait with
`spring.lifecycle.timeout-per-shutdown-phase` (30 seconds by default), so
`terminationGracePeriodSeconds` has to be at least that long.
