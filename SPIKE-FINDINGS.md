# Spike findings: B1, a broker command that correlates a message and waits for the instance result

Issue: camunda/camunda#70612. Base: `origin/main` at `2d0d3a92975`. Branch: `claude/spike-b1-correlate-result-7fe3df`. One local commit. Nothing is pushed. This is throwaway code, not mergeable.

Labels: **Verified** means a test ran or I read the exact lines. **Inferred** means I reasoned from code I read and did not run it.

## Verdict

Build the engine mirror, but only with one product decision made first. B1 for the same-partition case costs 172 production lines in 5 files. B2 costs 147 production lines in 5 files. The flip rule says "about 150 lines, about as much as B2". B1 meets that rule.

B1 has two real advantages over B2 and the gateway wait:
1. It has no `NOT_FOUND` race. The wait exists before the instance starts (Answer 2).
2. It matches create-with-result on incident, termination and timeout, so MCP and REST behave the same (Answer 4).

B1 has one real cost. A businessId that hashes to another partition cannot be awaited without a relay. The prototype rejects it. With 3 partitions, 2 of 3 random business ids are rejected (Answer 5). If the product cannot accept that, keep the gateway wait. The decision for the owner: is it acceptable that `awaitResult` and a cross-partition `businessId` are mutually exclusive in the first release?

The 172 lines also exclude the gateway request, service method and MCP handler. I estimate 150 to 250 more lines there (Inferred, my own estimate, same as in the B2 spike).

## What was built

| File | What it does | Lines added |
|---|---|---|
| `MessageCorrelationIntent.java` | New command intent `CORRELATE_AND_AWAIT_RESULT(5)` | 4 |
| `MessageCorrelationRecord.java` | New field `fetchVariables` (array, default empty). It has an accessor, no getter, so it is not in the JSON | 12 |
| `MessageCorrelationCorrelateAndAwaitResultProcessor.java` (new) | Subclass of the plain processor. Rejects a cross-partition businessId. Registers the wait | 124 |
| `MessageCorrelationCorrelateProcessor.java` | The response call moves into a `protected` method `respondToStartedInstance`. The class is no longer `final`. Behavior is the same | 11 (6 removed) |
| `MessageEventProcessors.java` | Registers the new processor for the new intent | 21 |
| Test: `CorrelateAndAwaitResultTest.java` (new), `MessageCorrelationClient.java`, golden file | 7 tests, a client helper, golden update | 287 |

Of the 124 lines in the new processor, about 40 are the licence header, imports and the 17-parameter constructor that only forwards to the parent.

## Answers

### 1. Dedicated intent and processor, plain correlate untouched?

Yes in behavior, with one small seam in the plain class (Verified).

The new intent has its own processor registration (`MessageEventProcessors`). The plain class changes only by extracting its single response call into `respondToStartedInstance`. The default body is the old call. I ran `CorrelateMessageTest`, `MessageCorrelation*Test`, `MessageSuspension*Test`, `EventAppliersTest` and the cross-partition tests together: 504 tests, 0 failures.

The task asked for a new processor class with no change to the plain one. A pure new class would copy about 300 lines of authorization and subscription code, or it would need to suppress the response of a shared `Writers`. The seam is the smaller cost. This is a judgment call, and I name it here.

Places that copy the correlation record field by field (Verified by reading):
1. `MessageCorrelationRecord.wrap` copies 7 fields. `fetchVariables` is not copied. Nothing calls it for this path.
2. `MessageSubscriptionCorrelateProcessor` line 144 builds a new record for the intermediate-event path.
3. `MessageSubscriptionRejectProcessor` line 147 does the same for rejection.
4. `DeferredMessageStartCorrelationResponse` line 60 rebuilds a record from stored state for the cross-partition reply.
5. `MessageCorrelationCorrelateProcessor` copies name, key, variables and tenant into a `MessageRecord`.

None needs the new field. B2 hit four such places because its wait request travelled between partitions. B1 on the same partition registers the wait locally, so the field never leaves the processor. The gateway `BrokerCorrelateMessageRequest` also builds the record, which is the gateway work I did not build.

### 2. Where is the wait registered? Is it atomic with instance creation?

The wait is registered inside `respondToStartedInstance`, in the same `processRecord` call that starts the instance (Verified, lines read and test run).

