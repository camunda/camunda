# ScheduledTask Result API — Design

Date: 2026-05-07
Author: Nico Korthout
Status: Approved for implementation
Related: PR #52604 (sibling PoC, same base branch `main`)

## Background

PR #52604 introduces a unified `ScheduledTask` API for periodic engine work. Its
shape is:

```java
interface ScheduledTask {
  String name();
  Outcome run(TaskContext context);
}
```

Side effects go through `TaskContext.sink()` (`append`, `sendInterPartition`).
Scheduling decisions are returned as an `Outcome` sealed type
(`Idle` / `YieldNow` / `AwaitDueAt`). Tasks that yield mid-iteration retain
their resume cursor (e.g. `executionTimestamp`, `DeadlineIndex startAtIndex` in
`JobTimeoutCheckScheduler`) as instance fields, and are responsible for
clearing them when iteration drains.

This design is a sibling PoC that revisits four shape choices.

## Goals

1. Return a single `Result` from `run` instead of `Outcome`. The `Result`
   carries appended commands, inter-partition sends, and the scheduling
   decision in one object.
2. Move the resume cursor from task instance state into the runtime, exposed
   on `TaskContext`. Tasks become stateless across calls.
3. Make cursor lifetime explicit at the type level: a generic parameter
   `<C>` on `ScheduledTask` and `TaskContext` documents which tasks have
   continuation state.
4. Match the file-and-test scope of PR #52604 so reviewers can compare the
   two API shapes side-by-side.

## Non-goals

- Changing `Schedule` (cadence config). Untouched.
- Changing the lifecycle adapter contract
  (`StreamProcessorLifecycleAware + Task`). Untouched.
- Changing how inter-partition sends are wired (still
  `InterPartitionCommandSender`). Untouched.
- Performance work. The change is a refactor; metric counters and yielding
  semantics carry over unchanged.

## API surface

Three files in
`zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/scheduled/api/`
change. `Schedule.java` is untouched. `Outcome.java` and `Sink.java` are
deleted.

### `ScheduledTask<C>`

```java
public interface ScheduledTask<C> {
  String name();
  Result run(TaskContext<C> context);
}
```

Tasks with no resume cursor declare `ScheduledTask<Void>`.

### `TaskContext<C>`

```java
public interface TaskContext<C> {
  InstantSource clock();
  int partitionId();
  boolean shouldYield();

  /** Cursor saved on the previous run via {@link Result.Builder#yieldNow}. */
  C resumeCursor();             // null when no cursor stored

  /** Fresh builder for the current run. */
  Result.Builder<C> result();
}
```

`resumeCursor()` returns `null` when no cursor is stored (engine-wide
convention: nulls represent missing values; `Optional` is reserved for
lambda-shaped APIs).

`sink()` is removed; appends and inter-partition sends move onto
`Result.Builder`.

### `Result` and `Result.Builder<C>`

`Result` is an immutable carrier with three internal pieces: the list of
appended commands, the list of inter-partition sends, and a `Decision`
(sealed: `Idle` / `AwaitDueAt` / `YieldNow(Object cursor)`). The cursor is
stored as `Object` inside `Decision`; the runtime
(`ManagedScheduledTask<C>`) is the only reader and casts back to `C` at the
boundary, so consumers of the API never see the cast.

The `Builder<C>` is the only construction path; `Result` has no public
factories.

