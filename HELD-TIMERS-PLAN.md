# Held timers — implementation plan

Status: local spike, in progress on branch `feat/hold-timers`. Not pushed, not
reviewed. Changes a public API contract and adds a public command, so it needs sign-off and most
likely an ADR before it becomes a PR.

Companion to `RESERVED-JOBS-PLAN.md` (jobs an instance holds back from every worker) and
`STUBBED-CALL-ACTIVITIES-PLAN.md` (call activities that wait on a stand-in job). This is the third
switch of the same recording problem, for the one wait state neither of those two reaches: a
**timer**.

## Problem

Test Studio records a CPT-compatible test by driving a real cluster: it creates a process instance,
and after each step the user authors the CPT equivalent of what just happened and appends it to a
test file. Reserved jobs solved this for jobs — a worker would otherwise grab and complete the job
before the user authored `completeJob`. A timer has the same failure mode with a different cause.

A timer fires when its due date is reached. On a live cluster the clock runs in real time, so a
short timer (`PT5S`, a boundary retry, an escalation deadline) reaches its due date and the engine's
`DueDateTimerCheckScheduler` fires it before the user can author the step. The recorded file then
silently lacks that transition — and a test with a missing step is worse than no test, because it
still passes.

The levers that exist today both miss the mark:

- **Pin the cluster clock** (`POST /v2/clock`). Freezes every timer on the cluster, not just this
  instance's. Cluster-wide, exactly the objection that killed "turn the workers off" for reserved
  jobs — two people cannot record side by side, and everyone else's timers stall.
- **Suspend the process instance.** Per-instance, and it does buffer a due timer
  (`TimerSuspensionGateTest.shouldBufferDueTimerAndFireOnResume`), but resume fires every buffered
  timer at once — pause, not step — and a suspended instance's jobs cannot be completed
  (`JobCompleteProcessor.onSuspended` rejects), so the recorder cannot drive the instance while it
  holds its timers. Unusable for interleaved recording.

The isolation has to be per process instance, and it has to leave the rest of the instance drivable.

## Solution

Two halves, mirroring reserved jobs:

1. **Hold** — creating a process instance with **`holdTimers`** keeps every timer that instance (and
   the child instances its call activities start) creates out of the due-date scheduler. A held
   timer is created and stored as usual, is visible in the element-instance API as a timer wait
   state, and never fires on its own.
2. **Fire** — a new public command, `POST /v2/process-instances/{processInstanceKey}/timers/trigger`
   with the element id in the body, fires exactly one held timer on demand. The recorder addresses
   the timer by the instance and the BPMN element id it sees on the canvas; the engine resolves the
   concrete timer and fires it. It rejects if no held timer — or more than one — matches that
   element.

Together they give the reserved-jobs property for timers: the recorder drives the timer itself while
every other instance on the cluster keeps firing its timers normally, and two people can record side
by side.

### Why this shape

The engine already skips a wait state from a scan by keeping it out of the index the scan reads:
process-instance suspension holds a timer back by deleting its entry from the `TIMER_DUE_DATES`
column family (`DbTimerInstanceState.suspend`) while leaving the timer itself in `TIMERS`. Holding a
timer follows that same shape from the other end — a held timer is **never inserted** into
`TIMER_DUE_DATES`, only into `TIMERS`. The scheduler scans `TIMER_DUE_DATES`, so it never sees a
held timer; the trigger command loads it from `TIMERS` by key. No change to the scan, no per-tick
cost, no new "withheld" timer state.

Firing reuses the core of what is already there. `TimerTriggerProcessor` already fires exactly one
timer and deliberately does **not** check the due date (it validates only that the timer exists and
its element is still active) — so firing a held timer early is a supported operation. The append-
TRIGGERED / activate-element / reschedule core is untouched. The processor gains a **second entry
path**: a scheduler-issued command still addresses the timer by key and runs the original path,
while a client-issued command addresses it by `(processInstanceKey, elementId)`, so the processor
resolves the concrete held timer from the held-timer index, authorizes the caller, hydrates the
command from the stored timer, and writes a response.

