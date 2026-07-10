# Architecture Decision Records — analytics

Module-scoped ADRs for the Optimize analytics pipeline (`analytics/analytics-engine`,
`analytics/event-bridge-analytics`, `analytics/analytics-webapp`). See the repo-wide
`docs/adr/README.md` for the tier structure and the streaming library's own
`event-bridge/event-bridge-streaming/docs/adr/` for runtime-level decisions this builds on.

## Index

- [0001 — Declarative datasets as the source of truth](0001-declarative-datasets.md)
- [0002 — Analytics engine code structure](0002-engine-structure.md)
- [0003 — Analytics engine layering and the operator model](0003-engine-layering.md)
- [0004 — Cube read path: prune, stream, and push down aggregation](0004-cube-read-path.md)
- [0005 — Runtime dataset provisioning and live activation](0005-runtime-dataset-provisioning.md)
- [0006 — Reports over multiple datasets](0006-multi-dataset-reports.md)
- [0007 — Stable source coordinates: dedup the Zeebe stream before the fold](0007-stable-source-coordinates.md)
- [0008 — Byte-backed dimension keys: the key is its serialized form](0008-byte-backed-dimension-keys.md)
- [0009 — The cube is the unit: single-writer composite cells](0009-single-writer-composite-cells.md)
- [0010 — Periodic snapshots: balance-over-time for additive cubes](0010-periodic-snapshots.md)

