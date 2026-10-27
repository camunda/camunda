# Draft comments for camunda/pdp-3517-variable-trace#53

Three comments, to be posted after review. Marks: 🟢 under 10% worse, 🟡 10% to 50% worse, 🔴 more than 50% worse or a health limit broken. All raw numbers: [variable-history-poc-results.md](variable-history-poc-results.md).

---

## Comment 1: What it costs to record every variable change with its value

**In one sentence:** the engine does not get slower, but secondary storage needs about 2.4 times the space and 60% to 80% more CPU, and data shows up in Operate about 1 second later.

**What we tested**

- A PoC that writes every variable change, with its full value, to its own index (Elasticsearch) or table (PostgreSQL).
- The same cluster as our standard load tests: 3 brokers, 3 Elasticsearch nodes or 1 PostgreSQL database.
- A realistic process with large values: about 765 variable changes and 2.4 MB of values per process instance. Each instance starts 50 sub-processes.
- 0.5 process instances per second, the highest rate where today's cluster is fully healthy with these large values.
- Today (nothing recorded) and full tracing (everything recorded), side by side for one hour.

**Elasticsearch**

|               What we measured                |   Today   | Full tracing |  Change  |    |
|-----------------------------------------------|-----------|--------------|----------|----|
| Process instances finished per second         | 0.50      | 0.50         | none     | 🟢 |
| Requests rejected by the engine               | 0%        | 0%           | none     | 🟢 |
| Engine processing time (p99)                  | 0.22 s    | 0.20 s       | none     | 🟢 |
| Time until data shows in Operate (p99)        | 2.5 s     | 3.8 s        | +1.3 s   | 🔴 |
| Elasticsearch CPU                             | 1.4 cores | 2.3 cores    | +58%     | 🔴 |
| Storage used after 75 minutes                 | 4.6 GB    | 11.2 GB      | x2.4     | 🔴 |
| Archiving: process instances moved per second | 0.50      | 0.50         | keeps up | 🟢 |
| Archiving: time per run                       | 0.60 s    | 0.93 s       | +57%     | 🔴 |

**PostgreSQL**

|             What we measured              |              Today              |        Full tracing        |             Change             |    |
|-------------------------------------------|---------------------------------|----------------------------|--------------------------------|----|
| Process instances finished per second     | 0.50                            | 0.50                       | none                           | 🟢 |
| Requests rejected by the engine           | 0%                              | 0%                         | none                           | 🟢 |
| Engine processing time (p99)              | 0.18 s                          | 0.23 s                     | +28%                           | 🟡 |
| Time until data shows in Operate (p99)    | 0.99 s                          | 1.25 s                     | +26%                           | 🟡 |
| Short stalls (data late by more than 5 s) | 1 in 3.5 hours (infrastructure) | 1 in 3.5 hours, up to 29 s | rare                           | 🟡 |
| PostgreSQL CPU                            | 0.14 cores                      | 0.26 cores                 | +83% (small in absolute terms) | 🔴 |
| Database size                             | 0.57 GB                         | 1.35 GB                    | x2.4                           | 🔴 |
| Data written to disk (WAL)                | 1.8 MB/s                        | 3.7 MB/s                   | x2                             | 🔴 |
| Cleanup: time per run                     | 0.6 s                           | 2.8 s                      | x4.4                           | 🔴 |
| Broker CPU                                | 1.27 cores                      | 1.39 cores                 | +10%                           | 🟡 |

**What this means**

- Process automation itself is not affected at this load. Customers will not see slower process instances.
- Operate gets slower to show new data: about 1 second on Elasticsearch, a quarter second on PostgreSQL, with rare longer stalls on PostgreSQL.
- Secondary storage pays the bill: more CPU, more than twice the space, twice the disk writes on PostgreSQL.
- Archiving and cleanup still keep up, but each run takes longer. They would be the first to fall behind at higher load.

**Price tag for a workload like ours** (2.4 MB of variable values per process instance)

- About 2.8 MB more Elasticsearch storage per process instance (with one replica), or 2.8 TB per million process instances kept.
- About 1.9 MB more PostgreSQL storage per process instance, or 1.9 TB per million process instances kept.
- About 0.8 more Elasticsearch CPU cores per 400 recorded changes per second.

<details><summary>For engineers</summary>

- PoC branch `bcan/53-variable-history-poc`, one image for all runs, a switch for the recorded share (`camunda.data.audit-log.variable-history-percent`).
- Index and table: one record per `CREATED` or `UPDATED` variable record, keyed by partition and position. Searchable: name, scope key, process instance key, root process instance key. Value preview and full value stored, not indexed. Archived with the process instance (Elasticsearch), deleted with the root process instance (PostgreSQL).
- Engine changes are tagged following the proposed list in #3.
- Write throttling was on, as in all load tests. It never kicked in: the exporter backlog stayed small in all runs.
- Windows, namespaces, Grafana and metrics workflow links, and every number: [variable-history-poc-results.md](variable-history-poc-results.md), Step 1.

</details>

---

## Comment 2: Does more hardware help?

**In one sentence:** yes, mostly. Twice the Elasticsearch nodes brings the cluster back close to today. On PostgreSQL a bigger database machine halves the extra delays, but the extra disk writes and the extra space stay.

**What we tested**

- Full tracing, same load as above, with more storage hardware.
- Elasticsearch: 5 nodes (+67%) and 6 nodes (+100%), with the history spread over all nodes.
- PostgreSQL: 1.5 and 2 times the CPU, memory and disk.
- The engine was not given more hardware: tracing does not change its load.

**Elasticsearch**