### The switch is a boolean, not a runtime instruction, not a token

`holdTimers` is a top-level boolean on `CreateProcessInstanceInstruction`, exactly like
`stubCallActivities` and for the same reason: a `HOLD_TIMERS` runtime-instruction subtype would be
an empty object whose only content is its own type. An instruction carries a payload by
construction; a hold-everything switch does not.

It is **not** a token of its own, either. The trigger command is not fenced by a token at all — it
is authorized like any other process-instance command (`PROCESS_DEFINITION` /
`UPDATE_PROCESS_INSTANCE`), so only a caller already permitted to update that instance can fire its
held timers. That reuses the standard authorization path instead of minting a recording-specific
credential.

### Inherited by children

`holdTimers` is read at the **root** instance, so a called process's timers are held too, without
the recording having to repeat the switch on every call activity — the same inheritance reserved
jobs and stubbed call activities already have, and the same root-instance read
(`getJobReservationTokenFromProcessInstance` in `BpmnJobBehavior`).

### Alternatives rejected

|                    Option                    |                                                         Why not                                                          |
|----------------------------------------------|--------------------------------------------------------------------------------------------------------------------------|
| Pin the cluster clock                        | Cluster-wide; freezes every instance's timers; two people cannot record at once                                          |
| Process-instance suspension                  | Per-instance but resume fires all buffered timers at once (pause, not step), and blocks job completion while held        |
| Per-instance / per-token clock offset        | Airtight and preserves real due dates, but adds a clock dimension to timer state and the scheduler — the heaviest option |
| A `HOLD_TIMERS` runtime instruction + token  | An empty instruction in a costume, and a second credential for a session that already has one                            |
| A new "withheld" timer state + column family | The suspension shape already gives "not in the due-date index" for free; a new state re-checks on every reschedule path  |

## What changes

### Protocol

- `ProcessInstanceCreationRecord` — new `holdTimers` boolean (beside `stubCallActivities`).
- `ProcessInstanceRecord` — same boolean, read at the root instance, **not** copied in `wrap(...)`
  so only the root element-instance record carries it (exactly as `stubCallActivities` is).
- `TimerRecord` — new `held` boolean, stamped at subscription time and carried onto the stored
  timer so it survives replay and repeating-timer reschedule.
- Value interfaces gain `default` getters (`ProcessInstanceCreationRecordValue`,
  `ProcessInstanceRecordValue`, `TimerRecordValue`) so existing implementors keep compiling.
- Golden files regenerated for the three changed records (literal source copies — re-copy after any
  `spotless:apply`).

### Engine

|                   Concern                   |                                                                                                                Where                                                                                                                |
|---------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Read the switch off the create block        | `ProcessInstanceCreationHelper.holdTimersOf`, beside `jobReservationTokenOf`; both create processors pass it                                                                                                                        |
| Store it on the instance at creation        | `ProcessInstanceCreationHelper.initProcessInstanceRecord`, beside `stubCallActivities`                                                                                                                                              |
| Stamp `held` on every timer                 | `CatchEventBehavior.subscribeToTimerEvent`, reading the **root** instance's `holdTimers` — the reservation-token shape                                                                                                              |
| Carry `held` into stored timer state        | `TimerCreatedApplier`, `TimerCreatedV2Applier`, `TimerInstanceMigratedApplier` copy it onto `TimerInstance`                                                                                                                         |
| Keep held timers out of the scheduler       | `DbTimerInstanceState.store` inserts a held timer into `TIMERS` but not into `TIMER_DUE_DATES`; `resume` skips a held timer for the same reason                                                                                     |
| Resolve a held timer by (instance, element) | New `HELD_TIMER_BY_PROCESS_INSTANCE` column family in `DbTimerInstanceState`, keyed `(processInstanceKey, elementInstanceKey, timerKey)`; a prefix scan on the instance loads each held timer and matches the element id against it |
| Fire one held timer                         | `TimerTriggerProcessor` — core firing unchanged, but it gains a client-command path: resolve the held timer, authorize the caller, hydrate the command, write a response. The scheduler path is untouched                           |

