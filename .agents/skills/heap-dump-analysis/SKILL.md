---
name: heap-dump-analysis
description: Analyze Camunda 8 / Zeebe broker heap dumps (.hprof). Use for a broker OOM, OutOfMemoryError, heap growth, or finding what fills the heap.
---

# Heap dump analysis

Headless Eclipse MAT on broker heap dumps, as we did for a DRG cache OOM on ten 8.9.23
brokers. The starter pack and the lessons come from that incident; for another cause,
build ad-hoc queries the same way. You read aggregates of the results, never the heap.

## Privacy rules (read first)

A heap dump holds customer data. Nothing from it may reach the LLM context except
aggregates. Details: [privacy.md](references/privacy.md).

- Everything MAT writes goes under `raw/`. Never read or print it.
- Take only numbers and class names out of the CSVs, with one-liners that print aggregates.
- A query that must read a String is named `raw_*`; hash its values with `scripts/hash_ids.sh`.
- Subagents return aggregates only. Never upload or commit dumps or outputs.

## Files

| File | What it holds |
|------|---------------|
| `scripts/run_mat.sh` | Runs MAT on one dump with query packs. Prints rc, seconds, problems, CSV names. |
| `scripts/hash_ids.sh` | Hashes the ids in a `raw_*` CSV with a salt. Prints hashes only. |
| `queries/starter_pack.xml` | Any OOM: heap totals, dominators, engine state per partition. |
| `queries/drg_cache_pack.xml` | DRG cache and process cache. Add it when `DeployedDrg` or `DbDecisionState` dominates. |
| [references/privacy.md](references/privacy.md) | Safe extraction, hashing ids. |
| [references/queries.md](references/queries.md) | MAT/OQL pitfalls, reading the heap, one-liners, truncated dumps. |
| [references/mat-headless.md](references/mat-headless.md) | Install, launch flags, index files, sizing, parallel runs. |
| [references/interpretation.md](references/interpretation.md) | The five questions and this incident as an example. |

## Workflow

1. **Collect facts.** Camunda version, pod memory limit, JVM flags, pod logs around the OOM.
   Our brokers had no `-Xmx`, so the max heap was 25% of the pod limit.
2. **Check names for the version** with `git show <tag>:<path>`
   ([queries.md](references/queries.md)).
3. **Pilot one dump.**
   ```bash
   OUT_DIR=~/incidents/<id>/mat-out .agents/skills/heap-dump-analysis/scripts/run_mat.sh <dump>.hprof
   ```
   `OUT_DIR` is required, outside any repo. Each pack needs `rc=0` and `problems=0`. A
   query without a CSV then had zero rows, or its class name is wrong for this version.
   Check the first-parse log for truncation ([queries.md](references/queries.md)).
4. **Run the others in parallel** ([mat-headless.md](references/mat-headless.md)), extract
   numbers ([queries.md](references/queries.md)), and answer the five questions in
   [interpretation.md](references/interpretation.md).

## Rules

- Never run two analyses of the same dump at the same time.
- Never change the `.hprof`. Truncated dumps are normal; say so in the report.
- Delete dumps, MAT indexes and outputs when the incident is closed.
