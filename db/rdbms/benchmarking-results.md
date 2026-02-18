# RDBMS Benchmarking results

## Methodology

- executed on the normal `zeebe-io` load-test cluster
- when not specified otherwise, the default settings of the load-tests were used:
  - 3 partitions and 3 nodes
  - camunda broker node with 3.5 CPU and 2GB RAM
  - PostgreSQL database with 3-6 CPU and 6GB RAM
    - Bitnami image with PostgreSQL 18.4
    - postgreSQL configured with
      - `wal_buffers` = 64MB
      - `max_wal_size` = 4GB
      - `min_wal_size` = 1GB
      - `checkpoint_timeout` = 20min
      - `checkpoint_completion_target` = 0.9
      - `wal_writer_delay` = 200ms
      - `wal_writer_flush_after` = 1MB
      - `shared_buffers` = 2GB
      - `effective_cache_size` = 4500MB
      - `work_mem` = 32MB
      - `maintenance_work_mem` = 512MB
      - `autovacuum_max_workers` = 6
      - `autovacuum_naptime` = 15s
      - `autovacuum_vacuum_scale_factor` = 0.03
      - `autovacuum_analyze_scale_factor` = 0.02
      - `autovacuum_vacuum_cost_limit` = 5000
    - 128GB disk with storage class `ssd`
- a default configured historyTTL of 1 hour was used for all benchmarks
- benchmarks are started via the Camunda GitHub action `load-test`

## Scenarios

**General notes**

- the RDBMS exporter flushes latest every 500ms, so the data availability latency is not real-time,
  but always somewhere between 500-800ms. Due to the transactional nature of an RDBMS, the data is
  always directly available for reading after a flush/commit.
- the history cleanup runs periodically in the same thread as the main exporter and blocks it while
  running. This may lead to spikes in the data availability latency.
- We still see some unwanted changes in the load and metrics even with a stable load. This can be
  caused by the shared resource `cluster` and also changes in the execution planner and caching
  inside of PostgreSQL. The given numbers here are based on the most stable runs.

### Simple / Max

- a process with just a single task
- by default 300 process-instances per second are started

#### Results:

- With 300 PI/s started, the RdbmsExporter was not able to keep up in exporting. The engine quickly
  ran into backpressure and the throughput dropped to around 200 PI/s.
- With 200 PI/s started, the RdbmsExporter was able to keep up and the throughput stayed at around 200 PI/s.
  - the exporterUsage `flush` was constantly around 35%
  - the exporterUsage `history` was constantly around 1-2%
  - largest tables (by rows) after 1 hour were `AUDIT_LOG` (11.5M) and `VARIABLE` (10.8M)
  - history cleanup happened every 16s and took 250-300ms each run
  - a process-instance took ~13KB of disk space
  - auditLog:
    - the SQL statements for the `AUDIT_LOG` table (`INSERT` and `DELETE`) consumed around 40% of all write database time
    - the audit-log table consumed 4.7GB of 9GB disk space

### Typical

- a process with ten tasks
- by default 50 process-instances per second are started

#### Results:

- With 50 PI/s started, the RdbmsExporter was able to keep up and the throughput. Even 70 PI/s could
  be sustained for a longer time, but sometimes the engine itself ran into backpressure without the
  exporter being overloaded. The following numbers are based on the 50 PI/s run:
  - the exporterUsage `flush` was constantly around 17-18%
  - the exporterUsage `history` was constantly around 1%
  - largest tables (by rows) after 1 hour were `AUDIT_LOG` (3M) and `VARIABLE` (2.8M)
  - history cleanup happened every 64s and took ~300ms each run
  - a process-instance took ~22KB of disk space
  - auditLog:
    - the SQL statements for the `AUDIT_LOG` table (`INSERT` and `DELETE`) consumed around 30% of all write database time
    - the audit-log table consumed 1.2GB of 3.9GB disk space

### Realistic

- a more complex process with sub-processes, timers and DMN
- a single process-instance per second is started, but spawns 50 sub-processes

