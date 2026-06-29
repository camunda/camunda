# ADR-0001: Namespace-Wide Diagnostics Collection via debug-cli

## Status

Proposed

## Deciders

- Lena Schoenburg ([@lenaschoenburg](https://github.com/lenaschoenburg))
- _(add reviewers)_

## Context

When a Camunda 8 Self-Managed cluster misbehaves, the first and most expensive step in any support
or incident investigation is collecting the diagnostic data needed to reason about the problem.

Today we ship a **standardized diagnostics collection script** for this purpose
([`camunda-collect-diagnostics.sh`](https://docs.camunda.io/docs/self-managed/deployment/helm/operational-tasks/diagnostics/),
sourced in the `camunda-docs` repo). Customers run it with `--namespace <ns>` and it produces a
`.zip` of pod logs (current + previous), `kubectl describe`/events, PV/PVC and node descriptions,
services/endpoints/ingresses, configmaps, and Helm values/history, which they review and then attach
to a support case. This works and is the established baseline — but it has structural limits:

- **It is client-side bash over `kubectl`/`helm`/`curl`.** It captures Kubernetes-level data well but
  has no awareness of Camunda internals, and growing it means growing a shell script. Open issues
  already push it in that direction — adding Elasticsearch index/import-position data
  ([#54608](https://github.com/camunda/camunda/issues/54608)) and JVM thread dumps
  ([#55031](https://github.com/camunda/camunda/issues/55031)) — each of which is today a manual
  back-and-forth with the customer because the script does not yet collect it.
- **Camunda-specific internals are hard to reach from outside.** Zeebe cluster topology and partition
  status, exporter positions, and management/actuator endpoints are most reliably gathered from
  inside the cluster, not via an operator's external `kubectl` context.
- **Redaction is fully manual.** The customer must review the bundle and strip secrets/PII by hand
  before sharing — error-prone for a bundle that, by design, leaves their environment.
- **No least-privilege story.** The script runs with whatever (often broad) kubeconfig the operator
  holds, rather than a scoped, auditable permission set.

We already ship `debug-cli` (`cdbg`) — a picocli fat-jar bundled into the distribution and present in
the container image at `/usr/local/camunda/bin/cdbg` (see `dist/pom.xml`). It already understands
Zeebe internals (`topology.meta`, SBE decoding, RocksDB snapshot inspection) and there is a working
precedent for running it as Kubernetes Jobs: `debug-cli/scripts/reset-incident-position` generates
one Job per broker, mounts the broker PVCs, and invokes `cdbg` to perform a recovery operation.

This decision spans three repositories — the CLI lives in `camunda/camunda`, the Job/RBAC manifests
in `camunda/camunda-platform-helm`, and the existing script in `camunda/camunda-docs` — so it is
recorded as a top-level (monorepo-wide) ADR rather than a module-scoped one.

> **Note on the referenced PR.** The request cited PR #9210 for context. In `camunda/camunda` that
> number resolves to an unrelated 2022 CI workflow, and it does not exist in
> `camunda/camunda-platform-helm`. The reference appears stale; this ADR is instead anchored on the
> existing diagnostics script and the `reset-incident-position` Job precedent. Please point us at the
> intended PR if one exists so we can incorporate its context.

## Decision

Build a native diagnostics-collection capability into `debug-cli` (working name
`cdbg diagnostics collect`) that gathers all relevant diagnostic data from a Kubernetes namespace
into a single, timestamped, namespaced archive (`tar.gz`) suitable for attaching to a support case.
The Camunda Helm chart defines an **opt-in** Kubernetes resource (a `Job`, or a manually-triggerable
`CronJob` suspended by default) plus a **dedicated, least-privilege RBAC bundle** (`ServiceAccount` +
namespace-scoped `Role` + `RoleBinding`) that runs this command. Over time this becomes the
successor to the bash script: the data the script collects today, plus the Camunda-internal data the
open issues ask for, gathered by one tested tool.

### What gets collected

- **Kubernetes objects** in the namespace: pods, statefulsets/deployments, services, endpoints,
  ingresses, configmaps (non-secret), events, PV/PVC metadata, and node/resource details — parity
  with the current script.
- **Pod logs**, current and previous, for all Camunda components.
- **Camunda management/actuator endpoints** reachable in-cluster: health, info/version, configuration,
  metrics, Zeebe cluster topology and partition status, and (opt-in, size-permitting) thread dumps
  (addresses [#55031](https://github.com/camunda/camunda/issues/55031)).
- **Secondary-storage state** where applicable: Elasticsearch/OpenSearch index and import-position
  data (addresses [#54608](https://github.com/camunda/camunda/issues/54608)).
- **Zeebe on-disk artifacts** already supported by `cdbg` where a PVC is mounted (e.g.
  `.topology.meta`).

### Native Kubernetes client, not shelling out to `kubectl`

The collector talks to the Kubernetes API through a **native Java Kubernetes client** (e.g. fabric8
or the official `kubernetes-client`), not by shelling out to an in-pod `kubectl`. This keeps the
collection logic typed, unit-testable, and free of dependencies on `kubectl`/`helm` binaries being
present and version-matched in the image. **No Kubernetes client library exists in any `pom.xml`
today**, so adding one is a new dependency that requires explicit maintainer approval per `AGENTS.md`;
selecting the specific library is part of accepting this ADR.

### Why in-cluster collection

Collection runs **inside the namespace** rather than client-side because it can reach internal
management endpoints not exposed externally, can mount PVCs for on-disk artifacts, and authorizes
against Kubernetes-native RBAC scoped to exactly what it needs — instead of relying on whatever
external access and kubeconfig the operator happens to hold.

### Least-privilege, opt-in by default

The Helm-provisioned `Role` grants only `get`/`list` on the specific resource types collected
(pods, pods/log, configmaps, events, statefulsets, deployments, services, endpoints, ingresses,
pvcs, and the read-only node/PV reads the script performs) and **explicitly not `secrets`**. The
whole feature is **disabled by default** and enabled via a chart value, so no extra `ServiceAccount`
or permissions exist in a cluster that has not asked for them.

### Output and confidentiality

The archive is written to a mounted volume and retrieved with `kubectl cp` (a later iteration may add
optional upload to an object store). Because the bundle leaves the customer environment, **secret
redaction is a first-class requirement, not an afterthought**: secrets are never read, and known
sensitive fields in collected configs are scrubbed before they reach the archive — improving on the
current "review it by hand" model.

### Scope boundary

This ADR covers the diagnostics-collection capability and its Helm/RBAC delivery. It does **not**
change any existing `cdbg` command. The existing bash script remains the supported path until the
native collector reaches parity.

## Alternatives considered

1. **Keep growing the bash script** (the status quo: extend `camunda-collect-diagnostics.sh` to cover
   ES data, thread dumps, topology, etc.). Rejected as the long-term path: it is untested shell, has
   no Camunda-internal awareness, runs client-side, and leaves redaction manual. It remains the
   interim baseline until the native collector reaches parity.
2. **Shell out to `kubectl` from inside the Job** (a thin wrapper rather than a native client).
   Rejected: couples the image to `kubectl`/`helm` binaries and their versions, and keeps the logic
   in untestable shell — the very problems we are trying to leave behind.
3. **A separate, dedicated diagnostics tool or operator.** Rejected: duplicates capabilities
   `debug-cli` already has (Zeebe-internal decoding, K8s-Job packaging) and adds another artifact to
   build, ship, and version.
4. **Rely on generic external tooling** (`kubectl cluster-info dump`, third-party support bundles).
   Rejected as sufficient on its own: captures no Camunda-specific internals. It may still be
   referenced as a complementary source.
5. **Client-side kubectl plugin** run from the operator's machine. Rejected as the primary path:
   requires broad external access, cannot reach internal-only management endpoints, and cannot mount
   PVCs.

## Open questions

- **Kubernetes client library.** fabric8 vs. the official `kubernetes-client` — to be settled when the
  new dependency is approved.
- **Helm resource shape.** Bare `Job` (apply-on-demand) vs. a `CronJob` suspended by default that an
  operator un-suspends to trigger a run.
- **Retrieval UX.** `kubectl cp` for the first iteration vs. optional object-store upload later.
- **Migration of the docs script.** Whether the bash script is eventually retired or kept as a
  zero-dependency fallback for environments where the Job cannot run.

## Consequences

### Positive

- One command produces a consistent, complete support bundle — including the Camunda internals
  (topology, partitions, exporter positions, thread dumps, ES import state) that today require manual
  back-and-forth.
- Camunda-specific internals are captured reliably alongside standard Kubernetes data.
- Permissions are explicit, namespace-scoped, least-privilege, and auditable — and absent unless
  enabled.
- Reuses an already-shipped tool that is in the image and proven to run as a Kubernetes Job.
- Collection logic lives in testable Java rather than shell.

### Negative

- `debug-cli` grows from offline file manipulation into live-cluster interaction and takes on a new
  Kubernetes-client dependency (requires explicit approval).
- The decision spans three repositories (`camunda/camunda`, `camunda-platform-helm`, `camunda-docs`);
  the CLI, the Job/RBAC manifests, and the existing script must be kept coherent across them and
  across supported versions.
- Redaction is security-critical and non-trivial; a regression risks exfiltrating sensitive data in a
  bundle that, by design, leaves the customer environment.
- Some artifacts (heap dumps, verbose logs, full ES dumps) are large and must be bounded/opt-in to
  keep bundles manageable.
- Two collection paths coexist until the native collector reaches parity and the script is retired.
