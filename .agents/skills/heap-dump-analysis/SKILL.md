---
name: heap-dump-analysis
description: Analyze Camunda 8 / Zeebe broker heap dumps (.hprof) with Eclipse MAT. Use for a broker OutOfMemoryError, an OOMKilled pod, heap growth, or finding what fills the heap. Not for memory outside the heap.
---

# Heap dump analysis

Find what fills a broker's heap from its heap dumps with headless Eclipse MAT, and write a
report. You read aggregates of the results, never the heap. The lessons come from one DRG
cache incident, the worked example in [interpretation.md](references/interpretation.md). For
another cause, write ad-hoc queries the same way.

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
| `scripts/run_mat.sh` | Runs MAT on one dump with query packs. Prints rc, seconds, problems, truncation, CSV names. |
| `scripts/hash_ids.sh` | Hashes the ids in a `raw_*` CSV with a salt. Prints hashes only. |
| `scripts/count_threads.sh` | Counts threads per stack pattern. Never prints stack lines. |
| `queries/starter_pack.xml` | Any OOM: heap totals, dominators, engine state per partition. |
| `queries/drg_cache_pack.xml` | DRG cache and process cache. Add it when `DeployedDrg` or `DbDecisionState` dominates. |
| `queries/raw_drg_ids_pack.xml` | DRG ids, to hash with `hash_ids.sh`. Never read its output. |
| [references/privacy.md](references/privacy.md) | Safe extraction, hashing ids. |
| [references/queries.md](references/queries.md) | MAT/OQL pitfalls, reading the heap, the CSV format, truncated dumps. |
| [references/mat-headless.md](references/mat-headless.md) | Install, launch flags, index files, sizing, parallel runs. |
| [references/interpretation.md](references/interpretation.md) | How to answer the five questions, and the DRG cache incident as an example. |

## Checklist

Copy it into your notes and tick each step.

```
- [ ] 1. Facts collected
- [ ] 2. Class and field names checked for the version
- [ ] 3. Pilot dump: every pack rc=0 and problems=0
- [ ] 4. Truncation noted for every dump
- [ ] 5. Other dumps run in parallel, numbers extracted
- [ ] 6. Five questions answered, each with a number
- [ ] 7. Report written from the template
```

1. **Facts.** Camunda version, pod memory limit, JVM flags, pod logs around the OOM. The max
   heap is `-Xmx`, otherwise the pod limit x `-XX:MaxRAMPercentage` (default 25%).
2. **Names.** The packs query engine classes and fields by name, such as
   `DbDecisionState.drgCache`. Check that they exist on the customer's tag with
   `git show <tag>:<path>` ([queries.md](references/queries.md)).
3. **Pilot one dump.**
   ```bash
   OUT_DIR=~/incidents/<id>/mat-out .agents/skills/heap-dump-analysis/scripts/run_mat.sh <dump>.hprof
   ```
   `OUT_DIR` is required, outside any repo. Set `MAT_XMX` to about 4x the dump size
   ([mat-headless.md](references/mat-headless.md)). If `DeployedDrg` or `DbDecisionState` dominates
   `histogram` or `top_dominators`, rerun with `queries/drg_cache_pack.xml` after the dump.
   A query without a CSV had zero rows, or its class name is wrong for this version.

   **Fix loop.** When a pack has `rc` other than 0 or `problems` above 0, move each suspect
   query into its own pack with its own `filename_suffix`, fix the one that fails (pitfalls
   in [queries.md](references/queries.md)), and rerun until every pack has `rc=0` and
   `problems=0`.
4. **Truncation.** `run_mat.sh` prints `truncated=N`; 0 means the dump is complete. Note it
   per dump. `unknown` means the first parse ran elsewhere
   ([mat-headless.md](references/mat-headless.md)).
5. **Run the others in parallel** ([mat-headless.md](references/mat-headless.md)) and extract
   numbers ([queries.md](references/queries.md)).
6. **Answer the five questions**, each with a number. How:
   [interpretation.md](references/interpretation.md).
   1. Was the heap full? Reachable heap against the max heap.
   2. What dominates? Top classes and dominators by retained size, as a share of the heap.
   3. Who owns it? The owner of each top dominator, and its partition.
   4. What were the threads doing? Threads per stack pattern.
   5. Is it the same on every broker? One row per dump; ids compared by hash.
7. **Write the report** from the template below.

## Report template

```markdown
# Heap dump analysis: <what fills the heap>

- Dumps: <n> from <brokers>, Camunda <version>
- Max heap: <size> (<-Xmx, or pod limit x MaxRAMPercentage>)
- Truncated dumps: <n> of <total>

| Dump | Reachable heap | Top dominator class | Retained | Share | Truncated |
|------|----------------|---------------------|----------|-------|-----------|

## Answers

1. Heap full: <answer with numbers>
2. Dominates: <answer with numbers>
3. Owner: <answer with numbers>
4. Threads: <answer with numbers>
5. Every broker: <answer with numbers>

## What the dumps couldn't tell

## Privacy check

- [ ] No values from the heap and no customer ids; hash prefixes or "DRG A" only
- [ ] No dumps or outputs uploaded or committed
```

**Done when** every checklist step is ticked, each answer has a number, and both privacy
checks are ticked.

## Rules

- Never run two analyses of the same dump at the same time.
- Never change the `.hprof`. Truncated dumps are normal; say so in the report.
- Delete dumps, MAT indexes and outputs when the incident is closed.
