---
feature: next-work-in-case
title: Deliver the next work in a case with its data
component: camunda/camunda/zeebe/engine
status: draft
source: https://github.com/camunda/product-hub/issues/3811
timebox: 2026-10-20
related_plans: []
updated: 2026-10-09
---

# Plan: Deliver the next work in a case with its data — camunda/camunda/zeebe/engine

## Summary
A client that completes a user task today has to search until the next task appears, then fetch
it and its variables, and each read can return a task that its own variables contradict. This plan
adds one opt-in interaction that hands a caller the user tasks available to them in a case (none,
one or several) together with their data, read from the engine's state so task and data agree.
Proposal: [camunda/product-hub#3811](https://github.com/camunda/product-hub/issues/3811). Which
mechanism hands the work out is still open ([Q3](#open-questions)).

## Open items
**Status:** draft — next: this team settles Q1–Q3 by 2026-10-20; then the plan goes to c8-api-team and identity for review.

To answer a question, comment on its row in the plan PR. The plan author folds answers into the
plan each round and links answers given elsewhere.

- [Q1](#open-questions) — Do a task and its local variables read in one command always agree? · this team · blocks the design
- [Q2](#open-questions) — What does a client do when a wait times out or a response is lost? · this team · blocks the design
- [Q3](#open-questions) — Which candidate hands out the work? · this team · blocks the design
- [Q4](#open-questions) — Confirm release split and time-box; alpha, flag, backports? · this team · not blocking
- [Q5](#open-questions) — Two consistency labels for user-task reads: document both or avoid the mix? · c8-api-team · blocks the design
- [Q6](#open-questions) — Endpoint design review before agreement · c8-api-team · blocks the design
- [Q7](#open-questions) — Do the existing user-task read grants fit this path? · camunda/identity · blocks the design
- [Q8](#open-questions) — Which variables are a task's data? · product · not blocking
- [Q9](#open-questions) — Confirm the assumed non-goals · product · not blocking
- [Q10](#open-questions) — Confirm the assumed edge cases · product · not blocking
- [Q11](#open-questions) — Who owns the guide page and the success signals? · product · not blocking
- Owners unverified ([capabilities](#capabilities)): CAP-1 this component; CAP-2 camunda/camunda/security — confirm before agreeing

## Scope
- **In:** one interaction that hands a caller the user tasks available to them in a case (a root
  process instance and everything below it), with each task's key and local variables read from
  the same state as the task. It tells "no work available yet" from "no work for you", returns
  several tasks when several are available, and finds tasks inside called processes. Opt-in and
  additive.
- **Edge cases handled:** several tasks available (parallel branches); no work yet vs. none for
  this person; tasks in called processes; a lost response or a timed-out wait. Assumed — Q10:
  a task still running its creating listeners is not handed out until it is created; a task
  assigned to someone else counts as "none for you"; a task offered to the caller as candidate is
  handed out unclaimed; tenant isolation as for every task read.
- **Out:** changing existing endpoints or their consistency labels; changing claiming; any client
  migration; work outside the current case. Assumed — Q9: list or search responses carrying
  variables; Tasklist UI adoption; a starter application; new task lifecycle states. Every edge
  case not listed above is unhandled.
- **Variants / products:** SaaS and Self-Managed, with every secondary-storage option, including
  none: the interaction doesn't read secondary storage ([D1](#d1--the-work-and-its-data-come-from-the-engines-state-option-n-native)).
- **Target release:** 8.11 hands out the task key and local variables; the full payload follows in
  8.12 ([D3](#d3--811-hands-out-the-key-and-local-variables-full-payload-in-812-option-n-native)) · **Backports:** unknown — Q4

## Capabilities
| ID | The system must… | Owner | Option | Ref |
|---|---|---|---|---|
| CAP-1 | hand a caller the user tasks available to them in a case (none, one or several) with each task's local variables read from the same state as the task, and tell "no work yet" from "none for you" | this component (unverified) | N Native | D1 |
| CAP-2 | decide which of those tasks a caller may receive, with the same permissions as reading a user task today | camunda/camunda/security (unverified) | C Consume | D2 |

## Decisions
### D1 — The work and its data come from the engine's state (option N Native)
- Capability: CAP-1
- Rejected: reading from secondary storage — exporters write a task and its variables as separate
  documents after processing, and no transaction spans them, so a read can catch one without the
  other; waiting for the exporter only moves the delay. Workaround in a client (cache, retries): the
  problem the feature removes.
- Consequence: The new interaction is strongly consistent while user-task search stays eventually
  consistent, two labels on one entity ([Q5](#open-questions)). Its reads run on the partition's
  processing thread and compete with commands.

### D2 — Permissions reuse the user-task read grants (option C Consume)
- Capability: CAP-2
- Rejected: N, a new permission type in the engine (the permission model is the security
  component's); R, a new permission requested from identity (nothing new is needed if the
  existing grants fit, [Q7](#open-questions)).
- Consequence: A caller receives only tasks it could read today. The read check runs in the engine
  for this path, so it must stay in parity with the read check in the service layer.

### D3 — 8.11 hands out the key and local variables, full payload in 8.12 (option N Native)
- Capability: CAP-1
- Rejected: the full payload (form, all visible variables) in 8.11, since it needs [Q8](#open-questions)
  answered first; dropping the consistent read to ship sooner, since it is the point of the feature.
  The target release cut payload, not ownership: no part moves to a component that doesn't own it.
- Consequence: 8.11 clients still fetch the form separately. The 8.12 payload may change the
  response shape, so 8.11 ships it as alpha unless [Q4](#open-questions) says otherwise.

### D4 — This team writes the API contract and Java client under the API guidelines (option C Consume)
- Capability: CAP-1
- Rejected: R, a brief to c8-api-team for the operation and the client command. The
  [endpoint guidelines](../../../../../docs/rest-api-endpoint-guidelines.md#1-end-to-end-workflow)
  give spec, controller, service and client to the feature team, with c8-api-team reviewing.
- Consequence: c8-api-team reviews the contract before the design is agreed ([Q6](#open-questions))
  and each PR that touches its paths. Nothing waits on its roadmap.

## Constraint review
| ID | Constraint | Applies? | Read | Decision / reason |
|---|---|---|---|---|
| M1 | Documentation | applies | [guidelines § 9](../../../../../docs/rest-api-endpoint-guidelines.md) | The API reference is generated from the spec descriptions. A guide on the flow and its consistency label is also needed; owner unknown ([Q11](#open-questions)). |
| M2 | Success measurement | applies | none | Signals: calls, wait time, timeouts, empty results, and fewer user-task searches after completion. Owner unknown ([Q11](#open-questions)). |
| M3 | User experience | n/a | none | API only; no UI in this plan. |
| C1 | Replay determinism and event applier immutability | applies | [golden files](../../../../../docs/zeebe/event-applier-golden-files.md) | A case index is filled by new appliers or new applier versions; released appliers stay unchanged; `NoChangesTest` passes. |
| C2 | Rolling-update compatibility of records and processing | applies | [rolling updates](../../../../../docs/zeebe/rolling_updates.md#processing) | A new command or intent is a transient incompatibility: until all brokers run 8.11 the interaction is rejected, and the gateway reports it as unavailable. |
| C3 | No full data migrations | applies | [data migrations](../../../../../docs/zeebe/rolling_updates.md#data-migrations) | A new column family is filled from the upgrade on; tasks created earlier are found by walking the element-instance tree. No migration. |
| C4 | Single-threaded non-blocking processing | applies | [README](../../../README.md#dos-and-donts) | Lookups are bounded per case and capped in size; any wait is held outside the processor (gateway or response state), never on the processing thread. |
| C5 | Authorization of externally triggered commands | applies | [handbook](../../../../../docs/zeebe/developer_handbook.md#authorization-checks-in-the-engine) | Each handed-out task is checked against the user-task read grants ([D2](#d2--permissions-reuse-the-user-task-read-grants-option-c-consume)); no new permission type expected. |
| C6 | Tenant isolation | applies | [security ADR 002](../../../../../docs/adr/security/002-tenant-access-provider-ownership-and-seam.md) | Each task's tenant is checked against the caller's tenants, as for other user-task commands; physical tenant follows the partition group. |
| C7 | Idempotent inter-partition commands | n/a | [handbook](../../../../../docs/zeebe/developer_handbook.md#how-to-do-inter-partition-communication) | A case lives on one partition: call activities create children on the parent's partition ([BpmnStateTransitionBehavior](../../../src/main/java/io/camunda/zeebe/engine/processing/bpmn/behavior/BpmnStateTransitionBehavior.java)). |
| C8 | No secondary storage from processors | applies | [README](../../../README.md#processors-do-not-have-access-to-the-secondary-storage) | Nothing is read from secondary storage ([D1](#d1--the-work-and-its-data-come-from-the-engines-state-option-n-native)). |
| C9 | Secret values never on the log | n/a | [secret resolution ADR](../../../../../docs/adr/secrets/001-central-secret-resolution-architecture.md) | Variables are handed out as stored; no secret is resolved for user tasks. |
| C10 | New records reach exporters and test tooling | applies | [handbook](../../../../../docs/zeebe/developer_handbook.md#how-to-create-a-new-record) | Camunda, RDBMS and ES/OS exporters and Camunda Process Test must ignore or handle the new intent; impact note below. |
| C11 | Event applier versions aligned across minors | applies | [golden files](../../../../../docs/zeebe/event-applier-golden-files.md) | No backport planned (Q4); if one is, new applier versions reach every newer minor. |
| service C2, C3 | Who enforces the permission; engine command goes through the mutators | applies | [guidelines § 5.2](../../../../../docs/rest-api-endpoint-guidelines.md#52-command-services) | Sent through the broker-request mutators with the caller's claims; the engine enforces the read grants (D2). |
| service C5, C6 | Secondary-storage dependency; error status the caller sees | applies | [guidelines § 2.5](../../../../../docs/rest-api-endpoint-guidelines.md#25-eventually-consistent-annotation-x-eventually-consistent) | No secondary storage needed. A timeout maps to a status that says whether the triggering command was applied ([Q2](#open-questions)). |
| gateway-rest C1, C2, C5 | Spec first; forward compatibility; eventual consistency | applies | [guidelines § 2.7](../../../../../docs/rest-api-endpoint-guidelines.md#27-backwards-compatibility-and-breaking-changes) | Spec first, additive only; `x-eventually-consistent: false` without `@RequiresSecondaryStorage`. Existing completion keeps its `204` unless the caller opts in. |
| gateway-rest C7, C8, C9 | Alpha annotations; disabled with the REST API; clients follow | applies | [guidelines § 2.8](../../../../../docs/rest-api-endpoint-guidelines.md#28-alpha-endpoints-and-properties) | Alpha per Q4 with the exact sentinel text; disabled with the REST API; Java client per D4; no MCP mirror. |
| protocol C1, C4 | Wire and log compatibility; a new record reaches every place | applies | [handbook](../../../../../docs/zeebe/developer_handbook.md#how-to-create-a-new-record) | New intent or value type added, never changed; reaches exporters and test tooling (C10). |
| gateway-protocol C1, C6 | Design review and API team sign-off; alpha marking | applies | [guidelines § 1](../../../../../docs/rest-api-endpoint-guidelines.md#1-end-to-end-workflow) | Contract reviewed by c8-api-team before agreement ([Q6](#open-questions)). |

## Design
UX design: n/a — not user-visible (API only)

**Closest existing features.** Two engine features already hand work and data out of a command:

- Job activation reads each job's variables while processing the activation command
  ([JobBatchActivateProcessor](../../../src/main/java/io/camunda/zeebe/engine/processing/job/JobBatchActivateProcessor.java),
  [JobVariablesCollector](../../../src/main/java/io/camunda/zeebe/engine/processing/job/JobVariablesCollector.java)),
  so job and data come from one state. The gateway parks a request with no jobs until the broker
  signals new work ([LongPollingActivateJobsHandler](../../../../gateway/src/main/java/io/camunda/zeebe/gateway/impl/job/LongPollingActivateJobsHandler.java)).
  REST: `POST /v2/jobs/activation` in [jobs.yaml](../../../../gateway-protocol/src/main/proto/v2/jobs.yaml).
- Create-with-result holds the response until the instance completes
  ([ProcessInstanceCreationCreateWithAwaitingResultProcessor](../../../src/main/java/io/camunda/zeebe/engine/processing/processinstance/ProcessInstanceCreationCreateWithAwaitingResultProcessor.java),
  [BpmnProcessResultSenderBehavior](../../../src/main/java/io/camunda/zeebe/engine/processing/bpmn/behavior/BpmnProcessResultSenderBehavior.java)).
  On a gateway timeout it returns `504` without the instance key
  ([process-instances.yaml](../../../../gateway-protocol/src/main/proto/v2/process-instances.yaml)),
  so the caller can't tell whether the instance was created.

**What every candidate needs (CAP-1).**

- **Finding the tasks in a case.** A case is a root process instance and everything below it. Call
  activities create the child on the parent's partition, so one partition holds the whole case.
  User-task state is keyed by task only ([DbUserTaskState](../../../src/main/java/io/camunda/zeebe/engine/state/instance/DbUserTaskState.java)):
  add an index from root process instance to its open user tasks, filled by appliers when a task is
  created and cleared when it completes or is canceled (C1, C3).
- **Available** means lifecycle `CREATED` ([UserTaskState](../../../src/main/java/io/camunda/zeebe/engine/state/immutable/UserTaskState.java)).
  A task still running listeners (`CREATING`, `ASSIGNING`) is not available yet.
- **For this person.** Assigned to the caller: handed out. Caller among the candidates: handed out
  unclaimed, since claiming is unchanged. Assigned to someone else: "none for you".
- **Data.** The task's local variables are read while processing the same command, as job activation
  does, so key and variables come from one state.
- **Outcomes.** One or several tasks; "no work yet" (the case is active and may still produce work);
  "none for you" (work exists that the caller can't receive); "case finished".
- **Reads go through a command.** [SYSTEM.md](../../../../../SYSTEM.md) DR3 says services read only
  through secondary storage, never engine state. A command response that carries data, as job
  activation does, keeps that rule; a plain query to the engine does not.

**The open choice** ([Q3](#open-questions)). The candidates come from
[camunda/product-hub#3811](https://github.com/camunda/product-hub/issues/3811); named here by what
they do:

| Candidate | Where the wait sits | Several available | Timeout or lost response | Needs beyond the above |
|---|---|---|---|---|
| Hand out on completion | Completion response held until the next task is created, as create-with-result does | Undefined: first, all, or wait for more | Caller can't tell whether the completion happened | Optional fields on the completion request; a body on an operation that returns `204` today |
| Activate work | A separate call parked in the gateway until work appears, as job activation does | A batch | Repeatable read: ask again | A "task available in a case" signal from the engine to the gateway |
| Ask the engine | None: the caller polls | A list | No wait to time out | An engine read path from the service layer: a decision record superseding DR3 |

### Boundary needs: camunda/camunda/service, zeebe/gateway-rest, zeebe/protocol, zeebe/gateway (this team)
- zeebe/protocol must carry the new command or intent and a response with task keys, local
  variables and the outcome — Acceptance: revapi passes; every exporter ignores or handles it · Needed in: 8.11
- service must send it through the broker-request mutators with the caller's claims — Acceptance:
  a caller without read grants receives no task · Failure behaviour: a timeout says whether the
  triggering command was applied, so a retry never completes a task twice · Needed in: 8.11
- zeebe/gateway-rest must expose it with `x-eventually-consistent: false` and no
  `@RequiresSecondaryStorage` — Acceptance: works with secondary storage set to `none` · Needed in: 8.11
- zeebe/gateway (only if "activate work" is chosen) must park a request until the case has work or
  the request times out — Acceptance: a parked request returns within its timeout, empty if no work
  appeared · Needed in: 8.11

### Changes in another team's code: camunda/camunda/zeebe/gateway-protocol v2 spec, camunda/camunda/clients (c8-api-team)
- [zeebe/gateway-protocol/src/main/proto/v2/user-tasks.yaml](../../../../gateway-protocol/src/main/proto/v2/user-tasks.yaml) — the new operation, or the completion option · allowed by: [guidelines § 1](../../../../../docs/rest-api-endpoint-guidelines.md#1-end-to-end-workflow), stage 2 (feature team writes, c8-api-team reviews)
- [clients/java/src/main/java/io/camunda/client/CamundaClient.java](../../../../../clients/java/src/main/java/io/camunda/client/CamundaClient.java) — the matching command · allowed by: [guidelines § 6](../../../../../docs/rest-api-endpoint-guidelines.md#6-camunda-client-extension)

## Outbound requirements
None: every component the design changes is this team's, or allows the change by a documented rule
([D4](#d4--this-team-writes-the-api-contract-and-java-client-under-the-api-guidelines-option-c-consume)).

| ID | Owner | Brief | Capabilities | Need-by | Status | Owner issue |
|---|---|---|---|---|---|---|

## Consumer impact
| Consumer | Contract | Impact | Note |
|---|---|---|---|
| Java client and Spring Boot starter users | REST API | additive | New command ([D4](#d4--this-team-writes-the-api-contract-and-java-client-under-the-api-guidelines-option-c-consume)); existing calls unchanged |
| SDKs generated from the spec (TypeScript and others) | OpenAPI spec | additive | They pick up the operation when regenerated; list unverified |
| Tasklist | User-task REST API | none | Its polling keeps working; adopting the interaction is a separate change |
| Camunda, RDBMS and ES/OS exporters; Camunda Process Test | Engine records | additive | The new intent must be ignored or handled (C10) |
| MCP gateway | REST operations | none | Not mirrored |

## Increments
<!-- Breakdown starts once the design is agreed (SKILL.md Step 6). -->
| ID | Goal | Blocked by | Status | Issue |
|---|---|---|---|---|

## Dependency view
None yet.

## Open questions
| ID | Question | Who answers | Blocking? |
|---|---|---|---|
| Q1 | Do the task and its local variables read inside one command always agree, including variables a creating listener has just set? Job activation reads job and variables that way. | this team | yes |
| Q2 | When a wait times out or a response is lost, what does the client do next? Create-with-result answers `504` without a key; a repeatable read can simply be retried. | this team | yes |
| Q3 | Which candidate hands out the work (Design table)? Settled by Q1, Q2 and Q5. "Activate work" also needs a task-available signal (this team); "ask the engine" needs a decision record superseding DR3. | this team | yes |
| Q4 | Confirm the 8.11/8.12 split (D3) and the 2026-10-20 time-box. Alpha, engine feature flag, or both? Any backport? | this team | no |
| Q5 | User-task search is eventually consistent and this interaction is not. Should the API document both labels for one entity, or avoid the mix? | c8-api-team | yes |
| Q6 | Review the endpoint contract in API governance ([guidelines § 1](../../../../../docs/rest-api-endpoint-guidelines.md#1-end-to-end-workflow)) once Q3 is settled. | c8-api-team | yes |
| Q7 | Do the existing user-task read grants fit handing out tasks on the engine's path, and does security confirm it owns that model? | camunda/identity | yes |
| Q8 | Which variables are a task's data: its local scope only (8.11), or every visible variable, as job activation returns? | product | no |
| Q9 | Confirm the non-goals marked "assumed" in Scope. | product | no |
| Q10 | Confirm the edge cases marked "assumed" in Scope. | product | no |
| Q11 | Who writes the guide page (M1) and wires the success signals (M2)? | product | no |

## Risks
- Related epics touch the same user-task API and may define a task's variables differently:
  [camunda/product-hub#3812](https://github.com/camunda/product-hub/issues/3812),
  [camunda/product-hub#3832](https://github.com/camunda/product-hub/issues/3832),
  [camunda/product-hub#3834](https://github.com/camunda/product-hub/issues/3834),
  [camunda/product-hub#3831](https://github.com/camunda/product-hub/issues/3831). None has a plan
  yet; the overlap is [user-tasks.yaml](../../../../gateway-protocol/src/main/proto/v2/user-tasks.yaml)
  and the variables returned with a task ([Q8](#open-questions)).
- Reads on the processing thread compete with commands (C4). A very large case needs a cap on how
  many tasks one call returns.
- Every ARCHITECTURE.md this plan relies on is an unreviewed draft. Owners and constraints may change
  when the teams review them.

## Changelog
- 2026-10-09 — created from [camunda/product-hub#3811](https://github.com/camunda/product-hub/issues/3811). The plan author set the release split (D3) and the time-box for the engine team to confirm (Q4); mechanism left open.
