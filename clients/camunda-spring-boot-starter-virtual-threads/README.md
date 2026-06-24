# Camunda Spring Boot Starter Virtual Threads

This module provides a Spring Boot auto-configuration extension for the Camunda client that enables virtual threads for job execution.

It extends the standard `camunda-spring-boot-starter` and must be used in addition to it; it cannot be used as a standalone replacement.

## Overview

Virtual threads (introduced in Java 21) are lightweight threads that allow for highly concurrent job processing without the overhead of traditional platform threads. This module configures the Camunda client to use:

- **Virtual threads** for job execution (unbounded, created per task)
- **Single platform thread** for scheduling operations

## Requirements

- Java 21 or higher
- Spring Boot 3.x
- `camunda-spring-boot-starter`

## Usage

Add the dependency to your `pom.xml`:

```xml
<dependency>
  <groupId>io.camunda</groupId>
  <artifactId>camunda-spring-boot-starter-virtual-threads</artifactId>
  <version>${camunda.version}</version>
</dependency>
```

The auto-configuration will automatically override the default executor service with one that uses virtual threads. No additional configuration is required.

## How It Works

1. The `VirtualThreadsAutoConfiguration` runs **before** the standard `CamundaAutoConfiguration`
2. It creates a `CamundaClientExecutorService` bean with:
   - A virtual thread executor for job handling (named `job-worker-virtual-N`)
   - A scheduled executor with 1 platform thread for scheduling
3. Both executors are marked as "owned by Camunda client" and will be properly shut down when the client closes
4. The `@ConditionalOnMissingBean` annotation in `CamundaAutoConfiguration` ensures the default executor is not created

## Benefits

- **Scalability**: Handle thousands of concurrent jobs without creating thousands of platform threads
- **Resource efficiency**: Virtual threads have minimal memory overhead compared to platform threads
- **Drop-in replacement**: Simply add the dependency to enable virtual threads
- **Optional**: Other modules can continue using Java 8/17 without virtual thread support

## Thread Naming

Virtual threads created for job execution follow the naming pattern: `job-worker-virtual-N` where N is an incrementing counter.

## Example

```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

That's it! Virtual threads are now enabled for your Camunda client job workers.

