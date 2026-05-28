# Build and Start Camunda Locally (H2)

## Prerequisites

- **JDK 21** - all Camunda core modules are built and tested with JDK 21
- **macOS Apple Silicon** - if you hit protoc errors, install Rosetta:

  ```bash
  softwareupdate --install-rosetta --agree-to-license
  ```

## Clean previous data

```bash
rm -rf dist/target/camunda-zeebe/camunda-data dist/target/camunda-zeebe/data dist/target/camunda-zeebe/logs
```

## Build

> **Note:** `-Dquickly` skips the dist module and will not produce a runnable distribution.
> Use the commands below to build the full dist for local usage.

```bash
# Backend only (recommended - skips Optimize, frontends, tests, and checks)
./mvnw clean install -pl dist/ -am -DskipChecks -DskipTests -PskipFrontendBuild -T1C

# With Operate, Tasklist, and Admin web UIs (skips Optimize)
./mvnw clean install -pl dist/ -am -DskipChecks -DskipTests -Dskip.fe.build=false -T1C
```

## Start

```bash
dist/target/camunda-zeebe/bin/camunda \
  '--spring.profiles.active=rdbmsH2,insecure,broker,operate,tasklist,admin' \
  '--camunda.security.initialization.users[0].username=demo' \
  '--camunda.security.initialization.users[0].password=demo' \
  '--camunda.security.initialization.users[0].name=Demo' \
  '--camunda.security.initialization.users[0].email=demo@example.com' \
  '--camunda.security.initialization.default-roles.admin.users[0]=demo'
```

## Endpoints

|  Endpoint  |                     URL                     |
|------------|---------------------------------------------|
| Operate    | http://localhost:8080/operate/              |
| Tasklist   | http://localhost:8080/tasklist/             |
| Admin      | http://localhost:8080/admin/                |
| REST API   | http://localhost:8080/v2/                   |
| Swagger UI | http://localhost:8080/swagger-ui/index.html |
| gRPC API   | localhost:26500                             |
| Actuator   | http://localhost:9600/actuator              |

Login: **demo / demo**

## Components

The single broker process bundles several products together. When you read log lines or hit
an error, this tells you which piece is talking:

| Component      | What it does                                                  | Where you see it           |
| -------------- | ------------------------------------------------------------- | -------------------------- |
| **Zeebe**      | The engine that executes BPMN processes (the "broker")        | Terminal logs, gRPC :26500 |
| **Operate**    | UI for monitoring running and completed process instances     | /operate/                  |
| **Tasklist**   | UI for humans completing User Tasks in a process              | /tasklist/                 |
| **Admin**      | UI for users, roles, tenants, authorizations (= Identity)     | /admin/                    |
| **REST API**   | HTTP API for deploying BPMNs and starting instances           | /v2/                       |
| **H2 / RDBMS** | Embedded database storing process history and Operate's data  | `dist/target/.../data`     |

Optimize (process analytics) is **not** part of this local setup — it's a separate product
with its own backend and database.

## Verify

```bash
curl -s http://localhost:8080/v2/topology | jq '.brokers | length'  # expect: 1
```

Then open <http://localhost:8080/operate/> and log in.

## Stop

Press `Ctrl+C` in the broker terminal for a graceful shutdown (drains in-flight work, flushes
the H2 store).

