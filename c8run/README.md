
# C8 Run

This is a deployment method very similar to C7 Run.

## Options

Not all of these options are implemented,

```
Usage: run.sh [start|stop] (options...) 
Options:
  --webapps    - Enables the Camunda Platform Webapps
  --rest       - Enables the REST API
  --swaggerui  - Enables the Swagger UI
  --example    - Enables the example application
  --production - Applies the production.yaml configuration file
  --detached   - Starts Camunda Run as a detached process
```

Right now, `start`, `stop`, and `--detached` are the only functional options.
