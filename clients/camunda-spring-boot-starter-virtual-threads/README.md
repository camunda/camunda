# Camunda Spring Boot Starter Virtual Threads

This module provides a Spring Boot auto-configuration extension for the Camunda client that enables virtual threads for job execution.

It extends the standard `camunda-spring-boot-starter` and must be used in addition to it; it cannot be used as a standalone replacement.

## Overview

Virtual threads (introduced in Java 21) are lightweight threads that allow for highly concurrent job processing without the overhead of traditional platform threads. This module configures the Camunda client to use:

- **Virtual threads** for job execution (unbounded, created per task)
- **Single platform thread** for scheduling operations

## Requirements

- Java 21 or higher
- Spring Boot 4.x
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

## Configuration

Virtual-thread job handling is enabled by default when this module is on the classpath. You can switch it off with a property — useful when an organization wraps the Camunda starters in an internally maintained starter that always includes this dependency, while individual teams or environments decide with a property:

```properties
camunda.client.threads.virtual.enabled=false
```

| Property | Type | Default | Description |
| --- | --- | --- | --- |
| `camunda.client.threads.virtual.enabled` | `boolean` | `true` | Whether job handling uses Java 21 virtual threads. Set to `false` to fall back to the default platform-thread executor of `camunda-spring-boot-starter`, sized by `camunda.client.execution-threads`. |

The property is client-wide rather than per worker, because the executor is shared by all workers.

When several named clients are configured (`camunda.clients.<name>.*`), each client gets its own virtual-thread job executor instead of sharing one: the executor is owned by its client and closed with it, so one client's shutdown cannot stop job handling for the others. Per-client executors are not individually metered — executor metrics stay on the shared `CamundaClientExecutorService` bean, because Micrometer is an optional dependency and the multi-client factory path must work without it.

When virtual threads are disabled, the regular platform-thread executor applies again and `camunda.client.execution-threads` controls its size. When virtual threads are enabled, `camunda.client.execution-threads` has no effect; the per-worker `maxJobsActive` setting becomes the effective limit on job concurrency, because the virtual-thread executor is unbounded.

## How It Works

1. The `VirtualThreadsAutoConfiguration` runs **before** the standard `CamundaAutoConfiguration`
2. It is guarded by `@ConditionalOnProperty(name = "camunda.client.threads.virtual.enabled", havingValue = "true", matchIfMissing = true)`, so it is active by default and backs off when the property is `false`
3. It creates a `CamundaClientExecutorService` bean with:
   - A virtual thread executor for job handling (named `job-worker-virtual-N`)
   - A scheduled executor with 1 platform thread for scheduling
4. Both executors are marked as "owned by Camunda client" and will be properly shut down when the client closes
5. The `@ConditionalOnMissingBean` annotation in `CamundaAutoConfiguration` ensures the default executor is not created

## Benefits

- **Scalability**: Handle thousands of concurrent jobs without creating thousands of platform threads
- **Resource efficiency**: Virtual threads have minimal memory overhead compared to platform threads
- **Drop-in replacement**: Simply add the dependency to enable virtual threads
- **Switchable**: Turn virtual threads off with a property for tests, troubleshooting, or performance comparisons — no dependency changes required
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
