# Local monitoring environment

This is a Docker Compose setup with Prometheus, Grafana, and a Zeebe broker, for testing locally
that metrics are exported and scraped correctly. It is not used to edit the dashboards shown in
the benchmark environment; see [../README.md](../README.md) for that.

## Running the stack

```bash
docker-compose up -d
```

This starts:

- **Zeebe**, with Prometheus metrics enabled, at http://localhost:9600/actuator/prometheus
- **Prometheus**, scraping the Zeebe broker, at http://localhost:9090
- **Grafana**, pre-provisioned with the Prometheus datasource and the dashboards from
  [../grafana](../grafana), at http://localhost:3000 (login `admin` / `camunda`)

## Verifying a dashboard against an existing cluster

> [!WARNING]
> Prefer editing and verifying dashboards directly in [Grafana in the benchmark
> environment](https://dashboard.benchmark.camunda.cloud/dashboards) (see
> [../README.md](../README.md)) — it already has access to real cluster data, and is easier to
> use than the port-forward setup below. Only follow these steps when the benchmark environment's
> Grafana does not have the cluster you need as a datasource.

Panels that render fine against a local broker can still be wrong against real workloads — a metric may not
exist under the name you assumed, or a label you filter on may never be set. To check a panel before it is
deployed, point the local Grafana at the Prometheus of any cluster. For example, for `camunda-benchmark-prod`,
you can run the load test there to validate the panel against a real workload. This is read-only: nothing is deployed or changed in the cluster.

The local Grafana comes pre-provisioned with a **Prometheus port-forwarded** datasource (see
[grafana/provisioning/datasources/datasources.yml](grafana/provisioning/datasources/datasources.yml))
that reaches a Prometheus forwarded from your machine. The container reaches the host through
`host.docker.internal`, which the [compose file](docker-compose.yml) wires up via `extra_hosts`.
It is unreachable until you start a port-forward:

1) Log in to Teleport and select the benchmark cluster:

```sh
tsh login --proxy=camunda.teleport.sh:443 camunda.teleport.sh
tsh kube login camunda-benchmark-prod
```

2) Forward the cluster's Prometheus to port 9091, so it does not collide with the local Prometheus
on 9090. Leave this running — it dies when the Teleport certificate expires, and is restarted
with the same command:

```sh
kubectl port-forward -n monitoring svc/kube-prometheus-stack-prometheus 9091:9090
```

3) Open a dashboard and select **Prometheus (port forward)** in the `DS_PROMETHEUS` picker.
