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

