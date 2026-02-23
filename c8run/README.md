# Camunda 8 Run

C8 Run is a packaged distribution of Camunda 8, which allows you to spin up Camunda 8 within seconds.

Please refer to the [local installation with Camunda 8 Run guide](https://docs.camunda.io/docs/next/self-managed/quickstart/developer-quickstart/c8run/) for further details.

## Default secondary storage

Camunda 8 Run now starts with H2 as the secondary storage backend. No additional configuration or flags are required—running `./c8run start` launches a full stack backed by an in-memory H2 database that is perfect for local development and testing scenarios. H2 is not supported for production workloads.

Elasticsearch remains available, but it is no longer started automatically. The `--disable-elasticsearch` flag defaults to `true`, so Elasticsearch processes are skipped unless you explicitly re-enable them.

### Running with Elasticsearch

1. Update `c8run/configuration/application.yaml` so that the secondary storage type is `elasticsearch` and points to your cluster:

   ```yaml
   camunda:
     data:
       secondary-storage:
         type: elasticsearch
         elasticsearch:
           url: http://localhost:9200
   ```
2. Start Camunda 8 Run and instruct it to manage Elasticsearch:

   ```bash
   ./c8run start --disable-elasticsearch=false
   ```
3. When stopping a stack that was started with Elasticsearch, pass the same flag to ensure the Elasticsearch processes are terminated:

   ```bash
   ./c8run stop --disable-elasticsearch=false
   ```

## CI requirement for merging

Only CI checks related to C8Run (those with "c8run" in the name) and CI runs marked as `required` are needed to merge. Non-C8Run-related CI checks can be ignored.

## Build C8run locally

To build and run C8Run locally run `./package.sh` followed by `./start.sh`

### Connectors launcher

C8Run automatically starts the connectors runtime through Spring Boot's `PropertiesLauncher` for connector bundles versioned 8.9.0 or newer (including snapshots). Older bundles continue to run via the legacy `JarLauncher`, so you can switch versions in `.env` without extra configuration.
