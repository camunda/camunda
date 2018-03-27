# The zeebe.cfg.toml file



## Metrics

Configure metrics. For more information about Zeebe's metrics subsystem, read
the documentation section on [metrics](operations/metrics.html).

**Properties**

* **reportingInterval**: interval in seconds; controls at which rate the metrics are written to the metrics file. Typical values would be in a range of [5-15] seconds. Default value is `5`.
* **metricsFile**: filename: the name of the metrics file. Default value is `zeebe.prom`.
* **directory**: filename: the directory to which the metrics file is written. Default value is `metrics/`, relative to Zeebe's root directory.

**Example:**

```
[metrics]
reportingInterval = 15
metricsFile = "zeebe.prom"
directory = "/var/lib/metrics"
```
