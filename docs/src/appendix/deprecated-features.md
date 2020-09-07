# Deprecated Features

This section lists deprecated features.

## Deprecated in 0.23.0-alpha2
- TOML configuration - deprecated and removed in 0.23.0-alpha2
- Legacy environment variables - deprecated in 0.23.0-alpha2, to be removed in 0.25.0-alpha2

New configuration:
```yaml
exporters:
  elasticsearch:
    className: io.zeebe.exporter.ElasticsearchExporter
  debughttp:
    className: io.zeebe.broker.exporter.debug.DebugHttpExporter
```

In terms of specifying values, there were two minor changes:
- Memory sizes are now specified like this: `512MB` (old way: `512M`)
- Durations, e.g. timeouts, can now also be given in ISO-8601 Durations format. However you can still use the established way and specify a timeout of `30s`