```java
public final class Result { /* immutable; package-private fields read by ManagedScheduledTask */

  public interface Builder<C> {

    /**
     * Appends a local follow-up command. Returns false when the result batch is
     * full; the task should stop iterating and call {@link #yieldNow}.
     */
    boolean append(Intent intent, UnifiedRecordValue value);

    /** Same as {@link #append(Intent, UnifiedRecordValue)} with an explicit record key. */
    boolean append(long key, Intent intent, UnifiedRecordValue value);

    /** Sends a command to another partition. Best-effort, fire-and-forget. */
    void sendInterPartition(int receiverPartitionId,
                            ValueType valueType,
                            Intent intent,
                            Long recordKey,
                            UnifiedRecordValue value,
                            AuthInfo authInfo);

    /** Convenience overload without {@link AuthInfo}. */
    void sendInterPartition(int receiverPartitionId,
                            ValueType valueType,
                            Intent intent,
                            Long recordKey,
                            UnifiedRecordValue value);

    // -------- Terminals --------
    // Exactly one terminal must be called per run. They all answer the same
    // question — "when should the runtime fire me next?" — so they are
    // mutually exclusive by construction.

    /**
     * Done with this run; no specific next due-date in mind. The runtime
     * reschedules at {@code now + fallbackInterval} when configured;
     * pure on-demand schedules sleep until externally re-triggered.
     * Clears any stored resume cursor.
     */
    Result idle();

    /**
     * Done with what is currently due. The next entry is not due until
     * {@code timestampMs}. The runtime sleeps until then.
     * Clears any stored resume cursor.
     */
    Result awaitDueAt(long timestampMs);

    /**
     * The task has more work right now but is releasing the actor thread so
     * other scheduled tasks (and stream processing) get a turn before
     * continuing. The runtime reschedules at {@code now + minResolution}.
     * Saves {@code cursor} on the runtime; the next run sees it via
     * {@link TaskContext#resumeCursor()}.
     */
    Result yieldNow(C cursor);

    /**
     * Shorthand for {@link #yieldNow(Object) yieldNow(null)}. Intended for
     * tasks declared as {@code ScheduledTask<Void>}.
     */
    Result yieldNow();
  }
}
```

## Cursor lifecycle

The runtime owns the cursor. Lifecycle is implicit, tied to which terminal
the task calls:

|      Terminal      |            Cursor effect            |
|--------------------|-------------------------------------|
| `idle()`           | cleared                             |
| `awaitDueAt(ts)`   | cleared                             |
| `yieldNow(cursor)` | stored (may be `null` for `<Void>`) |
| `yieldNow()`       | stored as `null`                    |

Stream processor lifecycle events:

- `onPaused` / `onClose` / `onFailed` clear the cursor alongside
  `disableAndCancel()`. A paused task that resumes starts fresh.
- `onRecovered` / `onResumed` leave the cursor at its initial `null`. The
  first run sees an empty cursor.

This encodes the invariants in the API: it is impossible to yield without a
cursor decision, and impossible to keep a stale cursor across an
`idle()` / `awaitDueAt()` decision.

## Runtime

`ManagedScheduledTask` becomes generic on the cursor type and gains one new
field:

```java
public final class ManagedScheduledTask<C> implements StreamProcessorLifecycleAware, Task {
  private final ScheduledTask<C> task;
  // ... existing fields (schedule, sender, metrics, clock, scheduleService,
  //                      partitionId, enabled, nextRun)
  private C resumeCursor;   // null when no continuation pending
  ...
}
```

`execute(TaskResultBuilder)` shape:

1. Clear `nextRun` (unchanged).
2. Construct a `ResultBuilderImpl<C>` bound to `taskResultBuilder`,
   `interPartitionCommandSender`, and `metrics`. This replaces today's
   `RunContext`-as-Sink role.
3. Construct a `TaskContextImpl<C>` exposing `clock`, `partitionId`,
   `shouldYield`, the current `resumeCursor`, and `result()` returning the
   builder.
4. Call `task.run(ctx)`. Catch `RuntimeException` exactly as today (log,
   count, treat as `idle()`).
5. Inspect the `Result`'s scheduling decision:
   - `Idle` / `AwaitDueAt` → `resumeCursor = null`.
   - `YieldNow(c)` → `resumeCursor = c`.
6. Slow-run / metrics logging (unchanged).
7. `scheduleNext(decision)` with the same three branches as today.

The cursor lifetime equals the lifetime of the `ManagedScheduledTask`
instance — exactly the lifetime of the registered task. No accidental
persistence across instance recreation.

## Task migrations

|               Task                |       `<C>`        |                                                Notes                                                |
|-----------------------------------|--------------------|-----------------------------------------------------------------------------------------------------|
| `DueDateTimerCheckScheduler`      | `Void`             | Re-scans from start each run; no continuation.                                                      |
| `JobBackoffCheckScheduler`        | `Void`             | Returns `awaitDueAt` / `idle`; no mid-iteration yield.                                              |
| `MessageTimeToLiveCheckScheduler` | `Void`             | Single-shot expire-check.                                                                           |
| `CommandRedistributionScheduler`  | `Void`             | `retryCyclesPerDistribution` is accumulated state across runs (not a resume cursor); stays a field. |
| `JobTimeoutCheckScheduler`        | `JobTimeoutCursor` | New record bundling `(executionTimestamp, DeadlineIndex resumeFrom)`. Instance fields are deleted.  |