#### Why the element id is not in the index key

The obvious key is `(processInstanceKey, elementId) → (elementInstanceKey, timerKey)` — the
trigger command's own addressing, resolvable with a single prefix scan. It is wrong, because
`elementId` is **mutable**: `TimerInstanceMigratedApplier` rewrites `handlerNodeId` and calls
`DbTimerInstanceState.update`, which only touches `TIMERS`. The index entry keyed by the old
element id is then orphaned for good — `remove` later wraps the *new* element id, so its
`deleteIfExists` no-ops — the timer is unreachable under its new element id, and a later held timer
on the old element id reads as `AMBIGUOUS` forever.

Keying only on fields that are immutable for the life of a timer removes the failure mode instead
of adding a fourth site that has to remember to maintain the index. `processInstanceKey`,
`elementInstanceKey` and `timerKey` never change (migration touches `handlerNodeId` and
`processDefinitionKey`, and `update` is the only mutation path in the engine). The element id
becomes a read-time predicate against the stored timer, where it is always current. Cost is a scan
of the instance's held timers — a handful, for a command a person issues by hand.

The alternative that would have removed the index entirely is addressing the trigger by
`(elementInstanceKey, timerKey)`. Rejected: Test Studio can read `elementInstanceKey` from
`/v2/element-instances/search`, but there is no timers search API, so it has no way to learn a
`timerKey`. Keeping element-id addressing is what keeps the feature usable by its only consumer —
at the cost of open item 4 below, which key addressing would have dissolved.

Two consequences, both handled:

- **Repeating held timers stay held.** `TimerTriggerProcessor.rescheduleTimer` calls
  `subscribeToTimerEvent` again for the next occurrence; the root instance still carries
  `holdTimers`, so the reschedule is held too and stays out of the scheduler. No special case.
- **The reschedule computes the next due date from the original due date**
  (`refreshTimer` uses `withStart(record.getDueDate())`), not from `now`. For a held timer this is
  irrelevant — the next occurrence is held regardless of when it is due — but it is the reason a
  future revocation of the hold (see open items) must recompute due dates before re-inserting them
  into `TIMER_DUE_DATES`.

### Interaction with process-instance suspension

Suspension and the hold both work by removing a timer from `TIMER_DUE_DATES`, so the two have to
agree on who puts entries back:

- `DbTimerInstanceState.resume` skips a held timer. Without this, suspending and resuming a
  `holdTimers` instance would insert a due-date entry the timer never had and hand every held timer
  to the scheduler — the feature would silently stop working.
- `TimerTriggerProcessor.onSuspended` / `onResuming` return `REJECT` for a non-internal command.
  The existing `BUFFER` behavior is correct for the scheduler's own trigger, which carries a timer
  key and a due-date entry to suspend; a client trigger carries neither, and buffering it means the
  REST caller waits for a resume that may never come. The scheduler path keeps its old behavior.

### Authorization

The trigger command uses `cslCheck.checkAuthorizationAndTenant(...)` — the same call
`ProcessInstanceCancelProcessor` makes — with `PROCESS_DEFINITION` / `UPDATE_PROCESS_INSTANCE` and
the resolved timer's `tenantId`. The tenant rejection is `NOT_FOUND`, not `FORBIDDEN`, so the
endpoint cannot be used to probe whether a held timer exists in a tenant the caller has no access
to. A permission-only check would let a principal holding `UPDATE_PROCESS_INSTANCE` on a
same-`bpmnProcessId` definition in one tenant fire another tenant's timer.

### API (REST v2 only)

`process-instances.yaml` — a new top-level `holdTimers` boolean on `CreateProcessInstanceInstruction`,
alpha, beside `stubCallActivities`:

```
POST /v2/process-instances
{ "processDefinitionId": "my-process", "reserveJobs": ..., "holdTimers": true }
```

`timers.yaml` (new bundle file) — `POST /v2/process-instances/{processInstanceKey}/timers/trigger`,
alpha, with the element id in the body:

