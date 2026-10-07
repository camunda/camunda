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

Both roles serve `/health/readiness` and `/health/liveness` on port 9600 (the management base path
is `/`, so there is no `/actuator` prefix).

- **Readiness (starter and worker):** ready once the cluster topology was retrieved. The
  `ConnectionMonitor` retries every error, including authentication errors, so a pod that cannot
  connect stays not ready.
- **Liveness (starter):** DOWN when no start request succeeded for
  `load-tester.starter.liveness-max-no-success-age` (default 2 minutes, environment variable
  `LOAD_TESTER_STARTER_LIVENESS_MAX_NO_SUCCESS_AGE`). The clock starts once the starter is
  connected. Keep the age above the interval between two requests. Backpressure answers (gRPC
  `RESOURCE_EXHAUSTED`, HTTP 429) count as success, because the cluster responds. Every other
  failure does not.
- **Liveness (worker):** not implemented. A worker that receives no jobs cannot be told apart from a
  worker without load, and the worker exposes no job stream state. A silently dead job stream needs
  a fix in the client.

Why a time window over all failures except backpressure:

- A starter that creates no process instances is useless whatever the cause (rejected credentials,
  an OAuth token failure, a stopped scheduler, an unreachable cluster). Classifying causes misses
  some, for example a rejected OAuth token surfaces as a plain `ClientException`.
- Backpressure is the cluster working as intended under load, and a restart would not change it.
- A count of consecutive failures would trip within milliseconds at high rates, for example during a
  partition leader change. A window does not depend on the rate.
- The cost is that a cluster outage longer than the window restarts the starter. This is harmless.

Startup is covered by a startup probe on `/health/readiness` instead of liveness, because the
starter has not sent a request yet and the readiness indicator already tells whether it is
connected.

### Reference probe configuration

```yaml
startupProbe:
  httpGet:
    path: /health/readiness
    port: http
  periodSeconds: 10
  failureThreshold: 30
readinessProbe:
  httpGet:
    path: /health/readiness
    port: http
  initialDelaySeconds: 10
  periodSeconds: 10
  failureThreshold: 3
  timeoutSeconds: 5
# starter only
livenessProbe:
  httpGet:
    path: /health/liveness
    port: http
  initialDelaySeconds: 10
  periodSeconds: 10
  failureThreshold: 3
  timeoutSeconds: 5
```

