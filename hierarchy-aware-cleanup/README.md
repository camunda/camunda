# Hierarchy aware cleanup tool

This tool has the purpose to fill a gap in the new history cleanup mechanism that would cause too
much tech debt in the core product.

## What is this gap?

The gap is that process instances being created before the upgrade to 8.9 will not contain the field
`rootProcessInstanceKey` in all of their records. This field determines which root process instance
a process instance belongs to. This information can then be used to archive the record once the root
process instance has been completed.

Naturally, no cleanup would happen to records that are missing this information. The existing gap
can be closed by using this tool.

## How does it work?

The tool has 2 main features: Stale root process instance detection and orphan detection and deletion.

The assumption is that retention is enabled. The default retention policy configured to the
orchestration cluster is applied as `camunda.cleanup.retention-policy` (default `P30D`). An
additional buffer can be
added (so that we do not interfere with the actual cleanup) with
`camunda.cleanup.retention-buffer` (default `P1D`).

Together, they form the `relaxedRetentionPolicy`.

### Stale root process instance detection

The application searches for process instances that:

- have no parent process instance
- are in the state `COMPLETED` or `TERMINATED`
- the end date is lower than `OffsetDateTime.now() - relaxedRetentionPolicy`

All matches qualify for cleanup as they should not be in place anymore.

As a consequence:

- the root process instance is deleted
- all children are treated as orphans

### Orphan detection and deletion

The application searches for process instances that:

- have a parent process instance
- are in the state `COMPLETED` or `TERMINATED`
- the end date is lower than `OffsetDateTime.now() - relaxedRetentionPolicy`

Then, each of the results is checked for the existence of the parent process instance.

If the parent is absent, all children of the missing parent process instance are treated as orphans.

How orphans are treated:

- the orphan process instance is deleted
- all children are treated as orphans