```
POST /v2/process-instances/{processInstanceKey}/timers/trigger
{ "elementId": "my-timer-catch-event" }
```

- Referenced from `rest-api.yaml` in a new `timers` block.
- `RequestMapper.toTimerTriggerRequest` → new `TimerServices.triggerTimer` → `BrokerTriggerTimerRequest`.
- The broker request carries `(processInstanceKey, elementId)` and routes to the partition owning the
  instance; the engine resolves the concrete `(elementInstanceKey, timerKey)` from the held-timer
  index, hydrates the timer record, and writes `TimerIntent.TRIGGERED`.
- Authorization: standard `PROCESS_DEFINITION` / `UPDATE_PROCESS_INSTANCE`, plus the tenant check —
  only a caller permitted to update that instance, in its tenant, can fire its held timers. No token.
- Rejections: `404 NOT_FOUND` when no held timer matches the (instance, element), and for a caller
  outside the owning tenant; `409 INVALID_STATE` when more than one held timer matches the element,
  or when the instance is suspended; `401 / 403` for an unauthorized caller.

The command carries no clock change, so no other instance is affected — that is the isolation.

## Tests

- `zeebe/engine/.../processing/timer/HeldTimersTest.java` — held timers are stored but never fire on
  a clock advance, a plain `increaseTime` past the due date leaves a held timer waiting, the trigger
  command fires exactly one held timer, a child instance's timer inherits the hold, a repeating held
  timer's next occurrence is also held, the hold survives a suspend/resume cycle, a client trigger
  against a suspended instance is rejected rather than buffered, and the hold survives log replay.
- `TimerInstanceStateTest` — at the state level: a held timer still resolves after a migration
  rewrites its element id (and no longer resolves under the old one), `resume` does not schedule a
  held timer, and `resume` still schedules an ordinary suspended one.
- `TimerTriggerProcessor`'s new client-command path (resolve by (instance, element), reject an
  unknown element, and the standard-authorization check) is covered by `HeldTimersTest` and the REST
  mapping tests.
- `ProcessInstanceCreationHelperTest`, `JsonSerializableToJsonTest`, `RecordGoldenFilesTest` updated
  for the new properties.
- REST mapping tests for the create switch and the trigger command.

## Java client and CPT support

Sibling of `JAVA-CPT-MOCKING-PLAN.md`, which did this for reserved jobs and stubbed call
activities. Same shape, one switch and one command instead of two switches and four commands.

The REST step that plan needed first — referencing the new path from `rest-api.yaml` so the
generator emits a request model — is already done here: `timers.yaml` is referenced from
`rest-api.yaml`, and `TimerTriggerRequest` is generated into
`clients/java/target/generated-sources/`. So this starts at the client.

### Step 1 — Java client

|               Command               |                     Addition                      |
|-------------------------------------|---------------------------------------------------|
| `CreateProcessInstanceCommandStep3` | `holdTimers(boolean)`                             |
| `CamundaClient`                     | `newTriggerTimerCommand(long processInstanceKey)` |

`holdTimers` mirrors `stubCallActivities` exactly: it writes only `httpRequestObject`, sets
`restOnlyProperty`, and `send()` throws over gRPC rather than dropping the switch. `gateway.proto`
has no counterpart and this work does not add one.

`TriggerTimerCommandStep1` follows `ReleaseJobCommandImpl`, the repo's precedent for a REST-only
command: no gRPC stub, `send()` goes straight to `httpClient`, and a 204 maps to a void-shaped
`TriggerTimerResponse`. It is a two-step command —

```java
camundaClient.newTriggerTimerCommand(processInstanceKey).elementId("escalation").send();
```

— because the element id is the entire request body and a trigger without one is never valid, the
same reason `withJobReservationToken` returns the final step on the release command.

There is no overload taking an element instance key or a timer key. The endpoint is keyed by
`(processInstanceKey, elementId)`, and nothing above the engine can obtain a timer key: there is no
timers search API.

### Step 2 — CPT JSON test-case format

