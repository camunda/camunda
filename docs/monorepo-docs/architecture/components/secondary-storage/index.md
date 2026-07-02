---
title: Secondary Storage
---

# Secondary Storage

Camunda 8 supports two secondary storage backends: Elasticsearch/OpenSearch (ES/OS) and relational
databases (RDBMS). This section documents how to work with, operate, and extend the secondary
storage layer.

## Guides

- [Working with Secondary Storage](./working-with-secondary-storage.md) — performance checklist,
  schema field-type guidance, query best practices, and common pitfalls covering ES/OS and RDBMS.
- [Archiving](./archiving.md) — how completed data is moved from active to dated archive indices in ES/OS.
- [Backup and Restore](./backup-and-restore.md) — snapshot strategy for ES/OS deployments and RDBMS backup guidance.

## RDBMS

The [RDBMS subsection](./rdbms/rdbms_architecture_docs.md) covers the relational database module:
architecture, developer guide, benchmarking, and architectural decision records.
