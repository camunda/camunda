# C7 Load Test Metrics

All app metrics are served on port 9600 at `/metrics`. PostgreSQL metrics come from `monitor/postgres/queries.yaml` and are named `cnpg_c7_*` in both environments (CNPG adds the prefix on Kubernetes, the local Prometheus relabels to match).

## Throughput

| Metric | Source | Meaning |
|---|---|---|
| `c7_process_instances_total{event="started\|completed"}` | engine | Committed process instance starts and ends |
| `c7_external_tasks_total{event="created\|completed\|failed\|deleted"}` | engine | Committed external task lifecycle events |
| `starter_process_instances_submissions_total{outcome="sent\|skipped"}` | starter | Requests sent, or skipped because of the in-flight limit |
| `starter_process_instances_started_total{outcome,error}` | starter | Answered start requests (`error`: `none`, `http_<code>`, `timeout`, `io`) |
| `worker_tasks_received_total` | worker | Tasks fetched and locked |
| `worker_tasks_completed_total{outcome}` | worker | Complete calls by outcome |

```promql
# Completed PI/s (C8 equivalent: zeebe_element_instance_events_total{type="PROCESS", action="completed"})
sum(rate(c7_process_instances_total{namespace=~"$namespace", event="completed"}[$__rate_interval]))
```

## Latency

| Metric | Meaning |
|---|---|
| `c7_process_instance_duration_seconds` | End time minus start time of the process instance |
| `starter_response_latency_seconds` | Start request round trip |
| `worker_task_wait_seconds` | External task creation until a worker received it |
| `worker_complete_latency_seconds` | Complete REST call |
| `c7_command_duration_seconds{command,outcome}` | Engine command including commit. Count = engine transactions |

```promql
histogram_quantile(0.99, sum by (le) (rate(c7_process_instance_duration_seconds_bucket{namespace=~"$namespace"}[$__rate_interval])))
```

## Growing state and database

| Metric | Meaning |
|---|---|
| `cnpg_c7_table_rows{table}` | Live rows per `act_*` table (estimate) |
| `cnpg_c7_table_total_bytes{table}` | Table size including indexes |
| `cnpg_c7_table_dead_rows{table}` | Rows waiting for vacuum |
| `cnpg_c7_table_seq_scan{table}` | Sequential scans (missing index on a growing table) |
| `cnpg_c7_db_size_bytes` | Database size |
| `cnpg_c7_db_xact_commit`, `cnpg_c7_db_tup_inserted`, ... | Database-wide counters |
| `cnpg_c7_wal_wal_bytes` | WAL bytes generated |
| `cnpg_c7_runtime_external_tasks` | Open external tasks (backlog) |
| `cnpg_c7_activity_connections{state,wait_event_type}` | Connections; `active` + `Lock` = row lock contention |

```promql
# WAL bytes per completed process instance
sum(rate(cnpg_c7_wal_wal_bytes{namespace=~"$namespace"}[$__rate_interval]))
/ sum(rate(c7_process_instances_total{namespace=~"$namespace", event="completed"}[$__rate_interval]))
```

## Saturation signals

- `cnpg_c7_runtime_external_tasks` grows: workers or the engine's fetch/complete path cannot keep up.
- `starter_process_instances_in_flight` grows or `skipped` > 0: the start path is saturated.
- `hikaricp_connections_pending` > 0: engine waits for DB connections.
- `c7_command_duration_seconds_count{outcome="optimistic_locking"}`: contention between engines or concurrent completions.
