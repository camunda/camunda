# Analytics Exporter Local Test

Run the analytics exporter locally and see raw log events + pre-aggregated metrics in Grafana.

## 1. Start the observability stack

```bash
docker compose -f zeebe/exporters/analytics-exporter/src/test/resources/local-test/docker-compose.yaml up -d
```

This starts:
| Service | URL | Purpose |
|---|---|---|
| OTel Collector | localhost:4318 | Receives OTLP logs + metrics |
| Prometheus | localhost:9090 | Metric storage |
| Loki | localhost:3100 | Log storage |
| Grafana | localhost:3333 | Dashboards (no login) |

## 2. Configure the analytics exporter

### Option A: Environment variables

Add these to your Zeebe / Camunda run configuration:

```
CAMUNDA_LICENSE_KEY=local-test-license-key
ZEEBE_BROKER_EXPORTERS_ANALYTICS_CLASSNAME=io.camunda.exporter.analytics.AnalyticsExporter
ZEEBE_BROKER_EXPORTERS_ANALYTICS_ARGS_ENDPOINT=http://localhost:4318
ZEEBE_BROKER_EXPORTERS_ANALYTICS_ARGS_PUSHINTERVAL=PT10S

```

### Option B: YAML configuration

Add to your `application.yaml` or pass via `-Dspring.config.additional-location=`:

```yaml
zeebe:
  broker:
    exporters:
      analytics:
        className: io.camunda.exporter.analytics.AnalyticsExporter
        args:
          endpoint: http://localhost:4318
          pushInterval: PT10S

camunda:
  license:
    key: local-test-license-key
```

A reference file is provided at `application.yaml` in this directory.

## 3. Generate load

### Shell script (included)

```bash
# Default: 5 instances/sec for 120 seconds
./zeebe/exporters/analytics-exporter/src/test/resources/local-test/generate-load.sh

# Custom: 10 instances/sec for 60 seconds
./zeebe/exporters/analytics-exporter/src/test/resources/local-test/generate-load.sh 10 60
```

### curl (one-off)

```bash
# Deploy a process
curl -X POST http://localhost:8080/v2/deployments \
  -F "resources=@-;filename=test.bpmn;type=application/xml" <<'BPMN'
<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="http://camunda.org/test">
  <bpmn:process id="analytics-test" isExecutable="true">
    <bpmn:startEvent id="start"/><bpmn:endEvent id="end"/>
    <bpmn:sequenceFlow id="flow" sourceRef="start" targetRef="end"/>
  </bpmn:process>
</bpmn:definitions>
BPMN

# Create instances
for i in $(seq 1 50); do
  curl -s -X POST http://localhost:8080/v2/process-instances \
    -H "Content-Type: application/json" \
    -d '{"bpmnProcessId":"analytics-test"}'
done
```

### Camunda Modeler / Web UI

Deploy any process via Modeler or the web UI at http://localhost:8080 and start instances manually.

## 4. View in Grafana

Open http://localhost:3333 → **Explore**

### Metrics (Prometheus datasource)

|                                 Query                                  |                      What it shows                       |
|------------------------------------------------------------------------|----------------------------------------------------------|
| `camunda_process_instance_created_total`                               | Pre-aggregated counter                                   |
| `sum by (camunda_process_id) (camunda_process_instance_created_total)` | Breakdown by process                                     |
| `camunda_metric_export_window`                                         | Companion gauge (flush sequence, positions, event times) |

### Logs (Loki datasource)

|                              Query                              |        What it shows         |
|-----------------------------------------------------------------|------------------------------|
| `{service_name="camunda-zeebe"}`                                | All raw analytics events     |
| `{service_name="camunda-zeebe"} \|= "process_instance_created"` | Process instance events only |

## 5. Tear down

```bash
docker compose -f zeebe/exporters/analytics-exporter/src/test/resources/local-test/docker-compose.yaml down
```

