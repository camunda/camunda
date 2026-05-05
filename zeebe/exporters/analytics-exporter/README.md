# Camunda Analytics Exporter

Zeebe exporter that ships product analytics events to a Camunda analytics endpoint.
Designed for Self-Managed and SaaS deployments.

## Design Principles

### Fire-and-forget

The exporter **never impacts broker throughput**. Every record's position is updated eagerly
and unconditionally — before any analytics processing happens. If the analytics pipeline is
slow, saturated, or completely down, the broker keeps running at full speed.

### Analytics-grade delivery

Data loss is accepted. Events may be dropped under any of these conditions:

- The batch queue is full (back-pressure from a slow or unreachable endpoint)
- The broker crashes or restarts (in-memory queue is lost)
- The endpoint returns errors (no persistent retry, no disk buffering)

This is a deliberate trade-off: analytics data is approximate, not exact.

### No record filter

The exporter does **not** set a `RecordFilter`. It accepts all records from the broker to
track the full log position for compaction. Only event records with registered handlers
produce analytics events; everything else is a fast no-op (EnumMap lookup returning null).

## Configuration

```yaml
zeebe:
  broker:
    exporters:
      analytics:
        className: io.camunda.exporter.analytics.AnalyticsExporter
        args:
          endpoint: "https://analytics.cloud.camunda.io"
          enabled: true
```

| Property   | Default                              | Description                                                               |
|------------|--------------------------------------|---------------------------------------------------------------------------|
| `endpoint` | `https://analytics.cloud.camunda.io` | Analytics endpoint base URL.                                              |
| `enabled`  | `false`                              | Opt-in. When false, the exporter updates positions but does nothing else. |

## Building

```bash
# Build with dependencies
./mvnw install -pl zeebe/exporters/analytics-exporter -am -Dquickly -T1C

# Run unit tests
./mvnw verify -pl zeebe/exporters/analytics-exporter -DskipTests=false -DskipITs -Dquickly
```
