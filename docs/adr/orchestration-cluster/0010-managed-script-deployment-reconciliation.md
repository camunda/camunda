# Managed script deployment reconciliation

**DRI**: Code Execution initiative

**Status**: Proposed

**Purpose**: Defines ownership and recovery semantics for asynchronously deploying managed scripts
to an external execution provider.

**Audience**: Orchestration Cluster, Connectors, Operate, Modeler, and runtime engineers working on
managed code execution.

## Context

Deploying a script to AWS Lambda or a Google Cloud Run function takes seconds or minutes and can
fail independently of a BPMN deployment. Performing provider calls while processing a Zeebe
deployment would couple partition availability and deployment latency to an external control plane.
Deferring deployment until the first process instance reaches the task would move the same latency
onto execution and create races between concurrent instances.

The provider-facing reconciler can be unavailable when a process definition is deployed. A
transient deployment notification is therefore not a sufficient work-discovery mechanism.
Deployment status must also be visible through the unified Camunda API and Operate without querying
the cloud provider.

Managed scripts run untrusted customer code. Provider credentials and customer code execution must
remain outside the Zeebe broker process.

## Decision

**D1. The Orchestration Cluster owns desired state and lifecycle state.** Deploying a process with a
managed script creates a durable `ManagedScriptDefinition` in `PENDING` as part of the process
deployment. The definition references the immutable deployed script resource and contains bounded,
safe provider metadata. Camunda is the source of truth consumed by APIs and projections.

**D2. A separately deployable managed-code service in the Connectors codebase owns provider
operations.** It packages source, deploys and inspects provider resources, invokes ready
deployments, and performs cleanup. It may reuse Connectors Runtime infrastructure, but has separate
configuration, credentials, scheduling, scaling, and lifecycle from ordinary connector workers.
It never executes production customer code in its own process.

**D3. Reconciliation uses a durable leased pull API.** The managed-code service activates eligible
definitions from authoritative engine state. Activation returns an opaque fencing token and an
expiry. Long-running work renews its lease. Expired work becomes eligible for another instance.
Lifecycle updates require the current lease token and expected definition revision.

**D4. Provider operations are resumable and idempotent.** Provider operation and deployment
identifiers are persisted as soon as they exist. A replacement reconciler resumes polling before
creating resources. Provider identity is based on:

```text
tenantId + provider + artifactDigest
```

The artifact digest is derived from the immutable source checksum, language, and runtime. Changing
the requested runtime therefore creates a distinct provider artifact even when source bytes are
identical.

**D5. Query APIs are projections, not a work queue.** Get and search APIs expose definition status
to Modeler, Operate, administrators, and diagnostics. Reconciliation activation does not poll
eventually consistent secondary storage.

**D6. Deployment readiness is an engine concern.** A managed-script job is not activatable while
its definition is `PENDING`, `BUILDING`, or `DEPLOYING`. Reaching `READY` wakes waiting jobs.
Waiting does not decrement BPMN retries or occupy a worker. A terminal deployment failure creates
an actionable incident for affected instances.

**D7. The lifecycle is headless-first.** REST APIs cover activation, lease renewal, fenced
transitions, get/search, and administrative retry before UI support is added.

The initial BPMN contract marks a Script Task with a deployment-bound linked resource whose
`resourceType` is `ManagedScript` and whose `linkName` is `script`. The task must use the reserved
job type `io.camunda:managed-script:1` and provide non-blank `language` and `runtime` task headers.
These constraints are validated during deployment so an incomplete definition cannot enter the
reconciliation queue.

## Consequences

- Process deployment returns without waiting for AWS or GCP.
- Definitions deployed while the managed-code service is unavailable remain discoverable.
- Provider latency and credentials remain outside the broker.
- The processing log needs a new value type, intents, state indexes, processors, and deployment
  transformation.
- New records need Camunda Exporter, Elasticsearch/OpenSearch, and RDBMS projections.
- The public API needs managed-script-specific authorization and tenant filtering.
- Process definitions created before this record exists need an idempotent backfill strategy.
- The managed-code service is an additional optional workload in production; C8Run needs a local
  provider so the feature remains self-contained for development.
- Invocation and UI work can be delivered after the deployment lifecycle without changing this
  ownership boundary.