Module `testing/camunda-process-test-json-test-cases`.

One optional boolean on `CreateProcessInstanceInstruction`, defaulting to `false`, beside
`reserveJobs` and `stubCallActivities`:

```json
{
  "type": "CREATE_PROCESS_INSTANCE",
  "processDefinitionSelector": { "processDefinitionId": "order-fulfillment" },
  "holdTimers": true
}
```

One new instruction type:

|      Type       |                   Payload                    |                     Replays as                     |
|-----------------|----------------------------------------------|----------------------------------------------------|
| `TRIGGER_TIMER` | `processInstanceSelector`, `elementSelector` | `newTriggerTimerCommand(key).elementId(id).send()` |

It carries a **process instance selector as well as an element selector**, unlike the three
call-activity instructions, which take an element selector alone. Those resolve to a job, and
`JobSelector` already carries a process definition id; a timer is addressed by an instance key,
which only a process instance selector can produce. `UpdateVariablesInstruction` is the precedent —
it pairs the two selectors for exactly this reason.

The element must be selected by **`elementId`**, not `elementName`. This is the same constraint
`buildCallActivityStubJobSelector` imposes, for a related reason: the endpoint takes the BPMN
element id of the timer catch event, and for a **boundary** timer that id belongs to the boundary
event while the element instance belongs to the activity it is attached to. Resolving a name
through the element-instance API would therefore find nothing for the commonest held-timer shape.
Passing the id straight through is both simpler and the only thing that works for boundary timers.

### Step 3 — CPT runtime

Module `testing/camunda-process-test-java`.

**Public context API.** `CamundaProcessTestContext` gains two overloads:
`triggerTimer(ProcessInstanceSelector, String elementId)`, mirroring the
`updateVariables(ProcessInstanceSelector, …)` style, and
`triggerTimer(long processInstanceKey, String elementId)`. The key overload is the primitive: a
mutation addresses exactly one instance, and the key is the only thing that says which. A Java
caller holds it already, from the `ProcessInstanceEvent` its own create returned.

**Eventual consistency.** Both overloads issue the trigger *inside* an await block, exactly as
`completeJob` does. `AwaitilityBehavior.untilAsserted` is configured with
`ignoreExceptionsInstanceOf(ClientException.class)`, and a REST rejection surfaces as
`ProblemException extends ClientHttpException extends ClientException` — so a `404` because the
held timer has not been created yet is retried rather than failing the test. The retry is about
timer-creation lag, not instance lookup, so the key overload keeps it even though it has nothing to
resolve. No new await plumbing is needed, and no element-instance lookup either.

**Handler.** `TriggerTimerInstructionHandler` in `impl/testCases/instructions/`, registered in
`TestCaseInstructionHandlerRegistry`. It reads the element id through a new
`InstructionSelectorFactory.buildTimerElementId`, which throws when only `elementName` is set, and
resolves the process instance through `CreatedProcessInstanceRegistry` rather than through
`InstructionSelectorFactory.buildProcessInstanceSelector`.

**Why the registry and not a selector.** A selector is a query: it asks the cluster for instances
matching a process definition id, sorted oldest first, and takes the first. That is sound for an
assertion and wrong for a mutation, which must address one instance and no other. Three ways it
picks the wrong one:

- an instance of an earlier test case in the same JUnit method — data is only deleted in
  `afterEach`, and `CamundaDataSource`'s `startDate >= testCaseStartTime` cutoff is set per test
  method, not per test case;
- an instance belonging to somebody else running against the same cluster in `REMOTE` mode — the
  job reservation token fences job workers off, not searches;
- a second instance the same test case created on purpose.

A held timer only exists on an instance created with `holdTimers`, and only
`CREATE_PROCESS_INSTANCE` sets that, so the runner created every instance a `TRIGGER_TIMER` may
legitimately address. Resolving from what it recorded is therefore not an optimization but the
complete and exact set. The first two cases become structurally unreachable; the third is reported
(see out of scope).

