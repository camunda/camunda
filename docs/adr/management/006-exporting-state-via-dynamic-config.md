# Exporting state managed exclusively through dynamic cluster configuration

**DRI**: Deepthi Akkoorath

**Status**: Accepted

**Deciders**
- Deepthi Akkoorath

**Purpose**: Retire the legacy exporting-control surfaces (`BrokerAdminService`'s `/actuator/partitions`
operations and the raw wire-protocol admin requests) in favor of the single dynamic-config-backed
`ExportingStateController`, and make the exporter director's own restart path agree with that source of
truth.

**Audience**: Engineers working on exporting control, broker startup, or the management endpoints.

## Context

Tracking issue: #39743 ("Manage exporting status via dynamic config").

Before this change, exporting pause/resume/status existed on three independent surfaces that could
disagree with each other:

1. **`/actuator/exporting`** and **`/v2/exporting` / `/cluster/v2/exporting`** — broadcast a raw wire
   request (`ExportingRequestBroadcaster`) to every replica of every partition, independently and
   concurrently, with no durable record of the target state. A broker that restarts mid-broadcast, or
   joins later, does not know the intended state.
2. **`/actuator/partitions`** (`BrokerAdminService` → `BrokerAdminServiceEndpoint`) — the same
   broadcast-and-forget pattern, exposed as one operation among several partition-admin operations
   (snapshot, stream processing, exporting).
3. **The raw admin wire protocol** (`AdminApiRequestHandler`, `AdminRequestType.PAUSE_EXPORTING` /
   `SOFT_PAUSE_EXPORTING` / `RESUME_EXPORTING` / `GET_EXPORTING_STATE`) — what (1) and (2) actually send
   over the wire, routed straight to `PartitionAdminAccess` on the receiving broker. Bypasses dynamic
   config entirely.

None of these three write to dynamic cluster configuration. A separate, already-existing mechanism —
`PartitionConfigurationManager.setExportingState` — *does* apply an exporting-state change from dynamic
config to a live director, but nothing fed it from these legacy entry points, and the exporter
director's restart path (`ExporterDirectorPartitionTransitionStep.openExporter`) sourced its phase from a
legacy per-partition marker file (`.exporterPaused`, in `PartitionProcessingState`) rather than from
dynamic config. So even where a legacy entry point happened to pause every live replica successfully,
the pause did not survive a broker restart.

`PR #60541` and `PR #60577` (already merged/in review) built the replacement: `ExportingStateController`
(per-tenant `ByTenant` and cluster-wide `ClusterWide`), backed by `DynamicConfigExportingStateController`,
which submits an `ExportingStateChangeRequest` through the dynamic-config coordinator and awaits its
resolution (`ClusterConfigurationChangeAwaiter`). This is a single, durable, gossiped state that every
replica converges on, including newly joined or restarted ones.

### Was routing the legacy wire protocol through dynamic config considered?

Yes — evaluated and rejected as infeasible without invasive changes:

- No `ClusterConfigurationManagementRequestSender` (or coordinator-submission path) exists inside
  `zeebe/broker`; it is only wired at the application-assembly layer (`dist`). `AdminApiRequestHandler`
  runs inside the broker itself.
- The legacy broadcaster calls every replica of every partition of a tenant concurrently and
  independently. Forwarding each call into dynamic config would submit N racing per-tenant-scope change
  requests; the coordinator's admission rule rejects a new request whose scope overlaps an
  already-pending plan. Most of those concurrent forwards would fail with an error the legacy caller
  never expected from what looks like "pause this one replica."
- There is no existing precedent for a broker-side handler *submitting* into the dynamic-config
  coordinator — every existing broker-side touch point is an *applier*, reacting to a plan the
  coordinator already decided.

Rejecting outright during a rolling upgrade is safer than accepting requests that only partially apply,
or that race with a concurrently-submitted dynamic-config change and leave the cluster in a state neither
caller intended.

## Decision

### D1. `BrokerAdminService` no longer supports exporting operations

`pauseExporting()`, `softPauseExporting()`, `resumeExporting()` on `BrokerAdminService` /
`BrokerAdminServiceImpl` (reached via `/actuator/partitions`) reject with an error recommending
`/actuator/exporting` or `/v2/exporting` / `/cluster/v2/exporting` instead. `BrokerAdminService` itself
is not adapted to delegate to dynamic config — the operation is simply no longer available on this
surface, so callers migrate to the endpoints that are backed by dynamic config.

### D2. The raw admin wire protocol no longer supports exporting operations

`AdminApiRequestHandler`'s `PAUSE_EXPORTING` / `SOFT_PAUSE_EXPORTING` / `RESUME_EXPORTING` /
`GET_EXPORTING_STATE` requests are rejected (see "Was routing... considered?" above). Nothing in this
codebase issues these requests once `ExportingRequestBroadcaster` and its callers
(`ExportingServices`, `ClusterExportingServices`, `ExportingEndpoint`) are migrated to
`ExportingStateController` (D4); the handler cases are kept only long enough to reject cleanly during a
rolling upgrade against an older broker that might still send them, then removed once no supported
version can.

### D3. The exporter director's restart path sources its phase from dynamic config, not the legacy file

`ExporterDirectorPartitionTransitionStep.openExporter` seeds and re-applies the director's exporter
phase from dynamic config's `exporting().state()` (already resolved onto the transition context),
instead of `PartitionProcessingState`'s legacy `.exporterPaused` marker file. Once this is in place, the
legacy file is retired outright — not read, not written — for the exporter half of
`PartitionProcessingState`. `PartitionGroupExportingStateInitializer` (`zeebe/dynamic-config`) already
performs a one-time migration of any pre-existing legacy file into dynamic config on the local member,
gated on dynamic config still being `UNKNOWN`; `ClusterConfigurationManagerStep` runs and completes
before `PartitionManagerStep` opens any partition (see `BrokerStartupProcess.buildStartupSteps`), so this
migration is guaranteed to have already run by the time `openExporter` reads dynamic config on a
restarting broker — a broker upgrading from a version that only had the legacy file does not silently
resume exporting.

The equivalent legacy file for stream-processor pausing (`.processorPaused`,
`PERSISTED_PAUSE_STATE_FILENAME`) is untouched by this decision; only the exporter half of
`PartitionProcessingState` is retired.

### D4. `ClusterExportingServices` submits one cluster-wide change, not one per tenant

`ClusterExportingServices` (backing `/cluster/v2/exporting`) uses `ExportingStateController.clusterWide()`
— a single atomic change plan covering every physical tenant — rather than looping per-tenant requests.
This avoids partial application across tenants (some tenants paused, others not, if a later request in
a loop failed) and matches how the coordinator already treats an unscoped request: tenants disabled, or
added after the change was submitted, are unaffected by it.

## Consequences

- External API surface is unchanged: `/actuator/exporting`, `/v2/exporting`, `/cluster/v2/exporting`
  keep their existing paths and response contracts.
- `/actuator/partitions`'s exporting operations, and the raw admin-wire exporting requests, stop working
  and return an explicit error instead of silently doing nothing durable. Any caller still depending on
  them (internal tooling, tests) must move to the dynamic-config-backed endpoints.
- A broker pause now survives restarts and is visible to every replica through gossiped configuration,
  closing the gap that #39743 exists to close.
- The legacy `.exporterPaused` file and `ExportingRequestBroadcaster` become dead code once every caller
  is migrated, and are removed in the same change rather than left as unused fallback paths.
