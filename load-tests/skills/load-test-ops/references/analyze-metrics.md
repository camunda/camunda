# Analyze metrics — alternative methods

The [default GHA path](../SKILL.md#analyze-metrics) covers the headline queries without
kubectl. Use one of these when you have kubectl/Grafana access, or need more than the
headline set.

## Via Grafana MCP (in Claude Code sessions — no port-forward needed)

Use `mcp__grafana__*` tools directly when running inside Claude Code. Confirm tools are active
with `mcp__grafana__check_datasources_health` at session start.

**Rule: always run `list_prometheus_metric_names` before writing any PromQL query.** Guessed
metric names return empty results silently — one discovery call eliminates all name-guess failures.

**Session startup:**
```
mcp__grafana__check_datasources_health()
  → datasourceUid: "prometheus" should show status: OK

mcp__grafana__list_prometheus_label_values(
  datasourceUid="prometheus", labelName="namespace",
  matches=[{filters: [{name: "__name__", value: "zeebe_.*", type: "=~"}]}],
  startRfc3339="now-24h")
  → lists all namespaces with recent Zeebe data
```

**Metric name discovery:**
```
mcp__grafana__list_prometheus_metric_names(datasourceUid="prometheus", regex="optimize.*", limit=50)
mcp__grafana__list_prometheus_metric_names(datasourceUid="prometheus", regex="zeebe.*process.*", limit=20)
```

For metric names and PromQL queries, see [`load-tests/docs/metrics.md`](https://github.com/camunda/camunda/blob/main/load-tests/docs/metrics.md).

See `load-tests/README.md` → **Accessing metrics via Claude Code (Grafana MCP)** for setup instructions.

## Via local script (kubectl + port-forward)

Faster when you have cluster access. First port-forward the monitoring Prometheus pod, then run
the script directly against `http://localhost:9090`:

```bash
# Port-forward the monitoring Prometheus service (leave running in a separate terminal)
kubectl port-forward svc/kube-prometheus-stack-prometheus -n monitoring 9090:9090

# Then run the metrics script
cd load-tests/docs/scripts
./loadTestMetrics.sh <full-namespace-with-c8-prefix> 1200 > /tmp/results.json
```

Args: `<namespace> [duration_seconds] [endpoint] [extra_curl_opts]`.

## Additional metrics via Prometheus (kubectl required)

When the headline metrics aren't enough, query the full set from
[`load-tests/docs/metrics.md`](https://github.com/camunda/camunda/blob/main/load-tests/docs/metrics.md) directly against Prometheus. Open a
port-forward in one terminal, then run ad-hoc PromQL in another:

```bash
# Open port-forward (keep this terminal open)
kubectl port-forward -n monitoring svc/prometheus-operated 9090:9090

# Ad-hoc query — substitute <namespace> and the PromQL from metrics.md
curl -sG 'http://localhost:9090/api/v1/query_range' \
  --data-urlencode 'query=<promql>' \
  --data-urlencode 'start=<unix-timestamp>' \
  --data-urlencode 'end=<unix-timestamp>' \
  --data-urlencode 'step=15s' \
  | jq '.data.result'
```
