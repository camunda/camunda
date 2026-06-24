# Monitoring

## Metrics

Zeebe and other Camunda components export several metrics to facilitate monitoring a cluster.
Currently, metrics are exported using Prometheus. You can find
documentation about the different Zeebe metrics
[here](https://docs.camunda.io/docs/product-manuals/zeebe/deployment-guide/operations/metrics).

### Testing

You can easily test metrics locally by using the standard provided [docker compose
file](../docker/compose/docker-compose.yaml) in combination with the one [here](docker-compose.yml), e.g.:

```sh
docker-compose --project-directory ./ -f docker-compose.yml -f ../docker/compose/docker-compose.yaml up -d
```

This will start the usual 3 brokers cluster, as well as a Grafana [instance](http://localhost:3000/) (on port 3000; login: u `admin`, p `camunda`) and a Prometheus instance on
port 9090. The Prometheus instance is configured to scrape the brokers every 5 seconds, and pre-assigns them the
namespace and pod label as `local` and `broker-*`.

> Remember that docker-compose does not remove volumes on the down command, so if you are completely done with it you
> will need to run either `docker-compose --project-directory ./ -f docker-compose.yml -f ../docker/compose/docker-compose.yaml down -v`
> or `docker volume prune`

### Testing with local Zeebe

When you want to use a local Zeebe Broker, you need to locally modify the config:
- enable Prometheus Docker container to access localhost ports:

```yaml
# add to the prometheus service
extra_hosts:
- "host.docker.internal:host-gateway"
```

- add the local Zeebe broker to Prometheus config:

  ```yaml
  # add to scrape_configs
  - job_name: 'zeebe_local'
    metrics_path: /actuator/prometheus
    static_configs:
         - targets: ['host.docker.internal:9600']

  ```

## Grafana

We use Grafana to visualize our metrics in dashboards for  monitoring and troubleshooting purposes. You can find general information about Grafana [here](https://grafana.com/docs/grafana/latest/fundamentals/).

### Creating a new dashboard (Camunda internal)

This is a step-by-step guide for creating a new Grafana dashboard, but especially the deployment steps may also be relevant for modifying existing dashboards.

This guide focuses on making use of existing metrics to create visualizations in Grafana.
If you want to learn how to add new metrics, a good starting point can be found in the observability documentation [here](../docs/observability/metrics.md).

#### Prerequisites

**Write access** for a (team) folder in a development Grafana instance (e.g. [dev](https://grafana-central.internal.dev.ultrawombat.com/) or [internal SaaS](https://grafana-central.internal.camunda.io/) environment). To get access, ask your team lead or the SRE team.

*or*

A **local Grafana** instance (e.g. using [Grizzly](https://grafana.com/blog/2024/10/29/edit-your-git-based-grafana-dashboards-locally/) to run Grafana locally, which can be started through the `make grizzly` command (see [Makefile](Makefile)) and instead make the modifications directly in the codebase.

For this guide the assumption is that you are using a shared Grafana instance.

#### Creating the dashboard

1) In Grafana, navigate to the desired folder and select `"New" > "Dashboard"` (or `"Import"` if you have an existing Dashboard that you want to use as a blueprint as described [here](https://grafana.com/docs/grafana/latest/reference/export_import/#importing-a-dashboard)).
2) Go to "Settings" to modify the dashboard title, description, tags, etc. Follow the best practices described in the dashboards repository [readme](https://github.com/camunda/grafana-dashboards) and [dashboard issue template](https://github.com/camunda/grafana-dashboards/blob/main/.github/ISSUE_TEMPLATE/new-dashboard.md).
3) Save the dashboard (this automatically sets a UID)
4) Go back into edit mode for the dashboard and navigate to `"Settings" > "JSON Model"` and look for the `"uid"`. Set a unique but sensible UID (e.g. "teamname-dashboardname") and save the dashboard again.
5) Create or edit variables (in the dashboard settings), panels and sections as needed.

#### Creating visualizations

This guide does not go into details about creating visualizations, but these are some helpful resources and tips to get started:

* You can find the official documentation for creating visualizations on the Grafana website [here](https://grafana.com/docs/grafana/latest/visualizations/panels-visualizations/visualizations/)
* By default, we use [Prometheus](https://prometheus.io/) to collect metrics. Consequently, the queries for our dashboards are written in [PromQL](https://prometheus.io/docs/prometheus/latest/querying/basics/). Familiarize yourself with the basics of it before getting started - especially the different metrics data types since those are relevant for choosing the right visualization type.
* Build queries iteratively "from the inside out": Start with just the metric you want to display and understand what it represents even if you already have the operators you want to use on it in mind.
* Think of metrics as database queries. If there is a way you would want to operate on the data in SQL, it probably also exists in PromQL - including joins on other metrics.
* There is always some small way to improve every panel you make, either through refining the queries or tweaking little parts of how it's visualized. At some point you need to make the decision that it's good enough, push it, and then iterate over it later with feedback from actual usage.

#### Exporting the dashboard

1) Save your modifications (if you are editing an already deployed dashboard, save it as a copy to ensure it does not get overwritten by a new deployment from code)
2) Exit edit mode and make sure to export as described [here](https://grafana.com/docs/grafana/latest/dashboards/share-dashboards-panels/#export-a-dashboard-as-json), checking `Export the dashboard to use in another instance` as you do.
3) Save the dashboard JSON in the codebase under [grafana/dashboards](grafana/dashboards) - create a folder for your team if it does not exist yet. Ensure that the filename, dashboard name and UID are correct if you saved it as a copy (they should reflect the original dashboard's values since the goal is to overwrite it).
4) Delete the copied dashboard (if one was created)

**Note**: If you edit an already existing dashboard, keep in mind that it may get automatically deployed once it was merged into the code base (see next section).

#### Dashboard Deployment

Our dashboards are deployed from the [grafana-dashboards repository](https://github.com/camunda/grafana-dashboards). You can follow the information there for creating new dashboards or folders, which also includes some best practices for designing Grafana dashboards that should be followed before deployment.
Some dashboards are directly maintained in that repository (e.g. SRE and Controller) and need to be updated there. Others (e.g. Zeebe, Data Layer and Core Features) are defined and maintained in this repository in the [grafana folder](grafana) and get fetched by linking the raw Github content of the dashboard definition like:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: example-dashboard
data:
  example-dashboard.json.url: https://raw.githubusercontent.com/camunda/camunda/main/monitor/grafana/dashboards/example-team/example-dashboard.json
```

You can refer to existing dashboards in that repository for examples if you are deploying a new dashboard, or use it to check where the deployed dashboards are maintained.

**Note**: The Zeebe team maintains an own Grafana deployment for [reliability testing](/docs/testing/reliability-testing.md). In case of dashboard deletion, check their deployment definition in the [zeebe-infra repository](https://github.com/camunda/zeebe-infra/blob/main/gcp/zeebe-io/zeebe-cluster/monitoring/kube-prometheus-stack.yml) to ensure it is not referenced there.

### Example Dashboard: Zeebe

You can find a pre-built Grafana dashboard [here](grafana/zeebe.json) to
visualize most metrics. This is the dashboard that we use to test and
monitor our own Zeebe installations.

> NOTE: this dashboard is used for development and can serve as a
> starting point for your own dashboard, but may not be tailored for your
> particular use case.

![Zeebe Grafana Dashboard Preview](grafana/preview.png)

