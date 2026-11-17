# Gaps in the draining deletion of process definitions

Findings from an audit of the draining lifecycle on `main` (`a86bc4673c3`). When a process
definition with running instances is deleted, it goes `ACTIVE → DRAINING`. Each partition deletes
it locally (`DELETING/DELETED`) once its last local instance ends and reports `DELETE_COMPLETE`
to the deployment partition, which writes `FULLY_DELETED` once all partitions have reported.

The first part lists the gaps reproduced by `DrainingProcessDefinitionGapsTest` and
`DrainingProcessDefinitionRaceTest`. These tests assert the expected behaviour and fail as long as
the gap exists. The second part lists further findings that have no test yet.

## Reproduced

### 1. A deleted version keeps the start events it took over

Tests: `shouldRemoveTimerStartEventWhenDeletingVersionThatTookItOver`,
`shouldHandOverMessageStartEventWhenDeletingVersionThatTookItOver`

1. v1, v2, v3 have a timer/message/signal start event. v3 holds the subscriptions.
2. Delete v3 while it has running instances. v3 becomes `DRAINING`, and its start events are
   handed over to v2.
3. Delete v2, which has no instances. v2 is deleted immediately, but its start events are neither
   closed nor handed over to v1.

Cause: `ResourceDeletionDeleteProcessor.deleteProcess` only hands over start events if the
deleted version equals `getLatestProcessVersion`. A draining version still counts as latest
(v3), so deleting v2 is not treated as deleting the latest version.

Effect:
- The left-over timer belongs to a definition that no longer exists. Every later deployment of
  **any** process is rejected on the deployment partition, because
  `DeploymentCreateProcessor.unsubscribeFromPreviousTimer` dereferences
  `getProcessByKeyAndTenant(...)` for every start timer:
  `PROCESSING_ERROR Expected to create timer for start event, but encountered the following error:
  Cannot invoke "DeployedProcess.getBpmnProcessId()" because the return value of
  "ProcessState.getProcessByKeyAndTenant(long, String)" is null`.
  The timer itself never goes away: `TimerTriggerProcessor` rejects it as `NOT_FOUND`.
- Message and signal start events start nothing, although v1 is active.
- It does not need a second delete: partitions finish draining at different times, so they can
  disagree on which version is latest, and the hand-over happens on some partitions only.

Fix direction: hand over when the deleted version is the latest *active* version.

### 2. Deploying while the latest drains leaves an older version subscribed

Test: `shouldOnlyStartNewVersionBySignalWhenDeployingWhileLatestDrains`

1. v2 is draining, so v1 holds the start events.
2. Deploy v3.

Cause: `StartEventSubscriptionManager.findPreviousVersionOfProcess` closes only the subscriptions
of the version directly below v3. That is the draining v2, which holds nothing.

Effect: v1 and v3 are both subscribed. A signal starts both. A message starts v1, because
subscriptions are visited in key order. The leak is permanent. Timer start events are not
affected (the deployment removes all timers of the process id).

Fix direction: close the subscriptions of the latest active version below the new one.

### 3. "Latest" lookups do not skip a draining latest version

Tests: `shouldCallLatestActiveVersionWhenLatestCalledVersionIsDraining`,
`shouldCorrelateBufferedMessageToLatestActiveVersionWhenLatestIsDraining`

- A call activity with `latest` binding resolves to the draining v2 and raises an incident,
  although v1 is active. On a partition that already deleted v2 locally, the same call starts v1.
  Create-by-id was fixed for exactly this in #61817, which left call activities out of scope.
- When an instance of the draining latest ends, the buffered message start looks up subscriptions
  on that version, which has none, so a buffered message is not correlated to v1.

Cause: `CallActivityProcessor` (via `BpmnStateBehavior.getLatestProcessVersion`) and
`BpmnBufferedMessageStartEventBehavior` use `getLatestProcessVersionByProcessId`.

Fix direction: use `getLatestActiveProcessVersionByProcessId`, as create-by-id does. An incident
remains correct when no active version exists, and for `deployment`/`versionTag` bindings.

### 4. The activation guard blocks existing instances

`ProcessInstanceStateTransitionGuard` rejects every `ACTIVATE_ELEMENT` of a PROCESS element whose
definition is draining. It cannot tell a new instance from one that already exists or was already
accepted.

**4a. Incident during activation.** Test:
`shouldContinueExistingInstanceAfterIncidentResolutionWhenDefinitionIsDraining`

