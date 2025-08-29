# Project

This project contains code for the starter and worker, which are used during our load tests.

## Build docker images for load test application

To build the docker images for the load test application, run the following command:

```bash
./mvnw -am -pl zeebe/load-tests/project package -DskipTests -DskipChecks
./mvnw -pl zeebe/load-tests/project jib:build -Pstarter
./mvnw -pl zeebe/load-tests/project jib:build -Pworker
```

