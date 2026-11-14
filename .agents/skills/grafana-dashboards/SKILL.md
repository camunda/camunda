---
name: grafana-dashboards
description: Use when creating or changing a Grafana dashboard in monitor/grafana/ — adding or editing panels, rows, queries, or variables, including adding panels for new or changed metrics. Covers the dashboard rules (UID, as code tag, DS_PROMETHEUS, refresh and time range, rows), the v2 and classic JSON models, PromQL, and deployment through grafana-dashboards.
---

# Grafana dashboards

Grafana dashboards for Camunda live in `monitor/grafana/` as JSON. Several of them are fetched by the
[grafana-dashboards](https://github.com/camunda/grafana-dashboards) repository and deployed to the dev, int, and
production Grafana instances, so a mistake here can break a dashboard in every environment after merge. See
`monitor/README.md` for the human-oriented guide.

## Rules for every dashboard

Apply these rules to what you add or change.

- **UID**: use a readable, unique `teamname-dashboardname` UID such as `core-features-overview`. Never use a random or
  auto-generated UID. Never reuse a UID, and never change the UID of an existing dashboard (links, playlists, and alerts
  depend on it). Search `monitor/grafana/` and the `grafana-dashboards` repository before choosing one.
- **Title**: must not equal the name of the Grafana folder it is deployed to.
- **Tag**: set the `as code` tag.
- **Datasource**: query Prometheus through the `DS_PROMETHEUS` datasource variable. Never hardcode a datasource name or
  UID. Avoid `thanos-global-view`: it sends every query to all Thanos systems and can put heavy load on the monitoring
  backend. Use it only when a panel truly needs cross-cluster data and there is no other way.
- **Refresh**: disable automatic refresh. Every selectable refresh interval must be 30s or slower.
- **Time range**: the default range must be 24h or less.
- **Layout**: group related panels into rows. Collapse rows that are expensive to query or show optional detail,
  because collapsed rows do not run their queries until expanded.

Where these live in the JSON depends on the data model:

|                  |                      v2 (Camunda 8.11+, Grafana 13+)                      |         Classic (`stable/8.10` and older)         |
|------------------|---------------------------------------------------------------------------|---------------------------------------------------|
| UID              | `metadata.name`                                                           | `uid`                                             |
| Tags             | `spec.tags`                                                               | `tags`                                            |
| Datasource var   | `DatasourceVariable` named `DS_PROMETHEUS` in `spec.variables`            | `type: datasource` entry in `templating.list`     |
| Time and refresh | `spec.timeSettings` (`from`, `to`, `autoRefresh`, `autoRefreshIntervals`) | `time`, `refresh`, `timepicker.refresh_intervals` |

Set the time and refresh settings explicitly in the file. For the classic shape, see the `timepicker` block in
[kube-dns.json](https://github.com/camunda/grafana-dashboards/blob/main/dashboards/sre/kube-dns.json#L661-L671).

## Editing workflow

1. Edit the dashboard JSON under `monitor/grafana/` directly.
2. Keep the diff small: change only the fields the task needs. Do not reformat, re-indent, or reorder the rest of the
   file, so the pull request stays reviewable.
3. Keep the existing data model. Do not convert a classic dashboard to v2 unless the task asks for it.
4. Do not query the benchmark clusters or set up a local environment unless the user asks. Instead, suggest how to
   test the change against real data: in the
   [benchmark Grafana](https://dashboard.benchmark.camunda.cloud/dashboards), or, if it lacks the cluster needed, with
   the local setup in `monitor/local-env/README.md`, which forwards a cluster's Prometheus to a local Grafana over
   Teleport.

## PromQL

- Pick the visualization from the metric type: counters use `rate()` or `increase()`, histograms use
  `histogram_quantile()`, gauges are shown as they are.
- Build a query from the inside out: start with the bare metric and check what it returns before adding operators.
- Prefer `$__rate_interval` over a hardcoded range.
- Avoid unbounded high-cardinality selectors. Keep the labels that legends and links rely on in the aggregation.
- For multi-value variables, use matcher syntax that accepts all selected values (`=~`).

## Deployment

- Dashboards maintained here, such as Zeebe, Data Layer, and Core Features, are referenced from the
  `grafana-dashboards` repository through a ConfigMap with a `.json.url` pointing at the raw file on `main`. Others,
  such as SRE and Controller, are maintained in that repository and must be changed there.
- A new local dashboard in `grafana-dashboards` does nothing until it is listed in its folder's `configMapGenerator`,
  and a new folder must be included from `dashboards/kustomization.yaml`. Remote dashboards must be listed under
  `resources`.
- Merging to `main` reaches the development stage. Integration and production are promoted by a manual release in
  `grafana-dashboards` (a pre-release for integration, a full release for production).
- Before deleting a Zeebe dashboard, check the reliability-testing deployment in
  [zeebe-infra](https://github.com/camunda/zeebe-infra/blob/main/gcp/zeebe-io/zeebe-cluster/monitoring/kube-prometheus-stack.yml),
  which may reference it.
- `make lint` in `grafana-dashboards` checks the `as code` tag and some datasource references only. It does not check
  UID uniqueness, PromQL, remote URLs, refresh settings, or Kustomize inclusion. Check those yourself.

## Pull request checklist

- [ ] UID is readable, unique, and unchanged for existing dashboards
- [ ] Title differs from the folder name; `as code` tag is set
- [ ] All queries use `DS_PROMETHEUS`; no `thanos-global-view` unless unavoidable
- [ ] No automatic refresh; refresh intervals 30s or slower; default range 24h or less
- [ ] Related panels are in rows; expensive or optional rows are collapsed
- [ ] Checked against real data in the benchmark Grafana or the local environment
- [ ] Diff contains only intended changes
