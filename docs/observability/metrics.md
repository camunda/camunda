# Metrics

[//]: # (As an initial scope, we will not include SLOs)

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

1. Get access to the meter registry

[//]: # (ask Stephan to explain the issue with setting up meter registry)

2. Pick a metric type

- If you need to observe a value change over time, use a `Counter`

  > [!Note]
  > Counters can only increment (they cannot be decremented). They reset when the application restarts, but time-derivative functions like Prometheus's rate() or increase() can accommodate this reset when used with time series data.

- If you need to observe the current value at a specific moment in time, use a `Gauge`. Gauges can go up or down and are useful for things like memory usage, thread counts, or queue sizes
- If you need to observe the distribution of values over time (e.g., request durations), use a `Histogram`. Histograms bucket observations and allow you to calculate percentiles and frequencies over a time window using Prometheus queries (like `histogram_quantile()`)
- If you need to observe a distribution of values with pre-calculated quantiles (but not necessarily over time), use a `Summary`. Summaries record quantiles directly, but they [cannot be aggregated across instances](https://prometheus.io/docs/tutorials/understanding_metric_types/?utm_source=chatgpt.com#summary) in Prometheus.

3. Name your metric

- We namespace metrics, all custom metrics should start with `camunda`. We try to group metrics together logically, for example `rest` for REST API metrics.

4. Add tags to the metric

- Depending on your use case (how do you want to group or differentiate the metrics) you may define certain tags for example `partitions` for Zeebe metrics, `outcome` for gRPC commands...

5. Define the metric in the code

- Use the meter registry and the appropriate Micrometer builder.

6. Test your metric

- As a rule of thumb, we write functional tests for new metrics to ensure they behave as expected, especially if they influence alerting or dashboards.

7. Document your metric

- Make sure to document the metric, so that it is clear what it does and how it should be used for example [TopologyMetricsDoc](https://github.com/camunda/camunda/blob/55f7d512c5a1a5251b6e0e28c6a95acacf6fdeaf/zeebe/dynamic-config/src/main/java/io/camunda/zeebe/dynamic/config/metrics/TopologyMetricsDoc.java)

## Best Practices

[//]: # (For each of the items below, provide examples)

#### Maintain Backward Compatibility

- **Avoid renaming or removing existing metric names or tags** unless absolutely necessary. Changes can break existing dashboards, alerts, and queries.
- **Micrometer appends the unit to the metric name** (e.g., `duration.seconds`). If you change the unit or forget to define it explicitly, it may lead to unintentional metric name changes. Always review unit definitions when updating metrics.

#### Be Careful with Tags

- Tags must be **bounded and finite** to prevent high cardinality issues in Prometheus.
  - Good: `status=success|failure`, `partition=1|2|3`
  - Bad: `userId`, `uuid`, `timestamp`, `errorMessage`

> High cardinality can lead to memory issues, slow dashboards, and instability in observability systems.

#### Avoid Static Global Registries

- **Do not use static global registries** (e.g., `Metrics.globalRegistry` in Micrometer) for registering metrics. Instead, prefer **dependency injection** of a `MeterRegistry` to maintain testability and modularity.

> Prefer injecting `MeterRegistry` into components that need to emit metrics.

#### Test Behavior, Not Implementation

- When writing tests for metrics, **focus on the observable behavior** (e.g., "metric is emitted after an event") rather than internal implementation details.
- Use tools like `SimpleMeterRegistry` or Prometheus test exporters to assert metric values in functional or integration tests.

[//]: # (Link to best practices)
[//]: # (Link to types of metrics, for gauge we should be careful with how they're instantiated)
[//]: # (Convention)
[//]: # (Namespacing, labelling, best practices, testing with examples, cardinality, memory usage, etc. The less tags you have the better, we should be wary about adding too many tags)
[//]: # (then Use Zeebe metrics to show examples of one type of metric, why do we use a counter here)
[//]: # (Dos and Donts)
[//]: # (Limitations of micrometer, hierarchy)
[//]: # (Tools we use maybe add custom alerting reference)
[//]: # (Use visualization to show the different types of metrics, and how they are used)

## Configuration

You can refer to the [user-facing docs](https://docs.camunda.io/docs/self-managed/operational-guides/monitoring/metrics/#configuration)

## Contribute

[//]: # (Local setup, how to run the metrics server, how to add new metrics, etc.)
[//]: # (PRs, )
[//]: # (documentation : micrometer with enum names, labels with auto-generation plugins &#40;we don't use this yet&#41;.)
[//]: # (We currently use docusarus which means injecting something in the pipeline. As a first iteration we can add that plugin)
[//]: # (Out of scope : libraries metrics like Spring boot...)
[//]: # ()
[//]: # (Grafana Ctl, JSON, collaboration on Grafana dashboards, etc.)