1. An incident is raised while the process element is activating (e.g. a process start
   execution listener whose job type expression fails). The instance is in
   `ELEMENT_ACTIVATING`.
2. The definition is deleted; the instance counts as running, so the definition drains.
3. The incident is resolved. `IncidentResolveProcessor` writes `ACTIVATE_ELEMENT` again, and the
   guard rejects it.

Effect: the instance stays in `ELEMENT_ACTIVATING` with no incident, and the definition never
finishes draining. Only cancelling the instance helps.

Fix direction: skip the check when the process element instance already exists.

**4b. Create and delete race.** Test: `DrainingProcessDefinitionRaceTest`

1. `CREATE` is processed: `CREATED` is written and the client gets the process instance key. The
   process `ACTIVATE_ELEMENT` is a follow-up command.
2. If the batch limit is reached, the follow-up is written to the log behind a `DELETE` that is
   already there. The delete marks the definition draining (or deletes it if no other instance
   runs), because the new instance has no element instance yet.
3. The guard rejects `ACTIVATE_ELEMENT`.

Effect: the client got `CREATED`, but the instance never runs. A parent call activity waits
forever; a message start leaves its correlation key locked. Normally the follow-up is processed
in the same batch as `CREATE`, so this needs the batch to be full; the test forces it with
`maxCommandsInBatch(1)`.

Fix direction: do not block the activation of an accepted instance, and make the delete aware of
instances that are accepted but not yet activated.

## Not reproduced yet

Engine:

- **Unexpected errors in the delete are not acknowledged.** `tryHandleError` only handles
  forbidden, not found and already draining. Any other exception (e.g. an `EvaluationException`
  while resubscribing the previous version's timer start event) leaves a distributed delete
  unacknowledged, which blocks the deployment queue. The in-memory process cache is not cleared on
  rollback either, so the leader keeps seeing `DRAINING`.
- **Correlation key lock.** If a delete lands between a message start correlation and the
  activation (4b), `MessageStartEventSubscriptionCorrelatedApplier` keeps (process id,
  correlation key) locked, and nothing releases it.
- **Banned last instance.** Finalization only runs on complete, terminate and migration. If the
  last instance is banned, the definition never reaches `FULLY_DELETED`. A repeated delete is
  accepted without effect. Cancelling the banned instance works around it.
- **Delete during a partition scale-up.** `drainPartitions` includes the new partition. If it is
  bootstrapped from a snapshot taken after the delete, the definition is already gone there and it
  never reports. The pending-deletion rows are partition-local and not copied, and the orphan retry
  gets the same `NOT_FOUND`. The definition never reaches `FULLY_DELETED`.
- **Timer start events on every partition (older bug).** Deleting the latest version re-creates
  the previous version's timer start events on every partition, but deployments create them only
  on the deployment partition. The extra timers are never removed and start duplicate instances.
- **Recovered orphan drops `deleteHistory`.** The orphan retry is a plain delete, so a requested
  history deletion is silently dropped.
- **Repeated delete lifecycles differ** depending on how far the drain is (with or without
  `DELETING`, distributed or not).
- **Conditional start event subscriptions** are not marked as not ordinal controlled (signal and
  message were fixed).
- **Authorization and tenants.** The orphan retry finishes a deletion without checking the delete
  permission, and the pending-deletion lookup is tenant agnostic, so a caller from another tenant
  learns that the key exists.
- **Metrics.** A definition restored from `PENDING_DELETION` is missing from the deployed
  definitions gauge until the next restart.

Secondary storage and consumers:

- **History deletion vs. exporter lag.** `FULLY_DELETED` starts the history deletion right after
  the last instance ends, and the item provider reads secondary storage. Instances not yet
  exported are not found, so their history stays.
- **ES/OS history cleanup deletes the definition regardless of remaining instances.**
  `HistoryDeletionJob.deleteProcessDefinitions` deletes the definition document, its message
  subscriptions and agent definitions unconditionally; RDBMS keeps the definition while instance
  rows remain. With a banned instance, Operate keeps an instance whose BPMN XML is gone.
- **Tasklist processes page** filters on `isLatestVersion: true` without `state: ACTIVE`. It shows
  a draining or deleted version (starting it fails) and hides the active one.
- **Analytics** emits "definition deleted" on partition 1's local `DELETED`, not on
  `FULLY_DELETED`.
- **Legacy Operate client** offers draining versions as migration targets (the engine rejects
  them).
- **Optimize** only imports `CREATED` and never learns about deletions.