Order inside one processing batch: `EventHandle.triggerMessageStartEvent` writes the `ACTIVATE_ELEMENT` command (`EventHandle` line 352) and returns the new key. The plain processor then calls `respondToStartedInstance` with that key. The new processor calls `setAwaitResultRequestMetadata` there. The activate command is processed after this batch, so the process cannot complete before the wait exists. There is no `NOT_FOUND` race. This is the same pattern as `ProcessInstanceCreationCreateWithAwaitingResultProcessor` line 138.

Test: `shouldAnswerWithKeyAndRequestedVariablesWhenProcessCompletesImmediately` uses a process with only a message start event and an end event. It passes with exactly one response.

Mutation check: I removed the `setAwaitResultRequestMetadata` call. 4 of 7 tests failed (all tests that wait for a result). I restored the line, and `git diff HEAD` was empty afterward.

### 3. Does the result carry the instance key and the requested variables?

Yes (Verified). The test checks `response.getProcessInstanceKey()` against the key of the completed process element. With `fetchVariables = ["a"]` and message variables `a` and `b`, the result holds only `a`. With an empty list, it holds both (`shouldReturnAllVariablesWhenFetchVariablesIsEmpty`). The empty-list rule comes from `BpmnProcessResultSenderBehavior.collectVariables` lines 94 to 96, which I reused unchanged.

The caller gets one response: the result, not `CORRELATED`. The test verifies `tryWriteResponse` is called exactly once. The `CORRELATED` event is still written to the log, so exporters still see it.

### 4. Incident, termination, timeout, compared with create-with-result

| Case | B1 result | Create-with-result | Evidence |
|---|---|---|---|
| Completed | Result with key and variables | Same | Verified |
| Incident | No response, caller waits for its own deadline | Same, because the only send site is completion | Verified for B1 (`shouldNotAnswerWhileInstanceIsInIncident`). Create-with-result: read, not run |
| Terminated | No response | Same | Verified for B1 (`shouldNotAnswerWhenInstanceIsTerminated`). Create-with-result: read, not run |
| Gateway timeout | Caller gets no key | Same | Inferred |

`BpmnProcessResultSenderBehavior.sendResult` has one caller: `ProcessProcessor` line 109 (Verified by grep). So both mechanisms answer only on completion.

One behavior gap the MCP user will notice: on timeout the agent has no instance key. Create-with-result has the same gap, so this is parity, not a regression. The gateway can fix it only if the broker answered "still running, here is the key" at its own deadline. That needs a broker timer and new state. I did not build it. B2 and the gateway wait do not have this gap, because the key arrives first.

The wait row also stays in state until the instance ends if the caller gives up. This is the same as create-with-result (Inferred, same state column).

### 5. A businessId that hashes to another partition

The prototype rejects it before any state write, with `INVALID_ARGUMENT` (Verified, `shouldRejectWhenBusinessIdHashesToOtherPartition`). The test also covers a businessId on the same partition, which works (`shouldAwaitResultWhenBusinessIdHashesToSamePartition`).

The rejection check is coarse: it fires for any non-empty businessId on another partition, even if the message would only hit an intermediate catch event (Inferred from the code order).

What a relay would need (Inferred, estimate):
1. On the cross-partition path, the instance lives on P_B and the request lives on P_K. `DeferredMessageStartCorrelationResponse` already answers the gateway from P_K after P_B replies with `STARTED`.
2. The wait would be registered on P_B, and P_B would have to send the result back to P_K. That needs a new command on P_K that carries the result variables and the request id, with retry and duplicate handling, like the existing `STARTED` reply. P_K also has to keep the request open until that arrives.
3. Size: a new record or fields on the ask record, one processor on each side, a sender method, a scheduler for lost replies, and tests. I estimate 250 to 350 production lines and a second protocol change. This is an estimate only.

Cost of rejecting instead: a caller that sets both `awaitResult` and a businessId gets an error for about (N-1)/N of business ids, where N is the partition count. The result is hard to predict for the caller, because the outcome depends on a hash. An alternative that costs nothing in the engine: the gateway sends the plain correlate when a businessId is present and returns the key without a result. That changes the contract of the tool, so the product owner has to accept it.

### 6. Record and protocol changes

