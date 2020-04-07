# Deprecated Features

This section lists deprecated features.

## Deprecated in 0.23.0-alpha2
- TOML configuration - deprecated and removed in 0.23.0-alpha2
- Legacy environment variables - deprecated in 0.23.0-alpha2, to be removed in 0.24.0

### TOML Configuration files
TOML configuration files are no longer supported. Existing configuration files need to be changed into YAML configuration files.

The structure of the configuration files has not changed - with one exception: The exporters are now configured via a map instead of a list.

Old configuration:
```toml
 [[exporters]]
- id = "elasticsearch"
  className = "io.zeebe.exporter.ElasticsearchExporter"
- id = "debug-http"
  className = "io.zeebe.broker.exporter.debug.DebugHttpExporter"
```

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

### Legacy Environment Variables
With the changes to the configuration, legacy environment variables were also deprecated.

They are still supported during the backwards compatibility window and will be internally mapped to the new environment variables. If legacy environment variables are detected, there will be a warning in the log:

```
17:20:00.591 [] [main] WARN  io.zeebe.legacy - Found use of legacy system environment variable 'ZEEBE_HOST'. Please use 'ZEEBE_BROKER_NETWORK_HOST' instead.
17:20:00.602 [] [main] INFO  io.zeebe.legacy - The old environment variable is currently supported as part of our backwards compatibility goals.
17:20:00.602 [] [main] INFO  io.zeebe.legacy - However, please note that support for the old environment variable is scheduled to be removed for release 0.24.0.
```

These warnings will appear at startup of Zeebe broker or gateway, they will appear before the banner appears and they will cover all legacy environment variables. 

 