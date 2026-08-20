# Camunda-Process-Test-Spring

Camunda's testing library for processes and process applications with Spring. It builds on
[camunda-process-test-java](../camunda-process-test-java) and integrates via
`CamundaProcessTestExecutionListener` (annotation `@CamundaSpringProcessTest`).

This is the canonical Spring artifact, built for **Spring Boot 4 / Spring 6**.

## Install

Add the following dependency to your Maven project:

```xml
<dependency>
  <groupId>io.camunda</groupId>
  <artifactId>camunda-process-test-spring</artifactId>
  <scope>test</scope>
</dependency>
```

> [!NOTE]
> On **Spring Boot 3.5.x**, use `camunda-process-test-spring-boot-3` instead — it repackages this
> library against Spring Boot 3. The artifact `camunda-process-test-spring-boot-4` is a relocation
> stub kept only for users migrating from 8.7/8.8; it now points to `camunda-process-test-spring`.

## Usage

Read more about the usage of the library and how to get started in our documentation: https://docs.camunda.io/docs/apis-tools/testing/getting-started/.

## Contributing

See the [testing contribution guide](../CONTRIBUTING.md).
