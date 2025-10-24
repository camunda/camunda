# Metrics

## Overview

Metrics are quantitative measurements that reflect the performance and behavior of an application. In observability systems like Prometheus and OpenTelemetry, a metric can refer to a single measured value (such as a counter or gauge) or to a more complex aggregation (such as a histogram or summary, which aggregate multiple observations over time).
For example:
- Average duration of database queries (aggregated via a histogram or summary)
- Count of HTTP 401 errors over the past 5 minutes (counter, often aggregated in queries)

These metrics help monitor application health and identify performance bottlenecks.

> [!Note]
> The term "metric" can be overloaded and may refer to different concepts depending on the context. In Prometheus, counters, gauges, histograms, and summaries are all considered metric types. Histograms and summaries are more complex, representing collections of time series derived from multiple measurements. For clarity, we use "metric" here to refer to both raw measured values and their aggregated forms.

### Micrometer in Camunda 8

In Camunda 8, we use [Micrometer](https://micrometer.io/) as a facade for metrics collection (similar to how SLF4J works for logging). Micrometer provides:

- Integration with observability systems like Prometheus, InfluxDB, Graphite, and others.
- Out-of-the-box support for popular libraries and frameworks such as Spring Boot, Netty, and gRPC.

### Visualization

The visualization of metrics is done using [Grafana](https://grafana.com/), which can be connected to any of the supported observability systems.

By default, we use [Prometheus](https://prometheus.io/) to collect, store, and alert on metrics.

## Usage

In Micrometer, a [MeterRegistry](https://docs.micrometer.io/micrometer/reference/concepts/registry.html) is the core component that tracks and manages all metrics (meters) in your application. Meters like counters, gauges, and timers are registered to a MeterRegistry, which then handles exporting these metrics to the configured monitoring systems.

In Spring Boot, MeterRegistry instances are [auto-configured](https://docs.spring.io/spring-boot/docs/2.1.x/reference/html/production-ready-metrics.html#production-ready-metrics-getting-started) based on which observability system dependencies are present (e.g., Prometheus, InfluxDB).

In the dist module, we typically use Autowiring to inject the registry where it's needed. In other cases, we pass it explicitly via constructor injection.

This approach helps avoid spreading Spring dependencies throughout the codebase and improves testability by allowing easier mocking or substitution of the registry in tests.

Example:

```java
final MeterRegistry meterRegistry = ...; // injected
final  var counter = Counter.builder("my.cool.counter")
  .description("A super cool counter")
  .register(meterRegistry);
counter.increment(); // increments the counter by 1
```

## Adding a metric

### 1. Get access to the meter registry

[//]: # (ask Stephan to explain the issue with setting up meter registry)

### 2.  Pick a metric type

#### Counter

If you need to observe a value change over time, use a `Counter`

> [!Note]
> Counters can only increment (they cannot be decremented). They reset when the application restarts, but time-derivative functions like Prometheus's rate() or increase() can accommodate this reset when used with time series data.

Example usage:

```java
  private Counter registerBatchProcessingRetries() {
  final Counter batchProcessingRetries;
  final var retriesDoc = StreamMetricsDoc.BATCH_PROCESSING_RETRIES;
  batchProcessingRetries =
    Counter.builder(retriesDoc.getName())
      .description(retriesDoc.getDescription())
      .register(registry);
  return batchProcessingRetries;
}
```

#### Gauge

If you need to observe the current value at a specific moment in time, use a `Gauge`. Gauges can go up or down and are useful for things like memory usage, thread counts, or queue sizes.

> [!Important]
> Gauges are very special as they only provide the current snapshot of the metric (you lose information between snapshots). Prometheus and other monitoring systems poll data from the nodes/servers/apps at defined intervals (typically every 15-30 seconds). This means when looking at this metric in Grafana, we should keep in mind that we don't know the gauge value between polls. For example, we don't know if it went up 500 and down 400, or just went up 100 - we only see the final state at each polling interval.

Example usage:

```java
Gauge.builder(meterName("bulk.memory.size"), bulkMemorySize, AtomicInteger::get)
    .description("Exporter bulk memory size")
    .register(meterRegistry);
```

#### Histogram

If you need to observe the distribution of values over time (e.g., request durations), use a `Histogram`. Histograms bucket observations and allow you to calculate percentiles and frequencies over a time window using Prometheus queries (like `histogram_quantile()`)

Example usage:

```java
private Timer registerProcessingDurationTimer(final ValueType valueType, final Intent intent) {
  final var meterDoc = StreamMetricsDoc.PROCESSING_DURATION;
  return Timer.builder(meterDoc.getName())
      .description(meterDoc.getDescription())
      .serviceLevelObjectives(meterDoc.getTimerSLOs())
      .tag(ProcessingDurationKeys.VALUE_TYPE.asString(), valueType.name())
      .tag(ProcessingDurationKeys.INTENT.asString(), intent.name())
      .register(registry);
}
```

#### Summary

If you need to observe a distribution of values with pre-calculated quantiles (but not necessarily over time), use a `Summary`. Summaries record quantiles directly, but they [cannot be aggregated across instances](https://prometheus.io/docs/tutorials/understanding_metric_types/?utm_source=chatgpt.com#summary) in Prometheus.

Example usage:

```java
  private DistributionSummary registerBatchProcessingCommands() {
    final DistributionSummary batchProcessingCommands;
    final var commandsDoc = StreamMetricsDoc.BATCH_PROCESSING_COMMANDS;
    batchProcessingCommands =
      DistributionSummary.builder(commandsDoc.getName())
        .description(commandsDoc.getDescription())
        .serviceLevelObjectives(commandsDoc.getDistributionSLOs())
        .register(registry);
    return batchProcessingCommands;
}
```

### 3. Name your metric

We namespace metrics, all custom metrics should start with `camunda`. We try to group metrics together logically, for example `rest` for REST API metrics.

### 4. Add tags to the metric

Depending on your use case (how do you want to group or differentiate the metrics) you may define certain tags for example `partitions` for Zeebe metrics, `outcome` for gRPC commands...

### 5. Define the metric in the code

Use the meter registry and the appropriate Micrometer builder.

### 6. Test your metric

As a rule of thumb, we write functional tests for new metrics to ensure they behave as expected, especially if they influence alerting or dashboards.

Use `SimpleMeterRegistry` for testing:

```java
@Test
void shouldIncrementCounterOnEvent() {
  // Given
  final var meterRegistry = new SimpleMeterRegistry();
  final var component = new MyComponent(meterRegistry);

  // When
  component.processEvent();

  // Then
  final var counter = meterRegistry.get("camunda.mycomponent.events.processed")
      .counter();
  assertThat(counter.count()).isEqualTo(1);
}
```

### 7. Document your metric

Make sure to document the metric, so that it is clear what it does and how it should be used for example [TopologyMetricsDoc](https://github.com/camunda/camunda/blob/55f7d512c5a1a5251b6e0e28c6a95acacf6fdeaf/zeebe/dynamic-config/src/main/java/io/camunda/zeebe/dynamic/config/metrics/TopologyMetricsDoc.java)

## Best Practices

[//]: # (For each of the items below, provide examples)

#### Maintain Backward Compatibility

- **Avoid renaming or removing existing metric names or tags** unless absolutely necessary. Changes can break existing dashboards, alerts, and queries.
- **Micrometer appends the unit to the metric name** (e.g., `duration.seconds`). If you change the unit or forget to define it explicitly, it may lead to unintentional metric name changes. Always review unit definitions when updating metrics.

#### Be Careful with Tags

- Tags must be **bounded and finite** to prevent high cardinality issues in Prometheus.
  - Good: `status=success|failure`, `partition=1|2|3`
  - Bad: `userId`, `uuid`, `timestamp`, `errorMessage`
- User inputs are almost always unbounded and should not be used as tags
- As a rule of thumb, if we can represent tags as an enum, it indicates that the tag is bounded and suitable for use in metrics.
- Some exceptions to unbounded tags are acceptable, such as `jobType`. In that case, we need to make sure that the metric can be disabled.
- We have a utility called `BoundedMeterCache` built on top of a LFU cache that can clear less frequently used tags to prevent high cardinality issues.

> High cardinality can lead to memory issues, slow dashboards, and instability in observability systems.

#### Avoid Static Global Registries

- **Do not use static global registries** (e.g., `Metrics.globalRegistry` in Micrometer) for registering metrics. Instead, prefer **dependency injection** of a `MeterRegistry` to maintain testability and modularity.
- Sometimes, it can be useful to create context, with an outer/inner registry as long as we have control of the lifecycle. For example, we do this for a partition so we can keep outer and inner registries in sync. We use this in `MetricsStep` for example where we close the meter registry when a leader partition transition happens.

> Prefer injecting `MeterRegistry` into components that need to emit metrics.

#### Test Behavior, Not Implementation

- When writing tests for metrics, **focus on the observable behavior** (e.g., "metric is emitted after an event") rather than internal implementation details.
- Use tools like `SimpleMeterRegistry` to assert metric values in functional or integration tests.

See `HealthTreeMetricsTest` for an example of how to test metrics in a functional test.

#### Be aware of Gauges instantiation

- Use gauges carefully, especially when tracking values that may be reset or removed.
- Improperly managed gauges can expose stale or misleading data in Prometheus.
- If we create multiple instances of a same gauge, only the first one will be registered, subsequent registrations (recreate metric class) will be ignored.
- This can lead to unexpected behavior if not managed properly. If we want to use gauge in multiple instances, we need to make sure the name is exactly the same (see `StatefulGauge` in the Extension section).

#### Don’t Over-Instrument

- Only emit metrics that are actionable or provide real business/operational value.
- There should be a concrete use case for each metric or tags.

#### Avoid Dynamic Metric Names

- Never generate metric names dynamically at runtime (e.g., response_time_cluster1234).
- Keep metric names static and predictable; use tags for variability instead (within cardinality constraints).

#### Avoid One-Off Metrics

- As a rule of thumb, refrain from emitting one-time metrics (e.g., timestamp of a deployment, build ID, etc.) that never update again. Some exceptions are acceptable such as startup metrics.
- Use logs for one-time or static values.

#### Be Careful with .publishPercentileHistogram()

- By default, the Micrometer generator yields 276 buckets, which comes with a cost
- We make use of `MicrometerUtil.defaultPrometheusBuckets()` which generally covers all the buckets we need

## Configuration

You can refer to the [user-facing docs](https://docs.camunda.io/docs/self-managed/operational-guides/monitoring/metrics/#configuration)

## Extensions / Limitations

While Micrometer provides a robust foundation for metrics collection, there are some limitations and extensions to be aware of when working with Camunda 8:

### Micrometer Limitations

#### Limited Extensibility

- **Micrometer is not easily extensible** for custom metric types beyond the standard counters, gauges, timers, and distribution summaries.
- Adding new metric types requires implementing complex interfaces and may not be compatible across all monitoring systems.

#### Gauge Lifecycle Management

- **Gauges can be tricky to manage** when dealing with dynamic instances or cleanup scenarios.
- Once registered, gauges remain in the registry until explicitly removed, which can lead to memory leaks in long-running applications.
- If you attempt to register multiple instances of the same gauge (same name and tags), only the first registration will be effective.

#### High Cardinality Risks

- **Tag cardinality must be carefully managed** to prevent performance degradation in monitoring systems.
- Prometheus can struggle with metrics that have thousands of unique tag combinations.
- There's no built-in protection against accidentally creating high-cardinality metrics.

### Camunda Extensions

#### StatefulGauge

To address some of the gauge lifecycle issues, we've implemented a `StatefulGauge` utility:

```java
// Example of StatefulGauge usage
private final StatefulGauge activeJobsGauge =
    StatefulGauge.builder("camunda.jobs.active")
        .description("Number of currently active jobs")
        .withRegistry(meterRegistry);

// Update the gauge value
activeJobsGauge.set(currentActiveJobs);
```

Benefits of StatefulGauge:
- **Proper lifecycle management**: Automatically handles registration and cleanup
- **Prevents duplicate registrations**: Manages the same gauge instance across multiple calls
- **Thread-safe updates**: Safe to use in concurrent environments

#### BoundedMeterCache

For scenarios where tag cardinality might become problematic, we provide `BoundedMeterCache`:

```java
// Create a meter provider with fixed tags
final var provider = Counter.builder(JOB_EVENTS.getName())
    .description(JOB_EVENTS.getDescription())
    .tag(EngineKeyNames.JOB_ACTION.asString(), jobAction.getLabel())
    .tag(EngineKeyNames.JOB_KIND.asString(), kind.name())
    .withRegistry(registry);

// Create bounded cache with a limit of 100 unique job types
private final BoundedMeterCache<Counter> jobEventCounters =
    BoundedMeterCache.of(registry, provider, EngineKeyNames.JOB_TYPE, 100);

// Use the cache - each job type gets its own counter instance
jobEventCounters.get("payment-processing").increment();
jobEventCounters.get("email-notification").increment();
```

This utility uses an LFU (Least Frequently Used) cache to automatically evict less-used metrics when the limit is reached.

## Contribute

This section provides guidance for developers who want to contribute to metrics in Camunda 8, including local development setup, testing, and collaboration practices.

### Local Development Setup

#### Running the Monitoring Stack Locally

The `monitor/` directory contains a complete local monitoring setup using Docker Compose:

```bash
# Navigate to the monitor directory
cd monitor/

# Start Prometheus, Grafana, and related services
docker-compose up -d

# View logs
docker-compose logs -f
```

This setup includes:
- **Prometheus**: Metrics collection and storage (http://localhost:9090)
- **Grafana**: Visualization and dashboards (http://localhost:3000)
- **Pre-configured dashboards**: Located in `grafana/dashboards/`

#### Running Camunda with Metrics

To enable metrics in your local Camunda instance:

```bash
# Build and run with metrics enabled
./mvnw package -Dquickly -T1C
java -jar dist/target/camunda-zeebe-*.jar \
  --management.endpoints.web.exposure.include=health,prometheus \
  --management.endpoint.prometheus.enabled=true
```

Access metrics at: http://localhost:9600/actuator/prometheus

#### Local Dashboard Development

To serve and test dashboards locally with [Grizzly](https://grafana.github.io/grizzly/):

```bash
make grizzly
```

This uses the grr CLI to serve dashboards from grafana/dashboards on http://localhost:8093
.
Make sure to set your Grafana credentials in a .env file:

```env
GRAFANA_URL=http://localhost:3000
GRAFANA_USER=admin
GRAFANA_TOKEN=your-token
```

### Future Improvements

#### Planned Enhancements

- **Auto-generated documentation**: Plugin to automatically generate metric documentation from code
- **Dashboard as code**: Improved tooling for managing Grafana dashboards in version control
- **Metric validation**: Build-time validation of metric names and cardinality
- **Performance monitoring**: Enhanced tooling for monitoring metric collection overhead

#### Contributing Ideas

- Improve metric testing utilities
- Enhance dashboard templating patterns
- Create metric migration tools
- Develop cardinality monitoring tools

## Internal Dashboards

For Camunda team members, we maintain internal Grafana instances for monitoring our production and development environments:

- **Internal SaaS Dashboard**: https://grafana-central.internal.camunda.io/
- **Internal Dev Dashboard**: https://grafana.dev.zeebe.io/

### Dashboard Synchronization

When editing dashboards in these internal environments, ensure you update the corresponding JSON files in `monitor/grafana/` for provisioning. This keeps our local development dashboards in sync with production.

For detailed instructions on dashboard management and provisioning, refer to the [monitor/README.md](../../monitor/README.md).
