# Archiving

## Concept

Archiving is the process of moving historical data from active ("main") indices into separate
archive ("dated") indices once that data is no longer needed for day-to-day operations. This keeps
primary indices lean, improves query performance, and enables independent lifecycle management
(e.g., different retention policies or ILM/ISM rules) for historical records. It is only active in
ES/OS deployments, with the RDBMS implementation not requiring the equivalent functionality.

Archiving runs as a background activity inside the **Camunda Exporter** (see
[`zeebe/exporters/camunda-exporter`](/zeebe/exporters/camunda-exporter)). It is implemented as a
collection of _archiver jobs_, each responsible for a particular category of data.

## Templated Indices

Many Camunda data entities (process instances, flow node instances, variable updates, etc.) are
stored in _templated_ (rollover) indices. A templated index is a parameterized pattern such as
`operate-list-view-*`, where each concrete index covers a time-window of data. Archiving exploits
this structure: documents belonging to a finished batch are **reindexed** from the active index
into a dedicated archive index (e.g. `operate-list-view-8.3.0_2024-01-01`), an ILM/ISM lifecycle
policy is applied to the archive index, and then the documents are **deleted** from the source.

Documents that are permanently stored in a templated index but are never picked up by an archiver
job (e.g. open start-event subscriptions for a process definition) are not a problem per se—they
simply remain in the active index until they are eventually cleaned up by a dedicated strategy.
Having such "orphan" documents in a templated index is acceptable but should be addressed
explicitly via a purpose-built cleanup strategy when the business logic allows it.

## Archiver Jobs

An _archiver job_ is an implementation of
[
`ArchiverJob`](/zeebe/exporters/camunda-exporter/src/main/java/io/camunda/exporter/tasks/archiver/ArchiverJob.java),
which in turn implements the
[
`BackgroundTask`](/zeebe/exporters/camunda-exporter/src/main/java/io/camunda/exporter/tasks/BackgroundTask.java)
interface and is registered with the
[
`BackgroundTaskManager`](/zeebe/exporters/camunda-exporter/src/main/java/io/camunda/exporter/tasks/BackgroundTaskManager.java)
inside the exporter context. The job is scheduled and rescheduled automatically; the
implementation only needs to describe _what_ to archive and _how_ to identify that batch.

### Core Abstractions

| Class / Interface    | Role                                                                                                                                            |
|----------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| `BackgroundTask`     | Interface for exporter background tasks; exposes `CompletionStage<Integer> execute()` and optional `getCaption()` / `close()` default methods.  |
| `ArchiverJob<B>`     | Abstract base class providing the archive loop: fetch batch → move documents → record metrics.                                                  |
| `ArchiveBatch`       | Represents a single batch of document IDs to be moved, along with the finish date used to name the destination index.                           |
| `ArchiverRepository` | Thin storage-layer abstraction over Elasticsearch/OpenSearch: `moveDocuments`, `reindexDocuments`, `deleteDocuments`, `setIndexLifeCycle`, etc. |

### Existing Archiver Jobs

The following archiver jobs ship out of the box:

| Job                                                   | Primary index               | Trigger / eligibility                                                                                  |
|-------------------------------------------------------|-----------------------------|--------------------------------------------------------------------------------------------------------|
| `ProcessInstanceArchiverJob`                          | `operate-list-view`         | Process instances with a `completed` timestamp (also archives all `ProcessInstanceDependant` indices). |
| `BatchOperationArchiverJob`                           | `operate-batch-operation`   | Finished batch operations (also archives `BatchOperationDependant` indices).                           |
| `StandaloneDecisionArchiverJob`                       | `operate-decision-instance` | Standalone (not PI-linked) decision evaluations.                                                       |
| `AuditLogArchiverJob`                                 | audit log index             | Audit log entries past the retention window.                                                           |
| `UsageMetricArchiverJob` / `UsageMetricTUArchiverJob` | usage-metric indices        | Metric documents past their retention window.                                                          |
| `JobBatchMetricsArchiverJob`                          | job-batch-metric index      | Job batch metric documents.                                                                            |

### Process-Instance–Linked Documents

The `ProcessInstanceArchiverJob` is the most comprehensive job. It uses the `operate-list-view`
index to identify process instances that have completed. Once a batch of eligible process
instances is found, the job concurrently archives every index that implements
[
`ProcessInstanceDependant`](/webapps-schema/src/main/java/io/camunda/webapps/schema/descriptors/ProcessInstanceDependant.java):
flow node instances, variable updates, sequence flows, correlated message subscriptions, and
others. The archiving of those dependant indices is driven entirely by the process instance keys
found in the batch.

