---

name: monitor-dashboard
description: Use when making changes or improvements to Grafana dashboards in monitor/grafana/dashboards/ — adding or editing panels, writing or fixing PromQL queries, updating dashboard variables, changing layout/rows, exporting dashboard JSON, or preparing a dashboard for deployment. Also use when creating a brand-new dashboard in this repo or debugging why a visualization shows unexpected data.
----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------

# Monitor Dashboard

Reference for editing, creating, and deploying Grafana dashboards in `monitor/grafana/dashboards/`. Dashboards in this directory are automatically fetched by the [grafana-dashboards](https://github.com/camunda/grafana-dashboards) repository and deployed to dev, int, and production Grafana environments. Mistakes here can silently break dashboards in all three environments after merge.

## Iron rules

- **UID must be human-readable and meaningful.** Use `teamname-dashboardname` format (e.g., `core-features-overview`, `zeebe-processing-heatmap`). Never use auto-generated or random UIDs — they are unreadable and make cross-repo references fragile. Never reuse UIDs across dashboards.
- Dashboard name must not be identical to a folder name in Grafana.
- Every dashboard must have the `as code` tag set. Dashboards deployed from this repo are read-only in Grafana — edits made in the UI will be overwritten on next deployment.
- **Never use `thanos-global-view` as the datasource.** Use `${DS_PROMETHEUS}` instead. `thanos-global-view` fans out queries to all Thanos systems simultaneously and can generate massive load on the monitoring backend. Only use it when cross-cluster aggregation is explicitly required and there is no other way.
- Every panel query must use the `datasource` template variable (`${datasource}` or `${DS_PROMETHEUS}`), not a hardcoded datasource name or UID. Create a `datasource` variable on every new dashboard.
- Automatic refresh must be disabled. Refresh interval options must all be `>= 30s`. Default time range must be `<= 24h`.
- Set the `timepicker` block explicitly in the dashboard JSON. Reference structure (from [kube-dns.json](https://github.com/camunda/grafana-dashboards/blob/main/dashboards/sre/kube-dns.json#L661-L671)):

  ```json
  "timepicker": {
    "collapse": false,
    "enable": true,
    "notice": false,
    "now": true,
    "refresh_intervals": [
      "30s", "1m", "5m", "15m", "30m", "1h", "2h", "1d"
    ],
    "type": "timepicker"
  }
  ```
- Group related panels into rows. Collapse rows that are expensive to query or provide optional/extra detail — collapsed rows do not execute queries until expanded.
- Export JSON using "Export for another instance" (checkbox in Grafana's export dialog) — this strips environment-specific datasource IDs and makes the JSON portable.
- Never commit a dashboard you saved as a copy without updating the `uid`, filename, and title to match the original. Copies have mangled UIDs by default.

## Standard workflow

### Editing an existing dashboard

1. Open the dashboard in a dev Grafana instance ([dev](https://grafana-central.internal.dev.ultrawombat.com/) or [internal SaaS](https://grafana-central.internal.camunda.io/)).
2. Save it as a copy before editing (prevents your in-progress state from being overwritten by a deployment).
3. Make changes. Save incrementally.
4. When done: `Dashboards > Share > Export > Export for another instance`. Download JSON.
5. Overwrite the existing file in `monitor/grafana/dashboards/<team>/`.
6. Verify `uid`, filename, and dashboard title match the original — not the copy.
7. Delete the copied dashboard from Grafana.

### Creating a new dashboard

1. In Grafana, navigate to your team's folder. `New > Dashboard`.
2. Set title, description, and tags in Settings. Add the `as code` tag now.
3. Save (Grafana auto-assigns a UID).
4. Open `Settings > JSON Model`, find `uid`, replace with a sensible value like `teamname-dashboardname`. Save again.
5. Create a `datasource` variable under `Settings > Variables`. Use it in all panel queries as `${datasource}`.
6. Build panels. Export as above.
7. Save JSON to `monitor/grafana/dashboards/<team>/`. Create the folder if it does not exist.

### Local testing

Spin up Grafana + Prometheus + 3-broker cluster locally:

```sh
docker-compose --project-directory ./ \
  -f monitor/docker-compose.yml \
  -f docker/compose/docker-compose.yaml \
  up -d
```

Grafana: http://localhost:3000 (admin / camunda). Prometheus: http://localhost:9090. Prometheus scrapes brokers every 5s with labels `namespace=local`, `pod=broker-*`.

Tear down including volumes:

```sh
docker-compose --project-directory ./ \
  -f monitor/docker-compose.yml \
  -f docker/compose/docker-compose.yaml \
  down -v
```

To scrape a locally running Zeebe broker, add to the Prometheus service in `monitor/docker-compose.yml`:

```yaml
extra_hosts:
  - "host.docker.internal:host-gateway"
```

And add to Prometheus scrape config:

```yaml
- job_name: 'zeebe_local'
  metrics_path: /actuator/prometheus
  static_configs:
    - targets: ['host.docker.internal:9600']
```

## Deployment

Dashboards in `monitor/grafana/dashboards/` are fetched by the [grafana-dashboards](https://github.com/camunda/grafana-dashboards) repository via raw GitHub URL:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: example-dashboard
data:
  example-dashboard.json.url: https://raw.githubusercontent.com/camunda/camunda/main/monitor/grafana/dashboards/example-team/example-dashboard.json
```

- Merging to `main` deploys to **dev/int** automatically.
- **Production** requires a GitHub release in the grafana-dashboards repo (no pre-release checkbox).
- Some dashboards (SRE, Controller) live directly in grafana-dashboards, not here — check there first if you can't find a dashboard in `monitor/grafana/`.
- The Zeebe reliability-testing environment uses a separate deployment in [zeebe-infra](https://github.com/camunda/zeebe-infra/blob/main/gcp/zeebe-io/zeebe-cluster/monitoring/kube-prometheus-stack.yml). Check it before deleting a Zeebe dashboard.

## PromQL guidance

- Match the metric type to the visualization type. Counters → `rate()`/`increase()` for graphs. Gauges → direct value. Histograms → `histogram_quantile()`.
- Build queries inside-out: start with the raw metric, understand what it returns, then layer on operators and aggregations.
- PromQL supports joins like SQL. `*` on two metrics with matching labels = inner join. Use `on(label)` to narrow join keys.
- Avoid high-cardinality label selectors in hot dashboard panels — they impact Prometheus query performance.
- Prefer `$__rate_interval` over hardcoded intervals so panels behave correctly across different time range selections.

## Panel layout best practices

- Group related panels into rows.
- Collapse rows that are expensive to query or provide optional detail — they only execute queries when expanded.
- Use consistent units across all panels in a row (set units in the panel's `Standard options > Unit`).
- Add a description to every panel that isn't self-explanatory from its title.

## Pre-PR checklist

- [ ] UID is human-readable, unique, and follows `teamname-dashboardname` format (not auto-generated)
- [ ] Dashboard title ≠ folder name
- [ ] `as code` tag present on dashboard
- [ ] No `thanos-global-view` datasource unless cross-cluster aggregation is explicitly required
- [ ] `datasource` variable created and used in all queries (`${DS_PROMETHEUS}` or `${datasource}`)
- [ ] Automatic refresh disabled; refresh intervals all `>= 30s`; default time range `<= 24h`
- [ ] `timepicker` block set explicitly in JSON with correct `refresh_intervals`
- [ ] JSON exported with "Export for another instance" checked
- [ ] Filename, title, and UID match (not a stale copy artifact)
- [ ] Related panels grouped into rows; expensive/optional rows collapsed
- [ ] Tested locally or in a dev Grafana instance

## Reference

- [`monitor/README.md`](monitor/README.md) — local setup, docker-compose usage, Grafana overview
- [`monitor/grafana/dashboards/`](monitor/grafana/dashboards/) — all dashboard JSON files
- [`monitor/Makefile`](monitor/Makefile) — `make grizzly` for local Grizzly-based editing
- [`docs/observability/metrics.md`](docs/observability/metrics.md) — how to add new metrics
- [grafana-dashboards repo](https://github.com/camunda/grafana-dashboards) — deployment config and folder structure
- [grafana-dashboards new-dashboard checklist](https://github.com/camunda/grafana-dashboards/blob/main/.github/ISSUE_TEMPLATE/new-dashboard.md) — canonical checklist for new dashboards
- [Grafana visualization docs](https://grafana.com/docs/grafana/latest/visualizations/panels-visualizations/visualizations/)
- [PromQL basics](https://prometheus.io/docs/prometheus/latest/querying/basics/)

