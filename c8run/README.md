# C8 Run

C8 Run is a packaged distribution of Camunda 8, which allows you to spin up Camunda 8 within seconds. It packages Camunda Core, ElasticSearch, and Connectors. The only prerequisite to run it, is to have a JDK 21+ installed locally.

C8 Run does not include Identity, Keycloak, or Optimize.

Please see the [Camunda 8 Run Installation Guide](https://docs.camunda.io/docs/next/self-managed/setup/deploy/local/c8run/) for more details.

## Installation

To install C8 Run, go to the [Camunda Releases page](https://github.com/camunda/camunda/releases), and search for "c8run". Then download and extract the artifact for your distribution.

![2024-09-24-144839_grim](https://github.com/user-attachments/assets/02f76946-fd43-4f92-8bad-6a3fa8f2e2f4)

Once extracted, go to the extracted folder and run one of the following commands:

## Linux / Mac Options

```
Usage: start.sh
Options:
  --detached   - Starts Camunda Run as a detached process
```

### Shutdown

To stop c8run on Linux or Mac, run `./shutdown.sh`

## Windows options

```
Usage: c8run.exe [start|stop] (options...)

There are currently no options for windows, but we plan to add support for --config and --detached.
```

## Note about connectors

Connectors is configured to run on port `8085`. Other camunda applications are expected to be accessible via `localhost:8080/<component>`.

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

## CI requirement for merging

Only CI checks related to C8Run (those with "c8run" in the name) are required for merging. Non-C8Run-related CI checks can be ignored.