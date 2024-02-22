# Project

This project contains code for the starter and worker, which are used during our benchmarks.

## Build docker images for benchmark application

To build the docker images for the benchmark application, run the following command:

```bash
./mvnw -am -pl benchmarks/project package -DskipTests -DskipChecks
./mvnw -pl benchmarks/project jib:build -Pstarter
./mvnw -pl benchmarks/project jib:build -Pworker
```

