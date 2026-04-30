# Load Test Configurations — 500 PI/s Realistic BPMN

All runs use the `camunda-load-test` GitHub Actions workflow with the `realistic` scenario
(`bankCustomerComplaintDisputeHandling.bpmn`) and no Optimize, targeting 500 PI/s.

---

## Base Configuration (defaults from `camunda-platform-values.yaml`)

### Zeebe Broker
| Parameter | Default Value | Notes |
|---|---|---|
| `clusterSize` | 3 | Number of broker pods |
| `partitionCount` | 3 | Parallel processing streams |
| `replicationFactor` | 3 | Each partition copied to all 3 brokers |
| `cpuThreadCount` | 3 | Command processing threads per broker |
| `ioThreadCount` | 3 | Exporter (ES write) threads per broker |
| `resources.requests.cpu` | 3000m | CPU per broker pod |
| `resources.limits.cpu` | 3000m | CPU per broker pod |
| `resources.requests.memory` | 2Gi | Memory per broker pod |
| `flowControl.write.limit` | 10000 | Max records/s before flow control throttles |
| `consistencyChecks` | enabled | Disabled via override file in stress runs |

### Elasticsearch
| Parameter | Default Value |
|---|---|
| `replicaCount` | 3 |
| `heapSize` | 3g |
| `resources.requests.cpu` | 7000m |
| `resources.limits.cpu` | 7000m |
| `resources.requests.memory` | 8Gi |

### Workers (from realistic scenario remote values)
All worker types default to **1 replica**, **capacity 30**, **300ms completion delay**.

| Worker Type | Replicas | Capacity | Completion Delay |
|---|---|---|---|
| customer-notification | 1 | 30 | 300ms |
| extract-data-from-document | 1 | 30 | 300ms |
| dispute-process-request-proof-from-vendor | 1 | 60 | 300ms |
| dispute-process-request-get-vendor-info | 1 | 30 | 10ms |
| refunding | 1 | 30 | 300ms |
| inform-about-successful-claim | 1 | 30 | 300ms |

---

## Run 1 — Baseline (3 partitions, default broker config)

**Workflow inputs:**
| Input | Value |
|---|---|
| `scenario` | `realistic` |
| `enable-optimize` | `false` |
| `additional_load_test_configuration` | `--set starter.rate=500` |
| `platform-helm-values` | `--set-file 'orchestration.extraConfiguration[1].content=./camunda-platform-override-values.yaml'` |

**Changes from base:**
- Consistency checks disabled (via override file)
- Starter rate overridden to 500 PI/s
- Everything else at defaults

**Results:**
- Actual sustained PI/s: ~0 (crashed immediately)
- Backpressure limit: oscillated at 15–40 (never recovered)
- System stability: unstable — repeated leader elections, exported records dropped to 0
- ES: green, heap at 40%, no rejections — not the bottleneck
- Root cause: 3 partitions × serial processing unable to handle ~50K commands/s from realistic BPMN

---

## Run 2 — 6 Partitions, Higher Broker CPU

**Workflow inputs:**
| Input | Value |
|---|---|
| `scenario` | `realistic` |
| `enable-optimize` | `false` |
| `additional_load_test_configuration` | `--set starter.rate=500` |
| `platform-helm-values` | `--set-file 'orchestration.extraConfiguration[1].content=./camunda-platform-override-values.yaml' --set core.partitionCount=6 --set core.cpuThreadCount=6 --set core.resources.requests.cpu=6000m --set core.resources.limits.cpu=6000m` |

**Changes from base:**
- `partitionCount`: 3 → 6 (doubles parallel processing streams)
- `cpuThreadCount`: 3 → 6 (matches new partition count)
- Broker CPU: 3000m → 6000m per pod (gives extra threads actual compute)
- Consistency checks disabled

**Results:**
- Actual sustained PI/s: ~86 (peak), system still ramping
- Backpressure limit: started at 100 (fully open), brief drop to ~20 during startup, recovered to 75–100
- System stability: much more stable, one leader election spike at startup (leader balancer restarting 5×)
- ES: green, 664 ops/s indexing, no rejections — not the bottleneck
- New bottleneck identified: workers — 22 completions/s vs 328 creations/s

---

## Run 3 — 6 Partitions + Scaled Workers

**Workflow inputs:**
| Input | Value |
|---|---|
| `scenario` | `realistic` |
| `enable-optimize` | `false` |
| `additional_load_test_configuration` | `--set starter.rate=500 --set workers.customer-notification.replicas=5 --set workers.extract-data-from-document.replicas=5 --set workers.dispute-process-request-proof-from-vendor.replicas=5 --set workers.dispute-process-request-get-vendor-info.replicas=5 --set workers.refunding.replicas=5 --set workers.inform-about-successful-claim.replicas=5` |
| `platform-helm-values` | `--set-file 'orchestration.extraConfiguration[1].content=./camunda-platform-override-values.yaml' --set core.partitionCount=6 --set core.cpuThreadCount=6 --set core.resources.requests.cpu=6000m --set core.resources.limits.cpu=6000m` |

**Changes from Run 2:**
- All worker types scaled from 1 → 5 replicas

**Results (in progress):**
- Actual sustained PI/s: ~58 at last reading
- Backpressure limit: started at 100, settled at 50–60 (more stable than Run 1)
- Job completion gap unchanged: 25/s completed vs 348/s created
- ES: green, 360 ops/s indexing, queue count 0, no circuit breakers
- Open question: partition count change effectiveness unconfirmed (dashboard shows only 3 partition lines)

---

## Summary

| Run | Partitions | Broker CPU | Worker Replicas | Peak PI/s | System Stable? |
|---|---|---|---|---|---|
| Run 1 | 3 | 3000m | 1 | ~0 | No — leader elections |
| Run 2 | 6 | 6000m | 1 | ~86 | Mostly — startup instability |
| Run 3 | 6 | 6000m | 5 | ~58 | Partly — backpressure at 50–60 |

**ES is not the bottleneck in any run.** The ceiling has been Zeebe processing capacity (partitions) and worker job completion throughput.
