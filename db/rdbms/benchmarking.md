# RDBMS benchmarking

This is a quick guide to performance analysis and benchmarking of the relational databases on the
example of PostgreSQL.

## Connect to the Kubernetes database

```bash
kubectl port-forward postgres-postgresql-0 5432:5432
```

This tunnels the database port to your local machine, so you can connect to it using any database
client. You have two options to connect to the database:

Via the regular camunda user:

```
username: camunda
password: camunda
```

Via the postgres admin user:

```
username: postgres
password: camunda
```

For advanced performance analysis, we recommend using the postgres user, as it has more permissions
and allows you to access more detailed performance metrics, especially registering the
`pg_stat_statements` extension, which is crucial for performance analysis.

## Session analysis

To analyze the current `camunda` sessions and their activity, you can use the following SQL query:

```sql
select * from pg_stat_activity where state = 'active' AND usename = 'camunda' AND client_addr IS NOT NULL
```

What to watch for:
- `wait_event`: If this column is not null, it indicates that the session is currently waiting for a
specific event, such as a lock or I/O operation.
- `wait_event_type`: This column provides more context about the type of event the session is
waiting for, such as 'Lock', 'IO', 'Client', etc. If you see a lot of sessions waiting on locks,
it may indicate contention issues.
- `query_start`: This column shows when the current query started. If you see sessions that have
been active for a long time, it may indicate long-running queries that could be causing
performance issues.

## Statement analysis

The PostgreSQL image used for local development and load-testing in the zeebe-io cluster has the
`pg_stat_statements` extension pre-installed, but it is not enabled by default. To enable it, you need
to connect to the database using the postgres user and run the following SQL command:

```sql
CREATE EXTENSION pg_stat_statements;
```

This will create the extension and start collecting performance metrics for all SQL statements
executed on the database. You can then query the `pg_stat_statements` view to see the performance
metrics for each statement, such as execution time, number of calls, and more.

The following SQL query retrieves the top 50 most expensive SQL statements based on total execution
time, along with various performance metrics:

```sql
SELECT query,
       round((100 * total_exec_time / sum(total_exec_time) OVER ())::numeric,
             3)                                                      AS percentage_of_total_time,
       round((total_exec_time / NULLIF(rows, 0) * 1000)::numeric, 2) AS exec_time_per_1000_rows_ms,
       round(rows / NULLIF(calls, 0))                                AS avg_rows_per_call,
       round((total_exec_time / NULLIF(calls, 0))::numeric, 2)       AS avg_time_per_call_ms,
       round(total_exec_time::numeric, 2)                            AS total_exec_time_ms,
       round(mean_exec_time::numeric, 2)                             AS mean_time_ms,
       round(min_exec_time::numeric, 2)                              AS min_time_ms,
       round(max_exec_time::numeric, 2)                              AS max_time_ms,
       calls,
       rows
FROM pg_stat_statements
WHERE rows >= 0
  AND query NOT LIKE 'SELECT%' -- Focus on writes
ORDER BY total_exec_time DESC
LIMIT 50;
```

The most interesting columns in the result are:

- `percentage_of_total_time`: This shows how much of the total execution time is spent on each
  statement, which helps identify the most expensive queries.
- `exec_time_per_1000_rows_ms`: This shows the execution time per 1000 rows. In the RdbmsExporter,
  we try to modify/insert as many rows as possible in a single statement, so this metric helps us
  understand how well the database handles large batches of data. Also this metric is more
  meaningful than the average execution time per call, as it takes into account the number of rows
  processed by each statement.

To reset the collected statistics, you can use the following SQL command:

```sql
SELECT pg_stat_statements_reset();
```

## Table statistics

### Table and index sizes

The following SQL query retrieves the size of each table and its indexes in the database, ordered by
total size:

```sql
SELECT tablename,
       pg_size_pretty(pg_relation_size(quote_ident(schemaname) || '.' || quote_ident(tablename))::bigint)       AS table_size,
       pg_size_pretty(pg_indexes_size(quote_ident(schemaname) || '.' || quote_ident(tablename))::bigint)        AS indexes_size,
       pg_size_pretty(pg_total_relation_size(quote_ident(schemaname) || '.' || quote_ident(tablename))::bigint) AS total_size,
       pg_total_relation_size(quote_ident(schemaname) || '.' || quote_ident(tablename))::bigint                 AS total_size_bytes
FROM pg_tables
WHERE schemaname = 'public'
ORDER BY total_size_bytes DESC;
```

This gives a hint on which tables and indexes consume most disk space and also in which tables the
data to index ratio is the highest, which can indicate potential performance issues.

### Vacuum and analyze

The following SQL query gives an overview of the last vacuum and analyze operations performed on
each table, along with the number of dead tuples and the estimated number of rows:

```sql
SELECT schemaname,
       relname                                                                      AS tablename,
       pg_size_pretty(pg_total_relation_size(schemaname || '.' || relname))         AS total_size,
       n_dead_tup                                                                   AS dead_tuples,
       n_live_tup                                                                   AS live_tuples,
       round((100.0 * n_dead_tup / NULLIF(n_live_tup + n_dead_tup, 0))::numeric,
             2)                                                                     AS dead_tuple_percent,
       last_vacuum,
       last_autovacuum,
       last_analyze,
       last_autoanalyze
FROM pg_stat_user_tables
WHERE schemaname = 'public'
  AND n_live_tup > 0
ORDER BY dead_tuple_percent DESC NULLS LAST
LIMIT 30;
```

A high percentage of dead tuples (> 5%) can indicate that the table is not being vacuumed frequently
enough, which can lead to performance degradation. The last vacuum and analyze timestamps can also
help identify if there are any tables that have not been maintained recently.

