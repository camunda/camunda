# Analytics Lake Serving

Read-only serving layer over the analytics lake warehouse: a Spring Boot REST API backed by an
embedded DuckDB instance that queries the Parquet tables the lake writer produces, plus a small
React UI. Ingest, folding and compaction live entirely in `analytics-lake` — this module only ever
reads.

This is a scaffold / proof-of-life: `GET /api/health`, `GET /api/tables`, `POST /api/query`, and a
one-page UI listing the discovered tables. The explain-shaped read features (REST tools and their
UI) build on this structure in follow-up lanes.

## Architecture

Read-only serving over the lake warehouse: at startup, `LakeViewRegistry` lists the directories
under `<warehouse-dir>/lake/` and registers a DuckDB view for each one that already has Parquet
data, without ever hardcoding a table name — tables simply appear as other lanes' writers merge.
`LakeQueryService` executes SQL against those views with a hard row cap and a statement timeout.
`LakeController` exposes that read path over HTTP.

## Running

### As a jar

```bash
./mvnw package -pl analytics/analytics-lake-serving -Dmaven.repo.local=/tmp/m2-agent-h -Dmaven.repo.local.tail=$HOME/.m2/repository
java -jar analytics/analytics-lake-serving/target/analytics-lake-serving-*-exec.jar \
  --lake.serving.warehouse-dir=/path/to/warehouse
```

The jar serves the built UI from `/` and the REST API from `/api/*` on port `8092` by default.

### Dev mode (Vite dev server + proxy)

```bash
# Terminal 1: the Spring Boot backend
./mvnw spring-boot:run -pl analytics/analytics-lake-serving \
  -Dspring-boot.run.arguments=--lake.serving.warehouse-dir=/path/to/warehouse

# Terminal 2: the Vite dev server (proxies /api to :8092)
cd analytics/analytics-lake-serving/client
npm install
npm run dev
```

Open the URL Vite prints (defaults to `http://localhost:5174`).

### Skipping the frontend build

`./mvnw package -pl analytics/analytics-lake-serving -PskipFrontendBuild` skips the
`frontend-maven-plugin` executions (no `node`/`npm` needed) for a Java-only build; the resulting
jar serves no static UI in that case.

## Configuration

All properties are bound with the `lake.serving` prefix (`application.yaml` / `-D` / env vars):

| Property                            | Default | Notes                                                                                   |
|--------------------------------------|---------|------------------------------------------------------------------------------------------|
| `lake.serving.warehouse-dir`         | *none — required* | Root of the lake writer's warehouse directory. The app fails fast at startup if unset. |
| `lake.serving.state-dir`             | *none (optional)* | Reserved for the open-state snapshot views a follow-up lane adds; not yet read.        |
| `lake.serving.max-rows`              | `500`   | Hard cap on the number of rows any query can return.                                     |
| `lake.serving.query-timeout-seconds` | `15`    | Best-effort per-statement timeout for `POST /api/query`.                                 |
| `server.port`                        | `8092`  | Standard Spring Boot property.                                                           |

## REST API

- `GET /api/health` — `{"status": "UP"}`.
- `GET /api/tables` — every discovered view with its current row count (`{"name": ..., "rowCount": ...}`);
  `rowCount` is `null` if the count query itself fails.
- `POST /api/query` — plain SQL in the request body (`Content-Type: text/plain`), `{"columns": [...], "rows": [[...], ...]}`
  out, capped at `lake.serving.max-rows`. Failures come back as `400` with `{"error": "..."}`.