If you `kill -9` or force-quit, the RDBMS exporter position can drift from the database state
and the broker will fail to restart with `expected N but found -1` errors. The fix is to clean
the data directories (see [Clean previous data](#clean-previous-data)) and start fresh.

---

## Frontend dev mode (Operate / Tasklist / Admin)

For frontend-only changes, skip the full dist rebuild and use the Vite dev server instead.
Edits to `.tsx`/`.ts` files hot-reload in the browser — no rebuild needed.

1. Build the backend only:

   ```bash
   ./mvnw clean install -pl dist/ -am -DskipChecks -DskipTests -PskipFrontendBuild -T1C
   ```

2. Start the broker (same command as the [Start](#start) section above).

3. In a separate terminal, start the Vite dev server for the app you're working on:

   |   App    |        Directory        | Command         | Port | Browser URL                       |
   |----------|-------------------------|-----------------|------|-----------------------------------|
   | Operate  | `operate/client`        | `npm run start` | 3000 | <http://localhost:3000/operate/>  |
   | Tasklist | `tasklist/client`       | `npm run start` | 3000 | <http://localhost:3000/tasklist/> |
   | Admin    | `identity/client`       | `npm run dev`   | 5173 | <http://localhost:5173/admin/>    |

   The dev server proxies API calls (`/v2`, `/v1`, `/api`, `/login`, `/logout`) to the broker
   on `:8080`, so the frontend at `:3000` (or `:5173`) and the backend cooperate transparently.

> **Port conflict:** Operate and Tasklist both default to port 3000. Run only one at a time, or
> override with `--port` on the npm script.

---

## Additional configuration options

### With authentication enabled

Replace `insecure` profile and add BASIC auth:

```bash
dist/target/camunda-zeebe/bin/camunda \
  '--spring.profiles.active=rdbmsH2,broker,operate,tasklist,admin' \
  '--camunda.security.authentication.method=BASIC' \
  '--camunda.security.authentication.unprotected-api=false' \
  '--camunda.security.authorizations.enabled=true' \
  '--camunda.security.initialization.users[0].username=demo' \
  '--camunda.security.initialization.users[0].password=demo' \
  '--camunda.security.initialization.users[0].name=Demo' \
  '--camunda.security.initialization.users[0].email=demo@example.com' \
  '--camunda.security.initialization.default-roles.admin.users[0]=demo'
```

REST API calls then require basic auth: `curl -u demo:demo http://localhost:8080/v2/topology`

### Audit log

Audit log is enabled by default. Configure filtering by category or entity type:

```bash
# Filter by category (ADMIN, DEPLOYED_RESOURCES, USER_TASKS)
'--camunda.data.audit-log.user.categories=ADMIN,DEPLOYED_RESOURCES'
'--camunda.data.audit-log.client.categories=ADMIN'

# Exclude specific entity types from logging
# Available: PROCESS_INSTANCE, VARIABLE, INCIDENT, JOB, USER_TASK, DECISION, BATCH,
#            USER, MAPPING_RULE, ROLE, GROUP, TENANT, AUTHORIZATION, RESOURCE, CLIENT
'--camunda.data.audit-log.user.excludes=VARIABLE,JOB'
'--camunda.data.audit-log.client.excludes=VARIABLE'

# Disable audit log entirely
'--camunda.data.audit-log.enabled=false'
```

- `user` = operations performed by logged-in users
- `client` = operations performed by API clients (M2M)

### Multi-tenancy

```bash
'--camunda.security.multi-tenancy.checks-enabled=true'
```

Requires auth enabled (not compatible with `insecure` profile). Tenants can be configured at init:

```bash
'--camunda.security.initialization.tenants[0].tenantId=tenant-a'
'--camunda.security.initialization.tenants[0].name=Tenant A'
```

### MCP gateway

Enable the MCP (Model Context Protocol) gateway for AI agent integration:

```bash
'--camunda.mcp.enabled=true'
```

### Selectively disable web UIs

Keep the API but disable specific UIs:

```bash
'--camunda.webapps.operate.ui-enabled=false'
'--camunda.webapps.tasklist.ui-enabled=false'
'--camunda.webapps.identity.ui-enabled=false'
```

### History TTL (RDBMS/H2)

Control how long completed process data is retained:

```bash
# Default TTL for all entities (Java Duration format)
'--camunda.data.secondary-storage.rdbms.history.default-history-t-t-l=P30D'

# Specific TTLs
'--camunda.data.secondary-storage.rdbms.history.decision-instance-t-t-l=P7D'

# Cleanup batch size and interval
'--camunda.data.secondary-storage.rdbms.history.history-cleanup-batch-size=1000'
'--camunda.data.secondary-storage.rdbms.history.min-history-cleanup-interval=PT1M'
```

### Controlled clock (testing)

Enable programmatic time control for testing timer events:

```bash
'--camunda.system.clock-controlled=true'
```

### All configuration reference

Full list of all config properties with env var equivalents:

```text
dist/target/camunda-zeebe/config/defaults.yaml
```

---

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| `Address already in use: 26500` or `:8080` on start | A previous broker is still running | `lsof -i :26500 :8080` to find the PID, then `kill <pid>` |
| `expected N but found -1` exporter errors after restart | Data dir state diverged from RDBMS (usually after a `kill -9`) | Clean data dirs (see [Clean previous data](#clean-previous-data)) and start fresh |
| Login: "Username and Password do not match" | Stale CSRF token or cookies | Open the app in an incognito / private browser window |
| `protoc` errors on Apple Silicon during build | Missing Rosetta | `softwareupdate --install-rosetta --agree-to-license` |
