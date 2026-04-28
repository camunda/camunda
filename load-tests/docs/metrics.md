# Load Test Metrics

This document defines all metrics used to observe and evaluate Camunda load tests. Each entry
includes the Prometheus query expression, unit, and any defined thresholds.

These metrics are queried against the [Prometheus instance](https://monitor.benchmark.camunda.cloud).
All metrics defined here are exposed and visualised in the
[Camunda Performance dashboard](https://dashboard.benchmark.camunda.cloud/d/camunda-benchmarks/camunda-performance?orgId=1).

## Where to start

**Check these to know if the test is healthy:**

1. **PI/s and FNI/s** — are both throughput SLOs met for the variant? These are the headline
   numbers. See the SLO targets table below.
2. **Data availability p99** — the strongest end-to-end signal. Covers the full round-trip
   (processing → exporting → ES indexing → REST). Threshold: p99 < 5 s.
3. **Request-response latency p99** — the primary metric for the `latency` variant (no throughput
   SLO). Threshold: p99 < 5 s.

**Check these to understand why it's not:**

4. **Backpressure (dropped requests)** — any value above ~0% means the system is actively
   rejecting work. Investigate immediately.
5. **Processing and exporting backlogs** — a stable backlog is acceptable; a *growing* one means
   the engine or exporter cannot keep up and latency will worsen over time.
6. **CPU throttling** — even brief throttling caps throughput and inflates latency. A value above
   ~0% often explains why PI/s is below target.
7. **JVM heap trend** — a monotonically growing heap is an early signal of a memory leak. Check
   this on weekly tests after several days.

## Prometheus query notes

The queries below use two Grafana template variables. Substitute them when querying Prometheus
directly:

|      Variable      |                                            Replacement                                            |
|--------------------|---------------------------------------------------------------------------------------------------|
| `$namespace`       | Exact namespace, e.g. `c8-my-test` (use `=` not `=~`)                                             |
| `$__rate_interval` | A duration ≥ 4× your scrape interval (Grafana computes this automatically from the scrape config) |

---

## SLO targets by test variant

|      Variant      | PI/s target | FNI/s target |                    Notes                     |
|-------------------|-------------|--------------|----------------------------------------------|
| `typical`         | 50          | 500          | Straight-through process, 10 tasks, 2 timers |
| `realistic`       | 50          | 100          | 1 PI/s created; multi-instance inflates FNI  |
| `max` / stress    | 300         | 300          | Minimal process, stress ceiling              |
| `latency`         | 1           | —            | No throughput SLO; focus on latency metrics  |
| `rdbms-realistic` | 50          | 100          | Same as realistic, PostgreSQL backend        |

Latency and health thresholds apply to all variants — see the sections below.

---

## Throughput

### Process instances per second (PI/s)

Measures the average process instances that can be completed per second (root and sub-process
instances).

- **Unit:** PI/s
- **Measurement:** rate

```promql
sum(rate(zeebe_element_instance_events_total{namespace=~"$namespace", action="completed", type="PROCESS"}[$__rate_interval]))
```

---

### Flow node instances per second (FNI/s)

Measures the average flow node instances (all element types except PROCESS) that can be completed
per second.

- **Unit:** FNI/s
- **Measurement:** rate

```promql
sum(rate(zeebe_element_instance_events_total{namespace=~"$namespace", type!="PROCESS", action="completed"}[$__rate_interval]))
```

---

### Service task instances per second (STI/s)

Measures the average number of service task instances that can be completed per second.

- **Unit:** STI/s
- **Measurement:** rate

```promql
sum(rate(zeebe_element_instance_events_total{namespace=~"$namespace", action="completed", type="SERVICE_TASK"}[$__rate_interval]))
```

---

### Jobs per second (J/s)

Measures the average number of jobs that can be completed per second.

- **Unit:** J/s
- **Measurement:** rate

```promql
sum(rate(zeebe_job_events_total{namespace=~"$namespace", action="completed"}[$__rate_interval])) by (namespace)
```

---

### Records processed per second

Measures the average number of records processed per second. This is the most atomic metric for
processing throughput.

- **Unit:** records/s
- **Measurement:** rate

```promql
sum(rate(zeebe_stream_processor_records_total{namespace=~"$namespace", action=~"skipped|processed"}[$__rate_interval])) by (pod, partition, processor)
```

---

### Records exported per second

Measures the average number of records exported per second. This is the most atomic metric for
export throughput.

- **Unit:** records/s
- **Measurement:** rate

```promql
sum(rate(zeebe_exporter_events_total{namespace=~"$namespace"}[$__rate_interval])) by (pod, partition, exporter)
```

---

### Archived process instances per second

Measures the average number of process instances archived per second.

- **Unit:** PI/s
- **Measurement:** rate

```promql
sum(rate(zeebe_camunda_exporter_archiver_process_instances_total{namespace=~"$namespace"}[$__rate_interval]))
```

---

### Elasticsearch indexed documents per second

Measures the average number of documents indexed per second in Elasticsearch. Note: records in
Camunda do not map 1:1 to ES documents — data is duplicated and some records get merged.

- **Unit:** documents/s
- **Measurement:** rate

```promql
avg(rate(elasticsearch_indices_indexing_index_total{namespace=~"$namespace"}[$__rate_interval]))
```

---

## Latency

### Processing latency

Time from writing a user command to the append-only log until the processing engine reads and
starts processing it. High values indicate a large processing backlog.

- **Unit:** seconds (quantiles)
- **Measurement:** p50, p90, p99
- **Threshold:** p99 < 250 ms

```promql
histogram_quantile(0.99, sum(rate(zeebe_stream_processor_latency_seconds_bucket{namespace=~"$namespace"}[$__rate_interval])) by (le))
```

---

### Exporting latency

Time from writing an event to the append-only log until the exporter reads and starts exporting
it. High values indicate a processing and/or exporting backlog.

- **Unit:** seconds (quantiles)
- **Measurement:** p50, p90, p99

```promql
histogram_quantile(0.99, sum(rate(zeebe_exporting_latency_seconds_bucket{namespace=~"$namespace"}[$__rate_interval])) by (le))
```

---

### Process instance execution time

Time from creating an instance until completion — the full end-to-end process execution time. This
metric is measured in the metric exporter.

- **Unit:** seconds (quantiles)
- **Measurement:** p50, p90, p99
- **Threshold:** p99 < 1 s

```promql
histogram_quantile(0.99, sum(rate(zeebe_process_instance_execution_time_seconds_bucket{namespace=~"$namespace"}[$__rate_interval])) by (le))
```

---

### Data availability

Time from creating an instance in the client until the process instance is available via the REST
API. This is the strongest end-to-end signal — it includes the full round-trip: processing,
exporting, ES indexing, REST layer, and the ES index fresh interval.

- **Unit:** seconds (quantiles)
- **Measurement:** p50, p90, p99
- **Threshold:** p99 < 5 s

```promql
histogram_quantile(0.99, sum by (le) (rate(starter_data_availability_latency_seconds_bucket{namespace=~"$namespace"}[$__rate_interval])))
```

---

### Request-response latency

Time from sending a request in the client to receiving a response. Measured in the Starter. Includes
processing latency (and the processing queue), plus authentication and authorization overhead.

- **Unit:** seconds (quantiles)
- **Measurement:** p50, p90, p99
- **Threshold:** p99 < 5 s

```promql
histogram_quantile(0.99, sum by (le) (rate(starter_response_latency_seconds_bucket{namespace=~"$namespace"}[$__rate_interval])))
```

---

## Resources

The following metrics apply to all running applications (Camunda and secondary storage). Replace
`$pod` with the relevant pod selector when querying a specific component.

### CPU usage

Measures the average CPU usage of the application.

- **Unit:** cores
- **Measurement:** rate

```promql
sum by (pod, container) (rate(container_cpu_usage_seconds_total{namespace=~"$namespace", container!=""}[$__rate_interval]))
```

---

### CPU throttling

Percentage of time the application is throttled by the system. Values above ~0% indicate the CPU
limit is too tight for the current load.

- **Unit:** percentage (0–100%)
- **Threshold:** ~0% for sustainable operation

```promql
sum(rate(container_cpu_cfs_throttled_periods_total{namespace=~"$namespace"}[$__rate_interval])) by (pod)
/ sum(rate(container_cpu_cfs_periods_total{namespace=~"$namespace"}[$__rate_interval])) by (pod)
* 100
```

---

### Memory limit

The memory limit assigned to the application.

- **Unit:** bytes
- **Measurement:** const

```promql
max(kube_pod_container_resource_limits_memory_bytes{namespace=~"$namespace", container=~".*(orchestration|zeebe|camunda).*"})
```

---

### Memory usage (container RSS)

Container RSS memory usage.

- **Unit:** bytes
- **Measurement:** gauge

```promql
container_memory_rss{namespace=~"$namespace"}
```

---

### JVM heap usage

JVM heap memory usage. Compare against memory limit to detect leak trends.

- **Unit:** bytes
- **Measurement:** gauge

```promql
sum(jvm_memory_bytes_used{namespace=~"$namespace", area="heap"}) by (pod)
```

---

### Disk usage (Elasticsearch PVCs)

Disk usage as a ratio of used to total capacity across Elasticsearch persistent volumes.

- **Unit:** percentage (0–1.0)

```promql
kubelet_volume_stats_used_bytes{namespace=~"$namespace", persistentvolumeclaim=~".*elastic.*"}
/ kubelet_volume_stats_capacity_bytes{namespace=~"$namespace", persistentvolumeclaim=~".*elastic.*"}
```

---

## Health indicators

### Backpressure — dropped request ratio

Percentage of requests dropped due to backpressure. Values above ~0% indicate the system is at
or near capacity.

- **Unit:** percentage (0–100%)
- **Threshold:** ~0% for sustainable operation

```promql
(
  sum(rate(zeebe_dropped_request_count_total{namespace=~"$namespace"}[$__rate_interval])) by (partition)
  / sum(rate(zeebe_received_request_count_total{namespace=~"$namespace"}[$__rate_interval])) by (partition)
) * 100
```

---

### Processing backlog

Number of records appended to the log but not yet processed by the process engine. A growing
backlog drives higher processing latency.

- **Unit:** records
- **Measurement:** difference between last appended and last processed position

```promql
max by (partition) (zeebe_log_appender_last_appended_position{namespace=~"$namespace"})
- max by (partition) (zeebe_stream_processor_last_processed_position{namespace=~"$namespace"})
```

---

### Exporting backlog

Number of records committed to the log but not yet exported. A growing exporting backlog drives
higher data availability latency. It is decoupled from processing throughput.

- **Unit:** records
- **Measurement:** difference between last committed and last exported position

```promql
max(zeebe_log_appender_last_committed_position{namespace=~"$namespace"}) by (partition)
- max(zeebe_exporter_last_exported_position{namespace=~"$namespace"}) by (partition)
```

---

### Write IOPS

Number of filesystem write operations per second.

- **Unit:** ops/s
- **Measurement:** rate

```promql
sum by (pod, container) (rate(container_fs_writes_total{namespace=~"$namespace", container!=""}[$__rate_interval]))
```