### Mechanical translation pattern (`DueDateTimerCheckScheduler`)

```java
public Result run(final TaskContext<Void> ctx) {
  final Result.Builder<Void> result = ctx.result();
  final long now = ctx.clock().millis();

  final long nextDueDate = timerInstanceState.processTimersWithDueDateBefore(
      now,
      timer -> {
        if (ctx.shouldYield()) return false;
        timerRecord.reset();
        // populate timerRecord ...
        return result.append(timer.getKey(), TimerIntent.TRIGGER, timerRecord);
      });

  if (nextDueDate > 0) return result.awaitDueAt(nextDueDate);
  return ctx.shouldYield() ? result.yieldNow() : result.idle();
}
```

### Cursor flow (`JobTimeoutCheckScheduler`)

```java
public Result run(final TaskContext<JobTimeoutCursor> ctx) {
  final Result.Builder<JobTimeoutCursor> result = ctx.result();
  final JobTimeoutCursor saved = ctx.resumeCursor();
  final long executionTs =
      saved != null ? saved.executionTimestamp() : ctx.clock().millis();
  final DeadlineIndex startAt = saved != null ? saved.resumeFrom() : null;

  final MutableInteger counter = new MutableInteger(0);
  final DeadlineIndex lastVisited = state.forEachTimedOutEntry(
      executionTs, startAt,
      (key, record) -> {
        if (counter.getAndIncrement() >= batchLimit || ctx.shouldYield()) {
          return false;
        }
        return result.append(key, JobIntent.TIME_OUT, record);
      });

  if (lastVisited != null) {
    return result.yieldNow(new JobTimeoutCursor(executionTs, lastVisited));
  }
  return result.idle();   // runtime clears the cursor
}

public record JobTimeoutCursor(long executionTimestamp, DeadlineIndex resumeFrom) {}
```

The previous `executionTimestamp = -1` and `startAtIndex = null` instance
fields are deleted.

## Test updates

- `FakeTaskContext` becomes `FakeTaskContext<C>`. Exposes
  `withResumeCursor(C)` for setup. After the task returns, tests inspect
  the captured `Result` via accessors that mirror the runtime's view:
  `result.appendedCommands()`, `result.interPartitionSends()`,
  `result.decision()` (returns the sealed `Decision`).
  The Sink-on-context model is gone, so `appended()` / `sends()` no longer
  live on the context.
- All 5 task tests and `ManagedScheduledTaskTest` are mechanically
  translated. Test count stays at 38.
- `ManagedScheduledTaskTest` gains tests for cursor lifecycle: `yieldNow`
  saves the cursor; `idle` clears it; `awaitDueAt` clears it; `onPaused`
  clears it.

## Branching

- Branch: `korthout-camunda-8991-scheduled-tasks-result-api`.
- Forked from the current PoC branch tip (`4ef27d30cc2`) so all existing PoC
  work is included.
- PR opened against `main` — same base as PR #52604 — so the two PoCs are
  reviewed side-by-side as alternatives, not a stack.

## Risks

- **Generic propagation.** `ManagedScheduledTask<C>` parameter ripples to its
  registration sites in `EngineProcessors`. Mitigated by Java's diamond
  inference; registration call sites change from
  `new ManagedScheduledTask(task, ...)` to `new ManagedScheduledTask<>(task, ...)`.
- **`yieldNow()` no-arg shorthand on non-`<Void>` builders.** Compiles for
  any `<C>` and stores `null` as the cursor. Documented in javadoc that this
  variant is for `<Void>` callers; non-`<Void>` callers using it on purpose
  is legal but indicates a probable bug. Not enforced by the type system.
- **`JobTimeoutCheckScheduler` semantic preservation.** The
  `executionTimestamp` must travel inside the cursor (not be recomputed) to
  preserve the "all entries within one continuation evaluated against one
  `now`" guarantee. The migration sketch above does this; covered by the
  existing test that asserts the timestamp is stable across yields.