**Registry.** `CreatedProcessInstanceRegistry` in `impl/testCases/`, owned by
`CamundaTestCaseRunner` and cleared per test case. `CreateProcessInstanceInstructionHandler`
records process definition id, instance key, whether the instance holds its timers, and whether it
is isolated. It is a `CopyOnWriteArrayList` because a `CONDITIONAL_BEHAVIOR` action creates
instances on the conditional behavior engine's background threads while the main thread resolves.
Both handlers take the registry as a constructor argument and have no no-arg constructor: a default
would give each handler a private registry, so the create would record into one and the trigger
resolve against another.

**Teardown.** A held-timer instance parks exactly like a reserved one: its timer never fires on its
own, so if a test fails before triggering it, the instance sits on a shared or remote cluster
forever. `CreateProcessInstanceInstructionHandler` records every instance it creates, and
`CamundaTestCaseRunner` cancels the isolated ones in a `finally`. `holdTimers` joins that condition
— `isolated = reserveJobs || stubCallActivities || holdTimers` — and the flag rides along on the
registry entry, which replaced the earlier `isolatedInstanceListener`.

**Conflict detection.** `holdTimers` together with `INCREASE_TIME` or `SET_TIME` is the analogue of
the `reserveJobs` + `MOCK_JOB_WORKER_*` conflict, with a milder failure mode: nothing starves, the
held timer simply does not fire and the next assertion times out. Same treatment — warn at parse
time, name the combination in the await-timeout message, do **not** reject the run. A test case may
legitimately hold one instance's timers while advancing the clock for another, so an up-front
rejection would be a false positive.

### Out of scope

- **gRPC.** REST-only, carried over from the engine work above.
- **A revoke/unhold instruction.** There is no engine command to revoke a hold (open item 3), so
  there is nothing for the client or the format to expose. This is where the timer story is
  genuinely narrower than reserved jobs, which do have `RELEASE_JOB`.
- **Ambiguity on multi-instance and parallel branches.** Within one instance, two live held timers
  on one element id reject with `409 INVALID_STATE` at the engine and the instruction replays as a
  failure. This is the CPT-level shadow of open item 4, and the same deferral
  `JAVA-CPT-MOCKING-PLAN.md` already books for the missing occurrence index on `JobSelector`.
- **Two instances of the same process in one test case.** The registry holds both and reports the
  ambiguity with their keys instead of picking one, so the case is detected but not yet writable.
  Resolving it means naming an instance: an alias on `CREATE_PROCESS_INSTANCE` that later
  instructions reference, which is the only way a JSON author can point at an instance whose key
  does not exist until run time. Deferred until a test case needs it — the registry is what makes
  it a small change.
- **A held timer inside a called process.** The hold propagates from the root instance, but the
  index entry lands under the child's own key, and the runner never recorded the child. Resolution
  reports that the timer must belong to an instance the test case created and that a called
  process's timer is not addressable yet. Resolving it means a query scoped to the recorded root,
  `byParentProcessInstanceKey`.
- **The selector path for other instructions.** `updateVariables` and `updateLocalVariables` still
  resolve through `awaitProcessInstance`, which takes the first match and applies no state filter —
  so both hit all three failures described under "Why the registry and not a selector". Filtering
  to active instances (as `findElementInstance` and incident resolution already do) and failing on
  more than one match belongs there, but it changes the behavior of two shipped methods and is a
  separate concern from this switch.
- **End-to-end coverage on a running engine.** Same blocker as the sibling plan: CPT `MANAGED` mode
  pulls `camunda/camunda:SNAPSHOT`, which does not contain these engine changes, so a `TestCasesIT`
  case would fail for the wrong reason. Coverage is unit-level; an acceptance test that builds a
  cluster from source is still to do.

### As built

Every step above landed. Where the implementation departed from the plan:

- **`awaitProcessInstance`'s failure message was generalized.** It read "Expected to update
  variables for process instance [%s] but no process instance is available." — accurate while
  variables were its only caller, wrong the moment the timer trigger shares it. Now "Expected to
  act on process instance [%s] …", and `UpdateVariablesTest.shouldFailIfNoProcessInstanceIsPresent`
  was updated with it.
