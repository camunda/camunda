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