|              What we measured              | Today, 3 nodes | Full tracing, 3 nodes | Full tracing, 5 nodes | Full tracing, 6 nodes |
|--------------------------------------------|----------------|-----------------------|-----------------------|-----------------------|
| Time until data shows in Operate (typical) | 2.5 s          | 3.9 s 🔴              | 3.3 s 🟡              | 2.5 s 🟢              |
| Time until data shows in Operate (p99)     | 3.0 s          | 3.8 s 🟡              | 3.4 s 🟡              | 3.2 s 🟢              |
| Export delay (p99)                         | 0.47 s         | 0.60 s 🟡             | 0.48 s 🟢             | 0.48 s 🟢             |
| Archiving: time per run                    | 0.59 s         | 0.95 s 🔴             | 0.57 s 🟢             | 0.55 s 🟢             |
| CPU limit reached (throttling)             | 0.6%           | 5.2% 🟡               | 0.4% 🟢               | 0.2% 🟢               |

**PostgreSQL**

|            What we measured            |  Today   | Full tracing, 3 cores | Full tracing, +50% | Full tracing, +100% |
|----------------------------------------|----------|-----------------------|--------------------|---------------------|
| Export delay (p99)                     | 0.22 s   | 0.75 s 🔴             | 0.49 s 🔴          | 0.47 s 🔴           |
| Cleanup: time per run                  | 0.54 s   | 2.43 s 🔴             | 0.91 s 🔴          | 0.93 s 🔴           |
| Data written to disk (WAL)             | 1.8 MB/s | 3.9 MB/s 🔴           | 3.8 MB/s 🔴        | 3.8 MB/s 🔴         |
| Time until data shows in Operate (p99) | 0.99 s   | 1.00 s 🟢             | 1.00 s 🟢          | 1.00 s 🟢           |

**What this means**

- On Elasticsearch, more nodes absorb tracing. With twice the nodes, a typical update shows up as fast as today.
- On PostgreSQL, the first 50% more hardware helps; another 50% adds nothing. The rest is the extra data itself, which no hardware removes.
- Space needs grow with the data, not with the hardware: plan for about 2.4 times the storage in both cases.

<details><summary>For engineers</summary>

- The hardware runs are compared with the 3-node and 3-core runs in the same hour. They are younger, so a rare stall is less likely to show up in them (1 h 15 min against 3 h 50 min of life).
- Elasticsearch history index with one primary shard per node (`number-of-shards-per-index.variable-history`). PostgreSQL `max_wal_size` kept at 4 GB, so only hardware changed.
- All numbers: [variable-history-poc-results.md](variable-history-poc-results.md), Step 2.

</details>

---

## Comment 3: What if only part of the changes are recorded?

**In one sentence:** recording a third or two thirds of the changes costs little; the big jump comes with the last third. In our workload, user and client changes are only 13% of the data, so the selective defaults are cheap.

**What we tested**

- The same load, with 0%, 33%, 66% and 100% of the variable changes recorded, all in the same hour.
- This replaces separate runs for "users only" and "users and clients": our load tests have no users, and the share tells us the cost of any mix.

|                   What we measured                    |     0%     |    33%    |    66%    |         100%          |
|-------------------------------------------------------|------------|-----------|-----------|-----------------------|
| Elasticsearch CPU                                     | 1.45 cores | +7% 🟢    | +10% 🟡   | +56% 🔴               |
| Time until data shows in Operate, Elasticsearch (p99) | 2.5 s      | 3.0 s 🟡  | 2.5 s 🟢  | 4.0 s 🔴              |
| Archiving: time per run                               | 0.62 s     | +5% 🟢    | none 🟢   | +65% 🔴               |
| PostgreSQL data written to disk (WAL)                 | 1.8 MB/s   | +26% 🟡   | +72% 🔴   | x2 🔴                 |
| PostgreSQL cleanup: time per run                      | 0.57 s     | +26% 🟡   | +54% 🔴   | x4.3 🔴               |
| PostgreSQL export delay (p99)                         | 0.22 s     | 0.33 s 🟡 | 0.47 s 🔴 | 6.6 s in this hour 🔴 |

**Who makes the changes (full tracing, our workload)**

- The engine: 87% of the recorded data. Most of it is one multi-instance result list, rewritten on each of its 50 iterations (56% of all data).
- Clients (job workers, process starts): 13%.
- Users: 0%, because our load tests have no users.

**What this means**

- "Users only", the default, costs close to nothing for a workload like ours.
- "Users and clients" records about 13% of the data, less than our 33% run: small CPU and latency cost, about 15% to 20% more storage than today.
- Recording engine changes is what makes tracing expensive. Growing result lists are the main reason: each change stores the whole list again.

<details><summary>For engineers</summary>

- The share is picked by a hash of the variable key, so a recorded variable keeps all its changes. Measured shares: 33% and 34%, 65% and 69% of the bytes.
- A shared infrastructure stall at 23:10 UTC (also hit a baseline run) is excluded: the tables use 22:15 to 23:05 UTC.
- The PostgreSQL 100% export delay in that hour includes one stall around a forced checkpoint; over 3.5 hours it had one 5-minute slice above 5 s.
- All numbers: [variable-history-poc-results.md](variable-history-poc-results.md), Step 3.

</details>

---

## Not covered

- OpenSearch, other RDBMS vendors and higher loads closer to capacity.
- Write throttling off: not needed, it never kicked in.
- Deleting archived Elasticsearch indices (1-day retention, an index drop).
- Customer payloads: values per process instance differ a lot between customers; costs scale with the amount of recorded data.
- Runs of several days.