> **Important:** Documents that are _not_ linked to a process instance (e.g. process-definition–
> scoped start-event subscriptions) will never be picked up by the process-instance archiver and
> require their own cleanup strategy.

## Extension Points for Developers

### Option 1 – Hook into an Existing Archiver Job via Dependant Interfaces

If the new entity is logically bound to an existing aggregate (process instance or batch
operation) you can register it as a dependant and let the existing job handle it.

**Steps:**

1. Make the index template descriptor implement the appropriate dependant marker interface:
  - [
    `ProcessInstanceDependant`](/webapps-schema/src/main/java/io/camunda/webapps/schema/descriptors/ProcessInstanceDependant.java)
    for entities tied to a process instance.
  - [
    `BatchOperationDependant`](/webapps-schema/src/main/java/io/camunda/webapps/schema/descriptors/BatchOperationDependant.java)
    for entities tied to a batch operation.
2. The concrete template descriptor must implement the dependant-specific field accessor (e.g.
   `getProcessInstanceDependantField()`) so the job knows which field to match against.
3. Register the new template descriptor with the index descriptor / resource provider
   configuration (for example via `ExporterResourceProvider` / `IndexDescriptors`) so it is
   exposed through `ExporterResourceProvider#getIndexTemplateDescriptors()` and automatically
   picked up by `BackgroundTaskManagerFactory` without additional wiring.

### Option 2 – Create a New Archiver Job

For entities that are not lifecycle-coupled to an existing aggregate (e.g. audit logs, metrics,
definition-scoped subscriptions) you need a dedicated archiver job.

**Steps:**

1. **Define eligibility logic.** Decide what makes a document eligible for archival (a timestamp
   field, a status flag, a deletion marker set by the exporter handler, etc.).

2. **Extend `ArchiverJob<B>`** and implement the three abstract methods:

   ```java
   // Human-readable name used in log messages
   String getJobName();

   // Query the storage layer for the next batch of eligible document IDs
   CompletableFuture<B> getNextBatch();

   // The primary index template this job operates on
   IndexTemplateDescriptor getTemplateDescriptor();
   ```

   Optionally override `archive(...)` to archive dependant indices before moving the primary
   documents (see `ProcessInstanceArchiverJob` and `BatchOperationArchiverJob` as examples).

3. **Add a `getXxxNextBatch()` method to `ArchiverRepository`** (and both its Elasticsearch and
   OpenSearch implementations) if a new storage query is needed.

4. **Register the job** in
   [
   `BackgroundTaskManagerFactory`](/zeebe/exporters/camunda-exporter/src/main/java/io/camunda/exporter/tasks/BackgroundTaskManagerFactory.java)
   so the `BackgroundTaskManager` schedules it alongside the other tasks.

5. **Add metrics** (optional but recommended): wire in counter callbacks using the existing
   `CamundaExporterMetrics` API (or extend it) and pass them to the `ArchiverJob` super
   constructor.

### Handling Definition-Scoped Entities

Some entities (e.g. start-event message subscriptions) are bound to a process definition rather
than a process instance. Their lifecycle therefore depends on the lifecycle of the definition, not
individual process instances.

Recommended approach:

- **Flag for deletion from the exporter**: when the engine emits a DELETE event for the entity
  (or when the definition itself is deleted), the exporter handler sets a `deleted` flag on the
  document. The engine's DELETE handler can be leveraged for this purpose.
- **Create a dedicated archiver / cleanup job** that scans for documents with `deleted = true`
  and removes (or archives) them. Alternatively, extend the definition-deletion logic to
  explicitly issue storage-layer deletions for these documents.

This pattern ensures that definition-scoped subscriptions are never moved by the process-instance
archiver job (which would be incorrect) and are still cleaned up deterministically.

## Example: Message Start Event Subscriptions

The following illustrates the recommended strategy for a definition-scoped entity. The
discussion below is drawn from the design thread for message start event subscriptions.

- **Index placement**: start-event subscriptions can coexist with other message subscriptions in
  an existing (possibly templated) index to avoid a dedicated endpoint for searching them. Because
  the process-instance archiver is solely based on the `operate-list-view` index, documents that
  cannot be linked to an archived process instance simply remain in the active index without
  causing errors.
- **Correlated subscription documents** (which track which process instances were created by
  which start events) _are_ archived together with the process instance, so their lifecycle is
  already handled.
- **Start subscriptions** should be deleted when the process definition is deleted. The
  definition-deletion path (now available in the V2 REST API) should be extended to cascade the
  deletion to start-event subscriptions.

