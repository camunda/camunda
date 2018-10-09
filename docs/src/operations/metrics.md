# The Metrics

When operating a distributed system like Zeebe, it is important to put proper monitoring in place.
To facilitate this, Zeebe exposes an extensive set of metrics.

Zeebe writes metrics to a file. The reporting interval can be configured.

## Types of metrics

* **Counters**: a time series that records a growing count of some unit. Examples: number of bytes transmitted over the network, number of workflow instances started, ...
* **Gauges**: a time series that records the current size of some unit. Examples: number of currently open client connections, current number of partitions, ...

## Metrics Format

Zeebe exposes metrics directly in Prometheus text format.
The details of the format can be read in the [Prometheus documentation][prom-format].

**Example:**

```
zb_storage_fs_total_bytes{cluster="zeebe",node="localhost:26500",partition="0"} 4192 1522124395234
```

The record above descibes that the total size on bytes of partition `0` on node `localhost:26500` in cluster `zeebe` is `4192`. The last number is a unix epoch timestamp.

## Configuring Metrics

Metrics can be configured in the [configuration file](operations/the-zeebecfgtoml-file.html#Metrics).

## Connecting Prometheus

As explained, Zeebe writes metrics to a file. The default location of the file is `$ZB_HOME/metrics/zeebe.prom`. There are two ways to connect Zeebe to Prometheus:

### Node exporter

In case you are already using the prometheus node exporter, you can relocate the metrics file to the scrape directory of the node exporter. The configuration would look as follows:

```
[metrics]
reportingInterval = 15
metricsFile = "zeebe.prom"
directory = "/var/lib/metrics"
```

### HTTP

In case you want to scrape Zeebe nodes via HTTP, you can start a http server inside the metrics directory and expose
the directory via HTTP. In case python is available, you can use

```
$cd $ZB_HOME/metrics
$python -m SimpleHTTPServer 8000
```

Then, add the following entry to your `prometheus.yml`:

```
- job_name: zb
  scrape_interval: 15s
  metrics_path: /zeebe.prom
  scheme: http
  static_configs:
  - targets:
    - localhost:8000
```

## Grafana Dashboards

The Zeebe community has prepared two ready to use Grafana dashboars:

### Overview Dashboard

The overview dashboard summarized high level metrics on a cluster level. This can be used to monitor a Zeebe production cluster. The dashboard can be found [here](https://grafana.com/dashboards/5237).

### Low-level Diagnostics Dashboard

The diagnostics dashboard provides more low level metrics on a node level. It can be used for gaining a better understanding about the workload currently performed by individual nodes. The dashboard can be found [here](https://grafana.com/dashboards/5210).

## Available Metrics

All metrics exposed by Zeebe have the `zb_*`-prefix.

To each metric, the following labels are added:

* `cluster`: the name of the Zeebe cluster (relevant in case you operate multiple clusters).
* `node`: the identifier of the node which has written the metrics

Many metrics also add the following labels:

* `partition`: cluster-unique id of the partition

The following components expose metrics:

* `zb_broker_info`: summarized information about available nodes
* `zb_buffer_*`: diagnostics, buffer metrics
* `zb_scheduler_*`: diagnostics, utilization metrics of Zeebe's internal task scheduler
* `zb_storage_*`: storage metrics
* `zb_streamprocessor_*`: stream processing metrics such as events processed by partition
* `zb_transport_*`: network transport metrics such as number of open connections, bytes received, transmitted, etc ...
* `zb_workflow_*`: worflow metrics such as number of workflow instances created, completed, ...

[prom-format]: https://prometheus.io/docs/instrumenting/exposition_formats/#text-format-details
