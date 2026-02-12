# Azure Event Hub Exporter

The Azure Event Hub Exporter is a Zeebe exporter that forwards workflow engine events to Azure Event Hub.

## Overview

This exporter allows you to stream Zeebe events (process instances, jobs, incidents, etc.) to Azure Event Hub for further processing, analytics, or integration with other Azure services.

## Features

- Exports Zeebe records as JSON events to Azure Event Hub
- **Exports only events by default** - filters out commands and command rejections
- Configurable batching for efficient event delivery
- Automatic retry on failures
- Support for Azure Event Hub connection strings

## Configuration

Configure the exporter in your broker configuration:

```yaml
zeebe:
  broker:
    exporters:
      azure-eventhub:
        className: io.camunda.exporter.eventhub.AzureEventHubExporter
        args:
          connectionString: "Endpoint=sb://your-namespace.servicebus.windows.net/;SharedAccessKeyName=your-key-name;SharedAccessKey=your-key"
          eventHubName: "your-event-hub-name"
          maxBatchSize: 100
          batchIntervalMs: 1000
```

or via environment variables:

```bash
ZEEBE_BROKER_EXPORTERS_AZUREEVENTHUB_CLASSNAME=io.camunda.exporter.eventhub.AzureEventHubExporter
ZEEBE_BROKER_EXPORTERS_AZUREEVENTHUB_ARGS_CONNECTIONSTRING="Endpoint=sb://your-namespace.servicebus.windows.net/;SharedAccessKeyName=your-key-name;SharedAccessKey=your-key"
ZEEBE_BROKER_EXPORTERS_AZUREEVENTHUB_ARGS_EVENTHUBNAME=your-event-hub-name
ZEEBE_BROKER_EXPORTERS_AZUREEVENTHUB_ARGS_MAXBATCHSIZE=100
ZEEBE_BROKER_EXPORTERS_AZUREEVENTHUB_ARGS_BATCHINTERVALMS=1000
```

### Configuration Parameters

- `connectionString` (string, required): The Azure Event Hub connection string. This includes the namespace endpoint, shared access key name, and shared access key.
- `eventHubName` (string, required): The name of the Event Hub to send events to.
- `maxBatchSize` (integer, optional): Maximum number of events to batch before sending (default: 100).
- `batchIntervalMs` (long, optional): Time interval in milliseconds to flush events if batch size is not reached (default: 1000).

#### Record Type Filtering

By default, the exporter only exports **events** and filters out commands and command rejections. You can customize this behavior:

```yaml
zeebe:
  broker:
    exporters:
      azure-eventhub:
        className: io.camunda.exporter.eventhub.AzureEventHubExporter
        args:
          connectionString: "..."
          eventHubName: "your-event-hub-name"
          index:
            event: true        # Export events (default: true)
            command: false     # Export commands (default: false)
            rejection: false   # Export command rejections (default: false)
```

Or via environment variables:

```bash
ZEEBE_BROKER_EXPORTERS_AZUREEVENTHUB_ARGS_INDEX_EVENT=true
ZEEBE_BROKER_EXPORTERS_AZUREEVENTHUB_ARGS_INDEX_COMMAND=false
ZEEBE_BROKER_EXPORTERS_AZUREEVENTHUB_ARGS_INDEX_REJECTION=false
```

## Azure Event Hub Setup

1. Create an Azure Event Hub namespace in the Azure Portal
2. Create an Event Hub within the namespace
3. Create a Shared Access Policy with "Send" permissions
4. Copy the connection string from the policy

## Event Format

Events are exported as JSON in the standard Zeebe record format. Each event contains:
- Record metadata (key, position, timestamp, etc.)
- Record value (specific to the record type)
- Value type (PROCESS_INSTANCE, JOB, INCIDENT, etc.)

Example event:
```json
{
  "key": 123456,
  "position": 789,
  "timestamp": 1234567890123,
  "valueType": "PROCESS_INSTANCE",
  "intent": "ELEMENT_ACTIVATED",
  "value": {
    "processInstanceKey": 123456,
    "bpmnProcessId": "my-process",
    ...
  }
}
```

## Development

### Build

```bash
./mvnw install -pl zeebe/exporters/azure-eventhub-exporter -am -Dquickly
```

### Run Tests

```bash
./mvnw verify -pl zeebe/exporters/azure-eventhub-exporter -DskipITs
```

### Run Integration Tests

```bash
./mvnw verify -pl zeebe/exporters/azure-eventhub-exporter -DskipUTs
```

## Security Considerations

- Store connection strings securely using environment variables or secret management systems
- Use Azure Managed Identity when possible instead of connection strings
- Ensure proper network security groups and firewall rules are configured
- Regularly rotate access keys

## License

This exporter is licensed under the Camunda License 1.0.
