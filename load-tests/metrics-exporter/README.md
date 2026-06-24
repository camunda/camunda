# Camunda internal metrics exporter for Prometheus

This project exposes Camunda internal metrics via a Prometheus endpoint.

Internal metrics are either very specific metrics that we used during internal testing, or metrics that _could_ be exported by Camunda itself, but are not yet.

## How to run?

You need access to an Elasticsearch or OpenSearch instance. You can, for instance, create a port-forwarding to a running load test with:

```shell
kubectl port-forward svc/elastic 9200
```

Start the exporter with:

```shell
go run main.go
```

Then fetch the metrics using:

```shell
curl -s http://localhost:9600/metrics | grep "^camunda_loadtest"
```

## Metrics exported

| ····························Metric·name···························· | ···································Description···································· | ·Type·· |
|---------------------------------------------------------------------|------------------------------------------------------------------------------------|---------|
| `camunda_loadtest_last_scrape_timestamp_seconds`                    | Unix timestamp of the last successful scrape, by module.                           | gauge   |
| `camunda_loadtest_scrape_errors_total`                              | Total number of failed Elasticsearch scrapes, by module.                           | counter |
| `camunda_loadtest_operate_root_process_instances_completed`         | Number of completed Operate root process instance documents.                       | gauge   |
| `camunda_loadtest_operate_root_process_instances`                   | Number of Operate root process instance documents.                                 | gauge   |
| `camunda_loadtest_optimize_process_instances_completed`             | Number of completed Optimize process instance documents (endDate field present).   | gauge   |
| `camunda_loadtest_optimize_process_instances`                       | Number of all Optimize process instance documents.                                 | gauge   |

## Notes

1. If Elasticsearch is not reachable (down, or not deployed), the exporter doesn't crash but doesn't collect any metrics from Elasticsearch. The logs should show the underlying error.

