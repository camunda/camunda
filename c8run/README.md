
# C8 Run

This is a deployment method very similar to C7 Run.

## Options

```
Usage: run.sh [start|stop] (options...)
Options:
  --config     - Applies the specified configuration file
  --detached   - Starts Camunda Run as a detached process
```


## Note about connectors

Connectors is configured to run on port `8085` just like the docker compose file. Other camunda applications are expected to be accessible via `localhost:8080/<component>`.

```
-------------------------------------------
Access each component at the following urls:

Operate:                     http://localhost:8080/operate
Tasklist:                    http://localhost:8080/tasklist
Zeebe Cluster Endpoint:      http://localhost:26500
Inbound Connectors Endpoint: http://localhost:8085

When using the Desktop Modeler, Authentication may be set to None.

Refer to https://docs.camunda.io/docs/guides/getting-started-java-spring/ for help getting started with Camunda

-------------------------------------------
```