- **The conflict warning names the clock instructions, not a starve.** Unlike the reserved-jobs
  warning, nothing is hidden from anything here: `holdsTimersAndMovesTheClock` fires when a test
  case both holds timers and issues `INCREASE_TIME` or `SET_TIME`, and the message points at
  `TRIGGER_TIMER`.
- **No new context state.** Reserved jobs needed a per-run token on `CamundaProcessTestContext`;
  the hold needs nothing — the switch is a boolean and the trigger is authorized like any other
  process-instance command.
- **`CamundaProcessTestContextImpl.triggerTimer` has its own test.** The sibling work covered only
  the handlers against a mocked context; `TriggerTimerTest` also drives the context method against
  a mocked data source, because the retry-on-404 reasoning lives there and nowhere else.
- **The DSL resolves the instance itself.** The handler was planned as a thin selector builder and
  is not: it reads the key out of `CreatedProcessInstanceRegistry`. The plan assumed a selector
  could address an instance for a mutation; it cannot, and the reasoning is written up under
  step 3. The selector-shaped payload in the JSON format is unchanged — only how the runner turns
  it into a key.
- **`CreatedProcessInstanceRegistry` replaced the `isolatedInstanceListener`.** The listener passed
  a bare key, which is enough to cancel an instance on teardown but not to address one. The
  registry carries the same keys plus the process definition id and the two flags, and serves both
  purposes.

### Verification

Baseline first — `-Dquickly` without `clean` leaves stale generated sources that hide a broken
model behind a green build:

```bash
./mvnw clean install -Dquickly -T1C
./mvnw verify -pl clients/java -DskipTests=false -Dquickly -T1C
./mvnw verify -pl testing/camunda-process-test-json-test-cases -DskipTests=false -Dquickly -T1C
./mvnw verify -pl testing/camunda-process-test-java -DskipTests=false -Dquickly -T1C
```

### Commits

1. `feat: let a client hold a process instance's timers and trigger them on demand` (client)
2. `feat: record a held timer's trigger in a test case` (format)
3. `feat: replay a triggered held timer` (runtime)

## Open items before this could be a PR

These are in addition to the ones `RESERVED-JOBS-PLAN.md` lists, which apply here too.

1. **gRPC is untouched — REST only**, for the same reason as both sibling switches. The Java client
   and CPT gaps this item used to name are closed by the section above: `holdTimers` on the create
   command, `newTriggerTimerCommand`, and a `TRIGGER_TIMER` CPT instruction. What remains is that a
   caller who opted into gRPC gets an `UnsupportedOperationException` from `send()` rather than a
   gRPC form of either. Acceptable for the same reason as the siblings: REST is the client default,
   and the switch only matters in CPT's `REMOTE` mode, where the clock runs for real.
2. **The early-fire race is narrowed, not closed, without the hold.** The trigger command alone only
   wins the race if the recorder reaches it before the due date; `holdTimers` is what removes the
   race. They ship as a pair.
3. **Revoking a hold** (the timer analogue of releasing a reserved job) is not implemented. A held
   timer can only be fired, not handed back to the scheduler to fire on its real due date. Making the
   hold revocable means recomputing the due date (see the reschedule note above) and inserting it
   back into `TIMER_DUE_DATES` — a separate, larger design. Test Studio does not need it: a recorded
   test fires its timers explicitly.
4. **Selector ambiguity on multi-instance / parallel branches**, inherited verbatim from the CPT
   plan: an element id cannot tell apart two live held timers on the same element. Because the
   command is keyed by `(processInstanceKey, elementId)`, it hits this too — it rejects with
   `409 INVALID_STATE` rather than guessing. Disambiguating it means an optional
   `elementInstanceKey` on the request: `TIMERS` is already keyed by
   `(elementInstanceKey, timerKey)`, so supplying it skips `resolveHeldByProcessElement` entirely
   and needs no new index. Future work; the recording format above it has the same open question.
5. The same ADR and alpha-status decisions as the rest of the branch.

