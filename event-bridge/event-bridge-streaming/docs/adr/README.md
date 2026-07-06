# Architecture Decision Records — event-bridge-streaming

Module-scoped ADRs for the streaming library. See the repo-wide `docs/adr/README.md` for the tier
structure.

## Index

- [0001 — Scope of materialized tables and richer joins](0001-materialized-tables-and-richer-joins.md)
- [0002 — Rebalance handoff for sharded partition state](0002-rebalance-state-handoff.md)
- [0003 — Parallel per-partition processing with a decode-ahead pipeline](0003-decoupled-fetch-decode-pipeline.md)
  (processing model superseded by 0004)
- [0004 — Actor-per-partition processing with async sink commits](0004-actor-per-partition-processing.md)