### Cache hit ratio

The following SQL query calculates the cache hit ratio for each table, which indicates how often
the database is able to serve data from the cache instead of reading from disk:

```sql
SELECT
    schemaname,
    relname AS tablename,
    heap_blks_read AS disk_reads,
    heap_blks_hit AS cache_hits,
    round((100.0 * heap_blks_hit / NULLIF(heap_blks_hit + heap_blks_read, 0))::numeric, 2) AS cache_hit_ratio_percent,
    pg_size_pretty(pg_relation_size(schemaname||'.'||relname)) AS table_size
FROM pg_statio_user_tables
WHERE schemaname = 'public'
  AND (heap_blks_hit + heap_blks_read) > 1000
ORDER BY cache_hit_ratio_percent ASC NULLS LAST;
```

Depending on the size of the table and the workload, a cache hit ratio below 90% can indicate that
the database is frequently reading data from disk, which can lead to performance issues. In such
cases, it may be worth investigating if the table can be optimized, for example by adding indexes
or increasing the amount of memory allocated to the database to improve caching.

## Index statistics

### Cache hit ratio

The following SQL query calculates the cache hit ratio for each index, which indicates how often the database is able to serve index data from the cache instead of reading from disk:

```sql
SELECT
    schemaname,
    relname AS tablename,
    indexrelname AS indexname,
    idx_blks_read AS disk_reads,
    idx_blks_hit AS cache_hits,
    round((100.0 * idx_blks_hit / NULLIF(idx_blks_hit + idx_blks_read, 0))::numeric, 2) AS cache_hit_ratio_percent
FROM pg_statio_user_indexes
WHERE schemaname = 'public'
  AND (idx_blks_hit + idx_blks_read) > 1000
ORDER BY cache_hit_ratio_percent ASC NULLS LAST
LIMIT 30;
```

### Index usage statistics

The following SQL query shows whether indexes are actually being used by queries and how frequently
they are accessed. This is critical for identifying unused indexes that waste disk space and slow
down write operations:

```sql
SELECT
    schemaname,
    relname,
    indexrelname,
    idx_scan AS number_of_scans,
    idx_tup_read AS tuples_read,
    idx_tup_fetch AS tuples_fetched,
    pg_size_pretty(pg_relation_size(indexrelid)) AS index_size,
    CASE
        WHEN idx_scan = 0 THEN 'UNUSED - Consider dropping'
        WHEN idx_scan < 50 THEN 'RARELY USED - Review necessity'
        ELSE 'ACTIVELY USED'
    END AS usage_status
FROM pg_stat_user_indexes
WHERE schemaname = 'public'
ORDER BY idx_scan ASC, pg_relation_size(indexrelid) DESC;
```

The key columns to examine are:
- `number_of_scans`: How many times the index has been used. If this is 0, the index has never been
used and can likely be dropped to improve write performance.
- `tuples_read` vs `tuples_fetched`: A large difference between these values can indicate that the
index is not selective enough (many rows are read but few are actually used).
- `index_size`: Large unused indexes are particularly wasteful and should be prioritized for removal.

## WAL buffer analysis

Write-Ahead Logging (WAL) is critical for database durability and performance. The WAL buffer holds
WAL data in memory before it's written to disk. Monitoring WAL buffer usage helps identify write
performance issues and configuration problems.

### WAL statistics overview

The following query provides a comprehensive view of WAL activity and buffer performance:

```sql
SELECT
    wal_records,
    wal_fpi AS full_page_images,
    round((wal_bytes / 1024.0 / 1024.0 / 1024.0 /
           NULLIF(EXTRACT(EPOCH FROM (now() - stats_reset)) / 3600.0, 0))::numeric, 2) AS wal_gb_per_hour,
    wal_buffers_full,
    stats_reset
FROM pg_stat_wal;
```

Key metrics to monitor:
- `wal_buffers_full`: Number of times WAL data was written because the buffer was full. This should
ideally be 0. If this value is greater than 0, consider increasing the `wal_buffers` configuration
parameter (default is typically 16MB).
- `wal_gb_per_hour`: Average rate of WAL generation in GB per hour. This metric helps with capacity
planning, estimating disk usage, and identifying abnormal write patterns. For example, if you
normally generate 10 GB/hour but suddenly see 50 GB/hour, it indicates a spike in write activity.

## Query analysis

You can also analyze the performance of specific queries by using the `EXPLAIN ANALYZE` command, which provides a detailed execution plan and performance metrics for a given SQL statement. For example:

```sql
EXPLAIN ANALYZE SELECT COUNT(*)
FROM AUDIT_LOG
WHERE CATEGORY = 'USER_TASKS'
```

Analysing the result may require some knowledge of how the database executes queries, but it can
provide valuable insights into potential performance bottlenecks, such as missing indexes,
inefficient query plans, or excessive disk I/O. Most often, this can be analysed by an AI assistant,
which can provide recommendations on how to optimize the query, for example by adding indexes,
rewriting the query, or changing the database configuration.

## Read Query benchmarking

In our common load-tester, we have a read query performance benchmark built-in. It executes a
predefined set of queries against the database and measures their execution time, which can be used
to track performance over time and identify potential regressions. You can find the implementation
of the read query benchmark in the `DataReadMeterQueryProvider` class in the `load-tester` module.

The measured results of the executed queries are reported as regular Prometheus metrics and are
visualized in Grafana.

These read benchmarks can influence the performance of the load-test, so they are not executed automatically on each load-test and need to be activated with the following load-test property in the load-test workflow:

```
--set app.performReadBenchmarks=true
```