#### Results:

- With 1 PI/s started, the RdbmsExporter was able to keep up and the throughput.
  - the exporterUsage `flush` was constantly around ~13%
  - the exporterUsage `history` was constantly around 1%
  - largest tables (by rows) after 1 hour were `AUDIT_LOG` (3M) and `VARIABLE` (2.8M)
  - history cleanup happened every 64s and took ~300ms each run
  - auditLog:
    - the SQL statements for the `AUDIT_LOG` table (`INSERT` and `DELETE`) consumed around 28% of all write database time
    - the audit-log table consumed 1.2GB of 3GB disk space

#### Results with HistoryTTL of 24H:

- With 1 PI/s started, the RdbmsExporter was able to keep up and the throughput.
  - the exporterUsage `flush` was constantly around ~14%
  - the exporterUsage `history` was constantly around 1-2%
  - largest tables (by rows) after 1 hour were `AUDIT_LOG` (62M) and `VARIABLE` (57M)
  - history cleanup happened every 64s and took ~500ms each run
  - a process-instance took ~18KB of disk space
  - a root process-instance including sub-processes took ~897KB of disk space
  - auditLog:
    - the SQL statements for the `AUDIT_LOG` table (`INSERT` and `DELETE`) consumed around 27% of all write database time
    - the audit-log table consumed 29GB of 74GB disk space

### Scalability scenarios

All the three scenarios were also executed with more than 3 nodes/partitions:
- 6 nodes and 6 partitions
  - `simple`: 400 PI/s ran stable. Everything more ran into backpressure.
  - `typical`: 100 PI/s ran stable.
  - `realistic`: 2 PI/s ran stable.
- 12 nodes and 12 partitions
  - `simple`: 800 PI/s ran more or less stable. Sometimes spikes were visible, but overall the exporter could keep up.
  - `typical`: 200 PI/s ran stable.
  - `realistic`: 4 PI/s ran stable.
- the database was not scaled up and had the same setup as described in the methodology section

**Conclusion**:
- The exporting throughput was more or less linear

### Experimental scenario: Database partitioning

In a proof-of-concept, I also tested partitioning the most relevant tables on database level by our
`partitionId`. I tested it to up to 6 DB partitions. The results were not promising, as the
partitioning itself added overhead and the performance was not better if even a bit worse.

## Read performance

In a yet-to-merge experiment I conducted query benchmarks against a `realistic` scenario with a historyTTL of 24 hours:
- the queries were executed using the `camundaClient` and the `OC API` from within the cluster but a different deployment ( the `starter`)
- all queries executed with a primary key or other very selective index value were very fast with ~10-15ms
- statistics queries like `processDefinitionStatistics` or `processDefinitionElementStatistics` were **much** slower with increasing amount of data (p99 sometimes at >10s)
- every query with a poor selectivity was very slow (p99 at >10s)

The slow queries can possibly be fixed with better indexing and more cache memory, but it is clear
that the read performance of the RDBMS exporter is not good with a large amount of data and needs to be improved further.

## Potential performance optimization

- async history-cleanup: put the history-cleanup in a separate actor and don't block the main
  exporter with this. The overall load will not be reduced, just made more concurrent.
- async flush: put the actual DB-flush in a separate actor/thread and fill the next batch in the
  mean-time. The overall load will not be reduced, just made more concurrent.
- special statistics-read-models: Maintain separate tables for the main statistics for faster read
  performance. Due to a lot of filter options even in the statistics queries, this might have
  limited use. But for some default queries (e.g. without any filter, when an Operate screen is just
  opened), it still can help a lot.
- Database setup: I have tested all this with a single-node k8s-containerized PostgreSQL database (6
  CPU, 8GB memory) and optimized the configuration with what copilot told be to configure. There is
  definitely room for improvement when we talk to real DB-Admins

## TL;DR

- RdbmsExporter is roughly expected to be at ~70% of ElasticSearch performance on writes
- RdbmsExporter is much slower in read performance in Operate
- Further optimizations come with a cost
