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

## Build docker images for benchmark application

To build the docker images for the load test application, run the following command:

```bash
./mvnw -am -pl load-tests/load-tester package -DskipTests -DskipChecks
./mvnw -pl load-tests/load-tester jib:build -Pstarter
./mvnw -pl load-tests/load-tester jib:build -Pworker
```

## Health probes

Both roles serve `/health/readiness` and `/health/liveness` on port 9600 (no `/actuator` prefix).

- **Readiness (starter and worker):** ready once the cluster topology was retrieved. The topology
  request retries every error, so a pod that cannot authenticate stays not ready.
- **Liveness (starter):** DOWN when no start request succeeded for
  `load-tester.starter.liveness-max-no-success-age` (default 2 minutes). The clock starts once
  connected, and the age has to exceed the interval between two requests. Backpressure answers
  (gRPC `RESOURCE_EXHAUSTED`, HTTP 429) count as success, every other failure does not.
- **Liveness (worker):** none. A worker without jobs cannot be told apart from one without load.

A startup probe on `/health/readiness` restarts a pod that never connects. Reference configuration
for the Helm chart, which does not apply it yet:

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

