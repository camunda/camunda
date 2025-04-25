# Camunda-Process-Test-Service

Work in progress!

## Build docker image

```
mvn com.google.cloud.tools:jib-maven-plugin:3.4.4:dockerBuild -pl testing/camunda-process-test-service
```

## Run Docker image locally

```
docker run -it --rm -v /var/run/docker.sock:/var/run/docker.sock -p 8080:8080 ghcr.io/camunda/camunda-process-test-service
```

Refer to [Testcontainers configuration](https://java.testcontainers.org/supported_docker_environment/continuous_integration/dind_patterns/).
