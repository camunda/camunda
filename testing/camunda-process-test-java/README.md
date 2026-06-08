# Camunda-Process-Test-Java

Camunda's new testing library for processes and process applications with Java.

## Install

Add the following dependency to your Maven project:

```xml
<dependency>
  <groupId>io.camunda</groupId>
  <artifactId>camunda-process-test-java</artifactId>
  <scope>test</scope>
</dependency>
```

## Usage

Read more about the usage of the library and how to get started in our documentation: https://docs.camunda.io/docs/apis-tools/testing/getting-started/.

## Camunda client environment variables

By default, Camunda Process Test disables Camunda client environment variable overrides
(`camunda.client.applyEnvironmentVariableOverrides=false`) so the injected client connects to the
test runtime.

If you want to opt in to environment variable overrides, set:

```properties
camunda.client.applyEnvironmentVariableOverrides=true
```
