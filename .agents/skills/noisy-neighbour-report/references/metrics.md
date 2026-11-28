# Metrics behind the noisy neighbour report

`W` is the window (default 15 minutes), evaluated at the end of the window. `tenant` is derived from the
pod name with `label_replace`: `starter-(pt[0-9]+)-.*` gives `pt<N>`, anything else is `default`. The
same is done for `worker-...` pods.

## Per tenant, from the load testers

Defined in `load-tests/load-tester/.../metrics/StarterMetricsDoc.java` and
`StarterLatencyMetricsDoc.java`.

| Number | Metric | Notes |
| --- | --- | --- |
| Offered load (PI/s) | `rate(starter_process_instances_submissions_total[W])` | `submitted` + `skipped`. Falls back to answered requests when missing |
| Skipped | the `skipped` outcome of the same counter | The starter dropped the request because too many were in flight: the generator or the cluster is too slow |
| Served (PI/s) | `rate(starter_process_instances_started_total{outcome="success"}[W])` | |
| Rejected | `outcome="failure"` of the same counter | Backpressure has `error` `grpc_resource_exhausted`, `http_503` or `http_429` |
| Response latency p50 / p90 / p99 | `histogram_quantile(q, sum by (tenant, le) (rate(starter_response_latency_seconds_bucket[W])))` | Request sent to response, as the client saw it |
| Data in the secondary storage p50 / p90 / p99 | `histogram_quantile(q, ... starter_data_availability_latency_seconds_bucket ...)` | From creating the instance until it can be queried: processing, export, indexing, the search path |
| Waiting for storage | `avg_over_time(starter_data_availability_pending_process_instances[W])` | Instances started but not yet queryable |
| In flight | `avg_over_time(starter_process_instances_in_flight[W])` | Requests without an answer |
| Jobs handled (/s) | `rate(camunda_client_worker_job_handled_total[W])` | Worker client metric. Verify that it is scraped |

## Per tenant, from the brokers

The metrics carry `physicalTenant` and `partition` (`PartitionKeyNames`). The partition number is unique
only within one tenant.

| Number | Query |
| --- | --- |
| Export queue (records) | `sum by (physicalTenant) (max by (physicalTenant, partition) (zeebe_log_appender_last_committed_position) - max by (physicalTenant, partition) (zeebe_exporter_last_exported_position))` |
| Change of the queue | the same expression at the end minus at the start of the window |
| Highest queue | `max_over_time((<expression>)[W:1m])` |
| Export rate | `sum by (physicalTenant) (rate(zeebe_exporter_last_exported_position[W]))` |
| Queue per partition | `max by (physicalTenant, partition) (zeebe_log_appender_last_committed_position) - min by (physicalTenant, partition) (zeebe_exporter_last_exported_position)`. `min` takes the slowest exporter of the partition |
| Export rate per partition | `min by (physicalTenant, partition) (rate(zeebe_exporter_last_exported_position[W]))` |
| Export throughput | the rate per partition above, summed over the partitions of a tenant, and its share of the sum over the tenants. Positions per second, not exact records. Every tenant has its own partitions, so the positions of different tenants are separate logs |
| Export lag (s) | queue divided by rate for each partition. The tenant's lag is its worst partition: a summed queue over a summed rate lets the fast partitions hide a slow one. Without partition data, the summed queue over the summed rate. A queue with a rate of 0 is a stall: the lag is unbounded (`stalled`) and misses the target. A queue or rate that is missing also misses it |

To check that the label exists on a namespace: `count by (physicalTenant) (zeebe_exporter_last_exported_position{namespace="<ns>"})`.

## Cluster context

| Number | Query |
| --- | --- |
| Requests dropped by backpressure | `sum(rate(zeebe_dropped_request_count_total[W])) / sum(rate(zeebe_received_request_count_total[W]))` |
| CPU per broker | `sum by (pod) (rate(container_cpu_usage_seconds_total{pod=~"camunda-[0-9]+"}[W]))` |
| CPU throttling per broker | `rate(container_cpu_cfs_throttled_periods_total) / rate(container_cpu_cfs_periods_total)` |

## Statistics

- **Served share** = served divided by offered. A tenant is served when it is at least `--min-service`.
- **Spread** = mean, min, max, population standard deviation and CV (standard deviation / mean), over the
  quiet tenants.
- **Jain's fairness index** = `(sum x)^2 / (n * sum x^2)` over the quiet tenants' served share. 1 is even,
  `1/n` is one tenant getting everything.
- **Interference** = value of a quiet tenant in the noisy run divided by the value of the same tenant in the
  reference run. Noisy tenants are not compared.
- **Fair share** = the tenant's weight divided by the sum of weights. Tenants without a load tester are left out.

## Other useful numbers not in the report

- Per tenant CPU is not measured by Zeebe today, only per pod. A CPU budget solution would add it.
- Processing queue per tenant: `max by (physicalTenant, partition) (zeebe_log_appender_last_appended_position) - max by (physicalTenant, partition) (zeebe_stream_processor_last_processed_position)`.
