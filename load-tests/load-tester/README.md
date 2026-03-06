# Project

This project contains code for the starter and worker, which are used during our benchmarks.
It is a Spring Boot application using the `camunda-spring-boot-starter`.

## Running locally

The application uses Spring profiles to select the role:

```bash
# Run as starter (creates process instances)
./mvnw -pl load-tests/load-tester spring-boot:run -Dspring-boot.run.profiles=starter

# Run as worker (completes jobs)
./mvnw -pl load-tests/load-tester spring-boot:run -Dspring-boot.run.profiles=worker
```

## Configuration

Configuration is managed via `application.yaml` and can be overridden using:
- Spring profiles (`application-{profile}.yaml`)
- Environment variables (e.g., `LOAD_TESTER_STARTER_RATE=500`)
- System properties (e.g., `-Dload-tester.starter.rate=500`)

Camunda client settings (connection, auth) use the `camunda.client.*` namespace
provided by the Camunda Spring Boot Starter.

## Build docker images for benchmark application

To build the docker images for the load test application, run the following command:

```bash
./mvnw -am -pl load-tests/load-tester package -DskipTests -DskipChecks
./mvnw -pl load-tests/load-tester jib:build -Pstarter
./mvnw -pl load-tests/load-tester jib:build -Pworker
```