- One new enum value `MessageCorrelationIntent.CORRELATE_AND_AWAIT_RESULT(5)`. `from(5)` returns it.
- One new field `fetchVariables` on `MessageCorrelationRecord`, default empty. The record field count goes from 11 to 12.
- The golden file `RecordGoldenFilesTest` demanded an update, because it detects new properties. I copied the new source over it. After that, protocol (466 tests) and protocol-impl (620 tests) pass (Verified).
- JSON: `fetchVariables` has an accessor named `fetchVariables()`, not a getter. It is not part of the JSON. `JsonSerializableToJsonTest` passes (160 tests). `MessageCorrelationRecordValue` is unchanged. ProcessInstanceCreationRecord does the same.
- Exporters: the exporters I checked switch on value type, not on this intent (Inferred, grep only). Raw record exporters see a new command name.
- Record JSON docs: no file in the repo names `NOT_CORRELATED` or the neighbouring intents outside Java and one ADR (Verified by grep), so I found no doc to update.
- Rolling upgrade (Inferred): an old broker decodes value 5 as `Intent.UNKNOWN` through `MessageCorrelationIntent.from`. The request fails. The gateway must treat that failure as "no wait" and fall back to the plain correlate with a key-only answer. I did not test this.
- The wait state is written without an applier, as in create-with-result. A leader change drops it (Inferred, same as B2 spike).

### 7. Size

`git diff --shortstat origin/main...HEAD`:

```text
 8 files changed, 459 insertions(+), 13 deletions(-)
```

| Split | Files | Added | Removed |
|---|---|---|---|
| Production | 5 | 172 | 9 |
| Tests and golden | 3 | 287 | 4 |

B2 spike: 147 production lines in 5 files, 375 lines in total. B1: 172 production lines in 5 files, 459 lines in total. B1 is 25 production lines larger, because it builds a subclass with a long constructor. A shorter version is possible: put the wait in the plain class behind an `awaitResult` flag and cut about 90 lines, at the price of touching the plain path (Inferred).

Verdict against the flip rule: B1 is about as expensive as B2 (about 150 lines), so the engine mirror becomes the recommendation for the same-partition case. See the verdict at the top for the condition.

### 8. Cross-partition path with B1 present

Correct (Verified). The command line `./mvnw verify -pl zeebe/engine -Dtest='CorrelateAndAwaitResultTest,CreateProcessInstanceWithResultTest,MessageStartEventCrossPartition*Test,CorrelateMessageTest,MessageCorrelation*Test,MessageSuspension*Test,EventAppliersTest'` ran 504 tests with 0 failures. It includes `MessageStartEventCrossPartitionCorrelateBusinessIdTest` (2 tests), `MessageCorrelationMultiplePartitionsTest` (5), `MessageSuspensionCrossPartitionTest` (3) and `CreateProcessInstanceWithResultTest` (6). Executed counts are above zero for each class.

## What I did not check

1. The gateway request, service method and MCP handler. Sizes are estimates.
2. Authorization with security enabled. The new processor reuses the parent checks, so it needs `CREATE_PROCESS_INSTANCE`, the same as plain correlate. I did not run an authorization test for the new intent.
3. A message that correlates only to an intermediate catch event through the new intent. The caller then receives the plain `CORRELATED` answer from the existing subscription path, not a result (Inferred from `MessageSubscriptionCorrelateProcessor`). A real version has to reject or handle this.
4. A message that matches several start events. As in plain correlate, only the first start subscription gets the wait (Inferred).
5. The create-with-result termination and incident cases were read, not run.
6. Rolling upgrade with a real old broker.
7. The relay. It is described and estimated, not built.
8. Heap and state size of waiters, and any limit on concurrent waits.
9. Business id uniqueness together with a wait (the uniqueness code runs before the seam, so I expect it to work).
10. No cluster, container or server was started. Engine tests ran in the in-process `EngineRule` only.

## Commands run

```text
./mvnw install -pl zeebe/engine -am -Dquickly -T2                         BUILD SUCCESS
./mvnw verify -pl zeebe/engine -Dtest='CorrelateAndAwaitResultTest,CreateProcessInstanceWithResultTest,MessageStartEventCrossPartitionCorrelateBusinessIdTest'   15 run, 0 failed
mutation (call removed): CorrelateAndAwaitResultTest                      7 run, 4 failed
./mvnw verify -pl zeebe/engine -Dtest='<8 patterns above>'                504 run, 0 failed
./mvnw verify -pl zeebe/protocol,zeebe/protocol-impl                      466 and 620 run, 0 failed (after golden update)
```
