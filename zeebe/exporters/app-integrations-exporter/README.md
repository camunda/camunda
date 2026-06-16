# App Integrations Exporter

The App Integrations Exporter is a Zeebe exporter that forwards workflow engine events to an external App Integration Backend via HTTP.

## Architecture

```
┌─────────────────────┐         HTTP Events          ┌──────────────────────────┐
│                     │                              │                          │
│   Zeebe Engine      │                              │   App Integration        │
│                     │    ┌──────────────────┐      │   Backend                │
│  ┌───────────────┐  │    │                  │      │                          │
│  │   Exporter    │──┼───►│  POST /events    │─────►│  ┌──────────────────┐    │
│  │   Stream      │  │    │                  │      │  │  Event Handler   │    │
│  └───────────────┘  │    │  ┌────────────┐  │      │  └──────────────────┘    │
│                     │    │  │  JSON      │  │      │           │              │
│  Events:            │    │  │  Payload   │  │      │           ▼              │
│  - Process          │    │  └────────────┘  │      │  ┌──────────────────┐    │
│  - Job              │    │                  │      │  │  Integrations    │    │
│  - Incident         │    └──────────────────┘      │  │  (Slack, Teams,  │    │
│  - UserTask         │                              │  │   Webhooks, ...) │    │
│                     │                              │  └──────────────────┘    │
└─────────────────────┘                              └──────────────────────────┘
```

## Features

- Exports records as HTTP events to a configurable backend endpoint
- Supports batching and retry mechanisms for reliable delivery
- Configurable event filtering

## Configuration

Configure the exporter in your broker configuration:

```yaml
zeebe:
  broker:
    exporters:
      app-integrations:
        className: io.camunda.exporter.appint.AppIntegrationsExporter
        args:
          url: "http://app-integration-backend:8080/events"
          apiKey: "myAPIKey"
```

or via environment variables:

```bash
ZEEBE_BROKER_EXPORTERS_APPINTEXPORTER_ARGS_APIKEY=myApiKey
ZEEBE_BROKER_EXPORTERS_APPINTEXPORTER_ARGS_URL=http://localhost:8088/app-int
ZEEBE_BROKER_EXPORTERS_APPINTEXPORTER_CLASSNAME=io.camunda.exporter.appint.AppIntegrationsExporter
```

Additional configuration options:
- `url` (string, required): The endpoint URL of the App Integration Backend.
- `apiKey` (string, optional): API key for authentication with the backend.
- `clusterId` (string, optional): Identifier of the cluster, sent to the backend via the `X-Cluster-Id` header (see [Context headers](#context-headers)). Intended for Self-Managed setups; expected to be unique per cluster/physical-tenant configuration.
- `continueOnError` (boolean, optional): Whether to continue processing events on error (default: true).
- `maxRetries` (integer, optional): Maximum number of retry attempts for failed requests (default: 2).
- `retryDelayMs` (duration, optional): Delay between retry attempts (default: 1500).
- `requestTimeoutMs` (duration, optional): Total timeout for export request, including retries (default: 5000).
- `batchSize` (integer, optional): Number of events to batch before sending (default: 50).
- `batchIntervalMs` (duration, optional): Time interval to flush events if batch size is not reached (default: 2000).
- `maxBatchesInFlight` (integer, optional): Maximum number of finished batches that are in-flight for sending (default: 2).

### Authentication

The exporter supports different authentication mechanisms.
Currently, it supports API key authentication as well as no authentication (if the backend does not require it).

#### Example of API key authentication:

You can provide the API key via the `apiKey` configuration property. The exporter will include the API key in the `x-api-key` header of each HTTP request:

```
x-api-key: myAPIKey
```

## Context headers

On every request the exporter attaches context-identification headers so the backend can tell
which cluster/org/tenant a batch of events originates from. Each header is sent independently,
**only when its source value is available** — there is no dependency on the deployment model
(SaaS vs Self-Managed) or the configured authentication mechanism.

|         Header         |                               Source                                |                   Sent when                    |
|------------------------|---------------------------------------------------------------------|------------------------------------------------|
| `X-Org-Id`             | `CAMUNDA_CLOUD_ORGANIZATION_ID` environment variable (set in SaaS)  | present, non-blank and not the `null` sentinel |
| `X-Cluster-Id`         | `clusterId` config option (preferred), else the broker's cluster id | the chosen value is non-blank                  |
| `X-Physical-Tenant-Id` | the broker context's physical-tenant id for this exporter instance  | non-blank (the `default` tenant is sent as-is) |

Notes:
- The configured `clusterId` takes precedence over the broker-provided cluster id, so a
Self-Managed operator value wins when both are present.
- In SaaS the org id and cluster id are provided by the environment/broker; no extra
configuration is required.

## Metrics

The exporter publishes Micrometer metrics under the `zeebe.app.integrations.exporter` namespace.
The `MeterRegistry` handed to the exporter is already partition scoped, so the metrics below carry
no partition tag. In addition, the broker emits the standard per-exporter metrics (exporting
latency, last exported position, exporter state, per-value-type event counters) for this exporter
like for every other exporter.

|                          Metric                          |         Type         |      Tags      |                           Description                           |
|----------------------------------------------------------|----------------------|----------------|-----------------------------------------------------------------|
| `zeebe.app.integrations.exporter.token.fetch.failed`     | counter              | —              | Failures while fetching a new OAuth token (non-timeout errors). |
| `zeebe.app.integrations.exporter.timeout`                | counter              | `phase=token`  | Timeout reached while acquiring an OAuth token.                 |
| `zeebe.app.integrations.exporter.timeout`                | counter              | `phase=export` | Timeout reached while exporting a batch.                        |
| `zeebe.app.integrations.exporter.export.unauthorized`    | counter              | —              | `401 Unauthorized` responses received while exporting.          |
| `zeebe.app.integrations.exporter.export.failed`          | counter              | —              | Failed batch export attempts.                                   |
| `zeebe.app.integrations.exporter.records.exported`       | counter              | —              | Events successfully exported to the backend.                    |
| `zeebe.app.integrations.exporter.batch.size`             | distribution summary | —              | Number of events per exported batch.                            |
| `zeebe.app.integrations.exporter.flush.duration.seconds` | timer                | —              | Duration of exporting a batch to the backend.                   |
| `zeebe.app.integrations.exporter.batches.in.flight`      | gauge                | —              | Number of batches currently being exported.                     |

## Development

### Build

```bash
./mvnw install -pl zeebe/exporters/app-integrations-exporter -am -Dquickly
```

### Run Tests

```bash
./mvnw verify -pl zeebe/exporters/app-integrations-exporter -DskipITs
```

### Run Integration Tests

```bash
./mvnw verify -pl zeebe/exporters/app-integrations-exporter -DskipUTs
```

### Test receiving events locally with a mock server

You can run the docker image from https://hub.docker.com/r/mendhak/http-https-echo to test the exporter locally:

```bash
docker run -p 8088:8080 --rm -t mendhak/http-https-echo:latest
```

Then configure the exporter to point to `http://localhost:8088/events`.
