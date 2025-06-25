# Metrics

[//]: # (As an initial scope, we will not include SLOs)

## Overview

Metrics are aggregated measurements that reflect the performance and behavior of an application. For example:

- Average duration of database queries.

- Count of HTTP 401 errors over the past 5 minutes.

These metrics help monitor application health and identify performance bottlenecks.

### Micrometer in Camunda 8

In Camunda 8, we use [Micrometer](https://micrometer.io/) as a facade for metrics collection (similar to how SLF4J works for logging). Micrometer provides:

- Integration with observability systems like Prometheus, InfluxDB, Graphite, and others.
- Out-of-the-box support for popular libraries and frameworks such as Spring Boot, Netty, and gRPC.

### Visualization

The visualization of metrics is typically done using [Grafana](https://grafana.com/), which can be connected to any of the supported observability systems.

By default, we use [Prometheus](https://prometheus.io/) to collect, store, and alert on metrics.

## Usage

Metrics are stored in Meter Registries, which are automatically created by Spring Boot, one for each configured observability system.

In the dist module, we typically use Autowiring to inject the registry where it's needed. In other cases, we pass it explicitly via constructor injection.

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

- If you need to observe a value change over time, then use a `Counter`
- If you need to observe the current value in time, then use a `Gauge`
- If you need to observe frequencies or latencies distribution, use a `Histogram`
- If you need to observe a distribution that is not time-based, use a `Summary`

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

[//]: # (Link to best practices)
[//]: # (Link to types of metrics, for gauge we should be careful with how they're instantiated)
[//]: # (Convention)
[//]: # (To get a meter registry setup, you)
[//]: # (Namespacing, labelling, best practices, testing with examples, cardinality, memory usage, etc. The less tags you have the better, we should be wary about adding too many tags, make sure tags are bounded / finite)
[//]: # (then Use Zeebe metrics to show examples of one type of metric, why do we use a counter here)
[//]: # (We avoid injecting metrics to the global static registry constant otherwise we can't test the metrics.)
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

