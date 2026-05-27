# ScheduledTask Result API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor the existing PoC `ScheduledTask` API so `run()` returns a unified `Result` (commands + inter-partition sends + scheduling decision) instead of using a separate `Sink` + `Outcome`, and move the resume cursor from per-task fields onto the runtime, exposed on `TaskContext<C>`.

**Architecture:** Replace `Outcome` and `Sink` with one immutable `Result` carrier built via a `Result.Builder<C>` exposed on `TaskContext<C>`. Builder offers `append` / `sendInterPartition` for side effects and three terminal methods (`idle()` / `awaitDueAt(ts)` / `yieldNow(cursor)`) that produce the `Result`. The cursor lifetime is owned by `ManagedScheduledTask<C>`: `yieldNow(c)` saves it, `idle()` / `awaitDueAt()` / lifecycle transitions (`onPaused`, `onClose`, `onFailed`) clear it. The next run reads it back via `TaskContext.resumeCursor()` (null when unset). Five existing tasks migrate (four declared `<Void>`, `JobTimeoutCheckScheduler` declared `<JobTimeoutCursor>`).

**Tech Stack:** Java 21, Maven, JUnit 5, Mockito, AssertJ, Micrometer. Module: `zeebe/engine`.

**Branch:** `korthout-camunda-8991-scheduled-tasks-result-api` (already created from `4ef27d30cc2`; design spec already committed as `7ae99c22936`). PR base: `main`.

**Spec:** `docs/superpowers/specs/2026-05-07-scheduled-task-result-api-design.md`.

---

## File Inventory

**New files (created in this plan):**
- `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/scheduled/api/Result.java`
- `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/job/JobTimeoutCursor.java`

**Modified files:**
- `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/scheduled/api/ScheduledTask.java`
- `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/scheduled/api/TaskContext.java`
- `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/scheduled/runtime/ManagedScheduledTask.java`
- `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/timer/DueDateTimerCheckScheduler.java`
- `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/job/JobBackoffCheckScheduler.java`
- `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/job/JobTimeoutCheckScheduler.java`
- `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/message/MessageTimeToLiveCheckScheduler.java`
- `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/distribution/CommandRedistributionScheduler.java`
- `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/EngineProcessors.java` (lines 130, 767)
- `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/job/JobEventProcessors.java` (lines 58, 65)
- `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/message/MessageEventProcessors.java` (line 152)
- `zeebe/engine/src/test/java/io/camunda/zeebe/engine/processing/scheduled/runtime/FakeTaskContext.java`
- `zeebe/engine/src/test/java/io/camunda/zeebe/engine/processing/scheduled/runtime/ManagedScheduledTaskTest.java`
- `zeebe/engine/src/test/java/io/camunda/zeebe/engine/processing/timer/DueDateTimerCheckSchedulerTest.java`
- `zeebe/engine/src/test/java/io/camunda/zeebe/engine/processing/job/JobBackoffCheckSchedulerTest.java`
- `zeebe/engine/src/test/java/io/camunda/zeebe/engine/processing/job/JobTimeoutCheckSchedulerTest.java`
- `zeebe/engine/src/test/java/io/camunda/zeebe/engine/processing/message/MessageTimeToLiveCheckSchedulerTest.java`
- `zeebe/engine/src/test/java/io/camunda/zeebe/engine/processing/distribution/CommandRedistributionSchedulerTest.java`

**Deleted files:**
- `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/scheduled/api/Outcome.java`
- `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/scheduled/api/Sink.java`

---

## Conventions for All Tasks

- **Module-scoped builds.** Build/run from the repo root with `-pl zeebe/engine -am -T1C`. Never run repo-wide builds.
- **Format before every commit.** Run `./mvnw license:format spotless:apply -pl zeebe/engine -T1C`. CI's `Java checks` job fails otherwise.
- **Conventional commit messages.** `<type>: <description>`. Header ≤ 50 chars (push details to body). No scope. Types used in this plan: `refactor`, `test`, `ci`. Co-authored-by trailer: `Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>`.
- **One commit per task** in this plan. After the per-task verification passes, commit only the files that task modified.
- **Refactor TDD shape.** This is a refactor of green code, not new behavior. Each migration task: translate the test first, confirm it fails to compile (proves the old API usage is gone), translate the implementation, run the test, confirm green. The "fail" step is a compile error, not a test assertion failure — that's expected for a refactor.

Use this exact `git commit -m` heredoc form for every commit:

```bash
git commit -m "$(cat <<'EOF'
<type>: <header ≤50 chars>

<body explaining why; wrap at 72>

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 1: Create `Result` and `Result.Builder<C>`

**Files:**
- Create: `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/scheduled/api/Result.java`

Defines the immutable carrier (`Result`) that bundles the appended commands, inter-partition sends, and the scheduling decision. The `Decision` sealed type matches today's `Outcome` shape (`Idle` / `AwaitDueAt` / `YieldNow`) but `YieldNow` carries the cursor as `Object` so the type stays out of `Result` itself; `ManagedScheduledTask<C>` casts at the boundary. `Builder<C>` is the only construction path. Accessors used by tests (`appendedCommands()`, `interPartitionSends()`, `decision()`) live on `Result`. Builder construction is left to `ManagedScheduledTask` and `FakeTaskContext` — no public factory on `Result`.

- [ ] **Step 1: Create `Result.java`**

Write this exact content:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.scheduled.api;

import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.util.List;

/**
 * Single output of one {@link ScheduledTask#run} call. Carries the appended local commands, the
 * inter-partition sends, and the scheduling {@link Decision} that tells the runtime when to fire
 * next.
 *
 * <p>Construct via {@link Builder} obtained from {@link TaskContext#result()}. Each builder
 * terminal ({@link Builder#idle}, {@link Builder#awaitDueAt}, {@link Builder#yieldNow}) returns
 * the finished {@code Result}; exactly one terminal must be called per run.
 */
public final class Result {

  private final List<AppendedCommand> appendedCommands;
  private final List<InterPartitionSend> interPartitionSends;
  private final Decision decision;

  Result(
      final List<AppendedCommand> appendedCommands,
      final List<InterPartitionSend> interPartitionSends,
      final Decision decision) {
    this.appendedCommands = List.copyOf(appendedCommands);
    this.interPartitionSends = List.copyOf(interPartitionSends);
    this.decision = decision;
  }

  /** Local follow-up commands appended during the run, in order. */
  public List<AppendedCommand> appendedCommands() {
    return appendedCommands;
  }

  /** Inter-partition sends issued during the run, in order. */
  public List<InterPartitionSend> interPartitionSends() {
    return interPartitionSends;
  }

  /** Scheduling decision: when the runtime should fire the task next. */
  public Decision decision() {
    return decision;
  }

  /**
   * The fluent builder a {@link ScheduledTask} uses to record side effects and pick the next
   * scheduling decision. The cursor type {@code C} is the task's resume-state shape; tasks with no
   * continuation declare {@code Builder<Void>} and use the no-arg {@link #yieldNow()} variant.
   *
   * <h3>Usage rules</h3>
   *
   * <ul>
   *   <li>Exactly one terminal ({@link #idle}, {@link #awaitDueAt}, {@link #yieldNow}) must be
   *       called per run. They are mutually exclusive — they all answer "when next?".
   *   <li>{@link #yieldNow(Object)} releases the actor thread <em>and</em> declares "I have more
   *       work, resume me from this cursor"; the runtime reschedules immediately. It is therefore
   *       not combinable with {@link #awaitDueAt(long)} (which says "I'm done with what's
   *       currently due, wake me at this absolute time"). {@link #idle()} similarly says "done for
   *       now"; combining either with yield would be contradictory.
   *   <li>{@link #append} returning {@code false} means the result batch is full; the task should
   *       stop iterating and call {@link #yieldNow}.
   * </ul>
   */
  public interface Builder<C> {

    /**
     * Appends a local command without an explicit record key.
     *
     * @return {@code true} if the record fit, {@code false} if the batch is full
     */
    boolean append(Intent intent, UnifiedRecordValue value);

    /**
     * Appends a local command with an explicit record key.
     *
     * @return {@code true} if the record fit, {@code false} if the batch is full
     */
    boolean append(long key, Intent intent, UnifiedRecordValue value);

    /** Sends a command to another partition. Best-effort, fire-and-forget. */
    void sendInterPartition(
        int receiverPartitionId,
        ValueType valueType,
        Intent intent,
        Long recordKey,
        UnifiedRecordValue value,
        AuthInfo authInfo);

    /** Convenience overload of {@link #sendInterPartition} without {@link AuthInfo}. */
    default void sendInterPartition(
        final int receiverPartitionId,
        final ValueType valueType,
        final Intent intent,
        final Long recordKey,
        final UnifiedRecordValue value) {
      sendInterPartition(receiverPartitionId, valueType, intent, recordKey, value, null);
    }

    // -------- Terminals --------

    /**
     * Done with this run; no specific next due-date in mind. The runtime reschedules at
     * {@code now + fallbackInterval} when configured; pure on-demand schedules sleep until
     * externally re-triggered. Clears any stored resume cursor on the runtime.
     */
    Result idle();

    /**
     * Done with what is currently due. The next entry is not due until {@code timestampMs}; the
     * runtime sleeps until then. Clears any stored resume cursor on the runtime.
     */
    Result awaitDueAt(long timestampMs);

    /**
     * The task has more work right now but is releasing the actor thread so other scheduled
     * tasks (and stream processing) get a turn before continuing. The runtime reschedules at
     * {@code now + minResolution} and saves {@code cursor}; the next run sees it via
     * {@link TaskContext#resumeCursor()}.
     */
    Result yieldNow(C cursor);

    /**
     * Shorthand for {@link #yieldNow(Object) yieldNow(null)}. Intended for tasks declared as
     * {@code ScheduledTask<Void>}; on a {@code <C>} task this stores {@code null} as the cursor,
     * which is usually a bug.
     */
    default Result yieldNow() {
      return yieldNow(null);
    }
  }

  /** What the task tells the runtime about when it should fire next. */
  public sealed interface Decision {
    /** Done; runtime falls back to its configured interval (or stays idle for on-demand). */
    Idle IDLE = new Idle();

    /** No more due entries until this absolute timestamp (millis since epoch). */
    record AwaitDueAt(long timestampMs) implements Decision {}

    /**
     * The task yielded mid-iteration; the runtime reschedules immediately. {@code cursor} is the
     * resume state to hand back via {@link TaskContext#resumeCursor()}, or {@code null}.
     */
    record YieldNow(Object cursor) implements Decision {}

    record Idle() implements Decision {}
  }

  /** A local command appended during a run. */
  public record AppendedCommand(long key, Intent intent, UnifiedRecordValue value) {}

  /** An inter-partition send issued during a run. */
  public record InterPartitionSend(
      int receiverPartitionId,
      ValueType valueType,
      Intent intent,
      Long recordKey,
      UnifiedRecordValue value,
      AuthInfo authInfo) {}
}
```

- [ ] **Step 2: Compile-check the new file in isolation**

Run: `./mvnw -pl zeebe/engine compile -Dquickly -T1C`
Expected: `BUILD SUCCESS`. The other API files (`Outcome`, `Sink`, `ScheduledTask`, `TaskContext`) and the runtime are still on the old shape, so nothing else changes.

- [ ] **Step 3: Commit**

```bash
git add zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/scheduled/api/Result.java
git commit -m "$(cat <<'EOF'
refactor: add Result + Result.Builder API

Introduces the immutable Result carrier (appended commands, inter-
partition sends, scheduling Decision) and the Builder<C> a ScheduledTask
will use to record side effects and pick its next scheduling decision.

Standalone in this commit; the next commits replace Outcome/Sink and
wire Builder through TaskContext and ManagedScheduledTask.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Replace `ScheduledTask` and `TaskContext`, delete `Outcome` and `Sink`

**Files:**
- Modify: `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/scheduled/api/ScheduledTask.java`
- Modify: `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/scheduled/api/TaskContext.java`
- Delete: `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/scheduled/api/Outcome.java`
- Delete: `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/scheduled/api/Sink.java`

After this commit the rest of the module won't compile (intentional — the runtime, the five tasks, and their tests still reference `Outcome`/`Sink`). The next commits fix that.

- [ ] **Step 1: Rewrite `ScheduledTask.java`**

Replace the entire file with:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.scheduled.api;

/**
 * Contract every periodic background task in the engine implements.
 *
 * <p>An implementation only describes the actual work: read state and emit follow-up commands or
 * inter-partition sends through {@link Result.Builder} obtained from {@link TaskContext#result()},
 * then return the {@link Result} produced by one of the builder's terminals
 * ({@link Result.Builder#idle}, {@link Result.Builder#awaitDueAt}, {@link Result.Builder#yieldNow}).
 * Lifecycle, scheduling cadence, yielding, error handling, logging and metrics are provided once
 * by the {@code ManagedScheduledTask} runtime and shared across all implementations.
 *
 * @param <C> the resume-cursor type. Tasks without continuation declare {@code <Void>}.
 */
public interface ScheduledTask<C> {

  /**
   * Stable, kebab-case identifier used as a label for metrics, log MDC, and tracing. Must be unique
   * per partition.
   */
  String name();

  /**
   * Performs one execution. The runtime invokes this in the stream processor's actor thread (or in
   * a configured async actor). Implementations must not retain references to {@link TaskContext}
   * or to the {@link Result.Builder} beyond the call.
   */
  Result run(TaskContext<C> context);
}
```

- [ ] **Step 2: Rewrite `TaskContext.java`**

Replace the entire file with:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.scheduled.api;

import java.time.InstantSource;

/**
 * Per-execution context handed to a {@link ScheduledTask#run(TaskContext)}.
 *
 * @param <C> the task's resume-cursor type
 */
public interface TaskContext<C> {

  /** Clock to be used for any "now" comparison. Honors test time-travel. */
  InstantSource clock();

  /** Partition id this task is running on. Useful for partition-scoped behavior. */
  int partitionId();

  /**
   * Returns {@code true} once the cooperative time budget for this run has been spent. Tasks that
   * iterate over many entries should poll this between items and return
   * {@link Result.Builder#yieldNow} to give the actor thread back to other work. The runtime will
   * reschedule immediately.
   *
   * <p>If the schedule does not configure a yield budget, this always returns {@code false}.
   */
  boolean shouldYield();

  /**
   * The cursor saved on the previous run via {@link Result.Builder#yieldNow(Object)}, or
   * {@code null} when no cursor is stored (first run, or the previous run terminated with
   * {@code idle()} / {@code awaitDueAt(...)}). The engine convention is to use {@code null} for
   * "missing"; {@link java.util.Optional} is reserved for lambda-shaped APIs.
   */
  C resumeCursor();

  /**
   * Fresh builder for this run. Tasks accumulate commands and sends on it, then return the
   * {@link Result} produced by one of its terminal methods. The runtime reads the builder's
   * accumulated state when the task returns.
   */
  Result.Builder<C> result();
}
```

- [ ] **Step 3: Delete `Outcome.java`**

Run: `git rm zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/scheduled/api/Outcome.java`
Expected: file removed; staged for deletion.

- [ ] **Step 4: Delete `Sink.java`**

Run: `git rm zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/scheduled/api/Sink.java`
Expected: file removed; staged for deletion.

- [ ] **Step 5: Verify the API package compiles**

Run: `./mvnw -pl zeebe/engine compile -Dquickly -T1C`
Expected: `BUILD FAILURE`. The api package itself compiles, but `ManagedScheduledTask` and the five tasks reference `Outcome` and `Sink`. This is expected — the next tasks fix it.

- [ ] **Step 6: Stage modified files**

Run: `git add zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/scheduled/api/ScheduledTask.java zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/scheduled/api/TaskContext.java`
Expected: both files staged for modification (deletions from steps 3-4 are already staged).

**Note:** Do not commit yet. The next task migrates the runtime; we'll commit Tasks 2 + 3 together so no commit leaves the module in a broken-compile state on `git bisect`.

---

## Task 3: Migrate `ManagedScheduledTask` to the `Result` API

**Files:**
- Modify: `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/scheduled/runtime/ManagedScheduledTask.java`

The runtime becomes generic on `C`, gains a `resumeCursor` field, and replaces the inner `RunContext` (which today implements both `TaskContext` and `Sink`) with two collaborators:

1. `ResultBuilderImpl<C>` — implements `Result.Builder<C>`, accumulates commands and sends, records metrics, and produces the final `Result` via terminals.
2. `RunContext<C>` — implements `TaskContext<C>`, exposes `clock` / `partitionId` / `shouldYield` / `resumeCursor` / `result()`.

After `task.run(ctx)` the runtime inspects the returned `Result.decision()` to derive (a) the new value of `resumeCursor` (`YieldNow.cursor` cast to `C`, otherwise `null`) and (b) the next scheduling action.

- [ ] **Step 1: Rewrite `ManagedScheduledTask.java`**

Replace the entire file with:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.scheduled.runtime;

import io.camunda.zeebe.engine.processing.scheduled.api.Result;
import io.camunda.zeebe.engine.processing.scheduled.api.Result.AppendedCommand;
import io.camunda.zeebe.engine.processing.scheduled.api.Result.Decision;
import io.camunda.zeebe.engine.processing.scheduled.api.Result.InterPartitionSend;
import io.camunda.zeebe.engine.processing.scheduled.api.Schedule;
import io.camunda.zeebe.engine.processing.scheduled.api.ScheduledTask;
import io.camunda.zeebe.engine.processing.scheduled.api.TaskContext;
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.stream.api.InterPartitionCommandSender;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.camunda.zeebe.stream.api.scheduling.SimpleProcessingScheduleService;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import io.camunda.zeebe.util.AtomicUtil;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adapts a {@link ScheduledTask} (the "what") to the stream processor's lifecycle and scheduling
 * service (the "how"). One instance manages a single task and is registered as a lifecycle listener
 * via {@code typedRecordProcessors.withListener(managed)}.
 *
 * <p>Provides the cross-cutting concerns once for every task that uses the new API: full
 * lifecycle, scheduling cadence translation, cooperative yielding, error handling, slow-run
 * logging, per-task metrics, external wake-up via {@link #requestRun(long)}, and resume-cursor
 * storage between yields.
 */
public final class ManagedScheduledTask<C> implements StreamProcessorLifecycleAware, Task {

  private static final Logger LOG = LoggerFactory.getLogger(ManagedScheduledTask.class);
  private static final long SLOW_RUN_THRESHOLD_MS = 100;

  private final ScheduledTask<C> task;
  private final Schedule schedule;
  private final InterPartitionCommandSender interPartitionCommandSender;
  private final ScheduledTaskMetrics metrics;

  private InstantSource clock;
  private ProcessingScheduleService scheduleService;
  private int partitionId;

  private volatile boolean enabled;
  private final AtomicReference<NextRun> nextRun = new AtomicReference<>(NextRun.NONE);

  /** Cursor saved by the previous run's {@code yieldNow(c)}; {@code null} otherwise. */
  private C resumeCursor;

  public ManagedScheduledTask(
      final ScheduledTask<C> task,
      final Schedule schedule,
      final InterPartitionCommandSender interPartitionCommandSender,
      final MeterRegistry meterRegistry) {
    this.task = task;
    this.schedule = schedule;
    this.interPartitionCommandSender = interPartitionCommandSender;
    metrics = new ScheduledTaskMetrics(meterRegistry, task.name());
  }

  // ---------------------------------------------------------------------------
  // External API
  // ---------------------------------------------------------------------------

  public void requestRun(final long timestampMs) {
    if (!enabled) {
      return;
    }
    rescheduleIfEarlier(timestampMs);
  }

  // ---------------------------------------------------------------------------
  // Lifecycle
  // ---------------------------------------------------------------------------

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    clock = context.getClock();
    scheduleService = context.getScheduleService();
    partitionId = context.getPartitionId();
    enabled = true;
    LOG.debug("Scheduled task '{}' recovered on partition {}", task.name(), partitionId);
    scheduleInitial();
  }

  @Override
  public void onResumed() {
    enabled = true;
    LOG.debug("Scheduled task '{}' resumed", task.name());
    scheduleInitial();
  }

  @Override
  public void onPaused() {
    LOG.debug("Scheduled task '{}' paused", task.name());
    disableAndCancel();
  }

  @Override
  public void onClose() {
    LOG.debug("Scheduled task '{}' closed", task.name());
    disableAndCancel();
  }

  @Override
  public void onFailed() {
    LOG.debug("Scheduled task '{}' failed", task.name());
    disableAndCancel();
  }

  // ---------------------------------------------------------------------------
  // Task execution
  // ---------------------------------------------------------------------------

  @Override
  public TaskResult execute(final TaskResultBuilder taskResultBuilder) {
    nextRun.set(NextRun.NONE);

    final long startMs = clock.millis();
    final long yieldAfterMs =
        schedule.yieldBudget() == null
            ? Long.MAX_VALUE
            : startMs + schedule.yieldBudget().toMillis();

    final ResultBuilderImpl<C> builder = new ResultBuilderImpl<>(taskResultBuilder);
    final RunContext<C> ctx = new RunContext<>(yieldAfterMs, resumeCursor, builder);

    Decision decision;
    try {
      final Result result = task.run(ctx);
      decision = result.decision();
    } catch (final RuntimeException e) {
      metrics.recordError();
      LOG.warn(
          "Scheduled task '{}' threw {}; will reschedule per fallback interval",
          task.name(),
          e.toString(),
          e);
      decision = Decision.IDLE;
    }

    final long elapsed = clock.millis() - startMs;
    metrics.recordRun(Duration.ofMillis(elapsed));
    if (elapsed >= SLOW_RUN_THRESHOLD_MS) {
      LOG.info("Scheduled task '{}' run took {}ms", task.name(), elapsed);
    }

    updateResumeCursor(decision);
    if (decision instanceof Decision.YieldNow) {
      metrics.recordYield();
    }

    scheduleNext(decision);

    return taskResultBuilder.build();
  }

  // ---------------------------------------------------------------------------
  // Scheduling helpers
  // ---------------------------------------------------------------------------

  private void scheduleInitial() {
    rescheduleIfEarlier(0L);
  }

  @SuppressWarnings("unchecked")
  private void updateResumeCursor(final Decision decision) {
    resumeCursor =
        decision instanceof Decision.YieldNow yield ? (C) yield.cursor() : null;
  }

  private void scheduleNext(final Decision decision) {
    if (!enabled) {
      return;
    }
    switch (decision) {
      case Decision.YieldNow ignored -> rescheduleIfEarlier(0L);
      case Decision.AwaitDueAt due -> rescheduleIfEarlier(due.timestampMs());
      case Decision.Idle ignored -> {
        if (schedule.fallbackInterval() != null) {
          rescheduleIfEarlier(clock.millis() + schedule.fallbackInterval().toMillis());
        }
      }
    }
  }

  private void rescheduleIfEarlier(final long requestedAtMs) {
    final long minResolutionMs = schedule.minResolution().toMillis();
    final long scheduleAt = Math.max(requestedAtMs, clock.millis() + minResolutionMs);

    final NextRun replaced =
        AtomicUtil.replace(
            nextRun,
            current -> {
              if (current.isScheduledAtOrBefore(scheduleAt)) {
                return Optional.empty();
              }
              return Optional.of(new NextRun(scheduleAt, runAt(scheduleAt)));
            },
            NextRun::cancel);

    if (replaced != null) {
      replaced.cancel();
    }
  }

  private SimpleProcessingScheduleService.ScheduledTask runAt(final long timestampMs) {
    if (schedule.async()) {
      return scheduleService.runAtAsync(timestampMs, this);
    }
    return scheduleService.runAt(timestampMs, this);
  }

  private void disableAndCancel() {
    enabled = false;
    resumeCursor = null;
    final NextRun cleared = nextRun.getAndSet(NextRun.NONE);
    cleared.cancel();
  }

  // ---------------------------------------------------------------------------
  // Inner types
  // ---------------------------------------------------------------------------

  private record NextRun(long scheduledAt, SimpleProcessingScheduleService.ScheduledTask handle) {

    static final NextRun NONE = new NextRun(Long.MAX_VALUE, null);

    boolean isScheduledAtOrBefore(final long timestampMs) {
      return handle != null && scheduledAt <= timestampMs;
    }

    void cancel() {
      if (handle != null) {
        handle.cancel();
      }
    }
  }

  /** Per-run TaskContext. The Builder is created in {@link #execute} and shared with the task. */
  private final class RunContext<X> implements TaskContext<X> {

    private final long yieldAfterMs;
    private final X resumeCursor;
    private final Result.Builder<X> builder;

    RunContext(
        final long yieldAfterMs, final X resumeCursor, final Result.Builder<X> builder) {
      this.yieldAfterMs = yieldAfterMs;
      this.resumeCursor = resumeCursor;
      this.builder = builder;
    }

    @Override
    public InstantSource clock() {
      return clock;
    }

    @Override
    public int partitionId() {
      return partitionId;
    }

    @Override
    public boolean shouldYield() {
      return clock.millis() >= yieldAfterMs;
    }

    @Override
    public X resumeCursor() {
      return resumeCursor;
    }

    @Override
    public Result.Builder<X> result() {
      return builder;
    }
  }

  /**
   * Per-run Result.Builder that delegates appends to the engine's {@link TaskResultBuilder} and
   * inter-partition sends to {@link InterPartitionCommandSender}. The terminal methods produce
   * the immutable {@link Result} with the accumulated state.
   */
  private final class ResultBuilderImpl<X> implements Result.Builder<X> {

    private final TaskResultBuilder underlying;
    private final List<AppendedCommand> appendedCommands = new ArrayList<>();
    private final List<InterPartitionSend> interPartitionSends = new ArrayList<>();

    ResultBuilderImpl(final TaskResultBuilder underlying) {
      this.underlying = underlying;
    }

    @Override
    public boolean append(final Intent intent, final UnifiedRecordValue value) {
      final boolean fit = underlying.appendCommandRecord(intent, value);
      if (fit) {
        appendedCommands.add(new AppendedCommand(-1L, intent, value));
        metrics.recordAppend();
      }
      return fit;
    }

    @Override
    public boolean append(final long key, final Intent intent, final UnifiedRecordValue value) {
      final boolean fit = underlying.appendCommandRecord(key, intent, value);
      if (fit) {
        appendedCommands.add(new AppendedCommand(key, intent, value));
        metrics.recordAppend();
      }
      return fit;
    }

    @Override
    public void sendInterPartition(
        final int receiverPartitionId,
        final ValueType valueType,
        final Intent intent,
        final Long recordKey,
        final UnifiedRecordValue value,
        final AuthInfo authInfo) {
      if (interPartitionCommandSender == null) {
        throw new IllegalStateException(
            "ScheduledTask '"
                + task.name()
                + "' attempted an inter-partition send but no sender was configured");
      }
      interPartitionCommandSender.sendCommand(
          receiverPartitionId, valueType, intent, recordKey, value, authInfo);
      interPartitionSends.add(
          new InterPartitionSend(
              receiverPartitionId, valueType, intent, recordKey, value, authInfo));
      metrics.recordInterPartitionSend();
    }

    @Override
    public Result idle() {
      return new Result(appendedCommands, interPartitionSends, Decision.IDLE);
    }

    @Override
    public Result awaitDueAt(final long timestampMs) {
      return new Result(
          appendedCommands, interPartitionSends, new Decision.AwaitDueAt(timestampMs));
    }

    @Override
    public Result yieldNow(final X cursor) {
      return new Result(
          appendedCommands, interPartitionSends, new Decision.YieldNow(cursor));
    }
  }
}
```

- [ ] **Step 2: Verify the runtime compiles**

Run: `./mvnw -pl zeebe/engine compile -Dquickly -T1C`
Expected: still `BUILD FAILURE`, but the failure must now be in the five task implementations and their tests (they still reference `Outcome` / `Sink` / non-generic `ScheduledTask`). The runtime itself and the api package are clean.

If runtime errors are reported, fix them before proceeding. The task migrations cannot start while the runtime is broken.

- [ ] **Step 3: Stage and commit (Tasks 2 + 3 together)**

```bash
git add zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/scheduled/runtime/ManagedScheduledTask.java
git commit -m "$(cat <<'EOF'
refactor: replace Outcome+Sink with Result API

Drops Outcome and Sink in favour of a single Result returned from
ScheduledTask.run, built via Result.Builder<C> exposed on TaskContext.
The runtime gains a resumeCursor field whose lifecycle is tied to the
builder's terminal: yieldNow(c) saves it; idle/awaitDueAt and lifecycle
transitions clear it.

Module does not yet build: the five ScheduledTask impls and their tests
still reference the deleted types. Subsequent commits migrate them.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

The branch will be in a broken-compile state for one commit (this one) until Task 4 lands the test fake; the five migration commits then bring it back to green. This is unavoidable because the API surface and the runtime must change together. Bisect can step over the broken commits.

---

## Task 4: Migrate `FakeTaskContext`

**Files:**
- Modify: `zeebe/engine/src/test/java/io/camunda/zeebe/engine/processing/scheduled/runtime/FakeTaskContext.java`

`FakeTaskContext` is the test-only `TaskContext` used by every scheduler unit test. Becomes generic on `C`, drops the `Sink` mixin, gains `withResumeCursor(C)`, and exposes the captured `Result` via `lastResult()`. Per spec, tests inspect `result.appendedCommands()`, `result.interPartitionSends()`, and `result.decision()` instead of `ctx.appended()` / `ctx.sends()`.

The fake's `Result.Builder<C>` is a tiny in-memory implementation: appends honour a configurable batch capacity, sends are recorded, and the terminals build a `Result` and store it as `lastResult` for test inspection.

- [ ] **Step 1: Rewrite `FakeTaskContext.java`**

Replace the entire file with:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.scheduled.runtime;

import io.camunda.zeebe.engine.processing.scheduled.api.Result;
import io.camunda.zeebe.engine.processing.scheduled.api.Result.AppendedCommand;
import io.camunda.zeebe.engine.processing.scheduled.api.Result.Decision;
import io.camunda.zeebe.engine.processing.scheduled.api.Result.InterPartitionSend;
import io.camunda.zeebe.engine.processing.scheduled.api.TaskContext;
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;

/** Test-only TaskContext that records appended commands, inter-partition sends, and the
 * scheduling decision. Inspect the captured {@link Result} via {@link #lastResult()} after the
 * task returns. */
public final class FakeTaskContext<C> implements TaskContext<C> {

  private InstantSource clock = InstantSource.fixed(Instant.ofEpochMilli(1_000_000L));
  private boolean shouldYield = false;
  private int partitionId = 1;
  private int batchCapacity = Integer.MAX_VALUE;
  private C resumeCursor = null;

  private final FakeBuilder<C> builder = new FakeBuilder<>();

  /** Convenience for the common {@code <Void>} case. */
  public static FakeTaskContext<Void> create() {
    return new FakeTaskContext<>();
  }

  /** Use when the task declares a non-{@code Void} cursor type. */
  public static <C> FakeTaskContext<C> createFor(final Class<C> cursorType) {
    return new FakeTaskContext<>();
  }

  public FakeTaskContext<C> withClockMillis(final long millis) {
    clock = InstantSource.fixed(Instant.ofEpochMilli(millis));
    return this;
  }

  public FakeTaskContext<C> withShouldYield(final boolean shouldYield) {
    this.shouldYield = shouldYield;
    return this;
  }

  public FakeTaskContext<C> withBatchCapacity(final int capacity) {
    batchCapacity = capacity;
    return this;
  }

  public FakeTaskContext<C> withResumeCursor(final C cursor) {
    resumeCursor = cursor;
    return this;
  }

  /**
   * The {@link Result} produced by the most recent terminal call on this context's builder, or
   * {@code null} if the task did not call a terminal. Tests use this to assert on appended
   * commands, inter-partition sends, and the scheduling {@link Decision}.
   */
  public Result lastResult() {
    return builder.lastResult;
  }

  @Override
  public InstantSource clock() {
    return clock;
  }

  @Override
  public int partitionId() {
    return partitionId;
  }

  @Override
  public boolean shouldYield() {
    return shouldYield;
  }

  @Override
  public C resumeCursor() {
    return resumeCursor;
  }

  @Override
  public Result.Builder<C> result() {
    return builder;
  }

  /** Tiny in-memory Builder. Records appended commands subject to {@code batchCapacity}. */
  private final class FakeBuilder<X> implements Result.Builder<X> {

    private final List<AppendedCommand> appendedCommands = new ArrayList<>();
    private final List<InterPartitionSend> interPartitionSends = new ArrayList<>();
    private Result lastResult;

    @Override
    public boolean append(final Intent intent, final UnifiedRecordValue value) {
      return append(-1L, intent, value);
    }

    @Override
    public boolean append(final long key, final Intent intent, final UnifiedRecordValue value) {
      if (appendedCommands.size() >= batchCapacity) {
        return false;
      }
      appendedCommands.add(new AppendedCommand(key, intent, value));
      return true;
    }

    @Override
    public void sendInterPartition(
        final int receiverPartitionId,
        final ValueType valueType,
        final Intent intent,
        final Long recordKey,
        final UnifiedRecordValue value,
        final AuthInfo authInfo) {
      interPartitionSends.add(
          new InterPartitionSend(
              receiverPartitionId, valueType, intent, recordKey, value, authInfo));
    }

    @Override
    public Result idle() {
      return capture(Decision.IDLE);
    }

    @Override
    public Result awaitDueAt(final long timestampMs) {
      return capture(new Decision.AwaitDueAt(timestampMs));
    }

    @Override
    public Result yieldNow(final X cursor) {
      return capture(new Decision.YieldNow(cursor));
    }

    private Result capture(final Decision decision) {
      lastResult = new Result(appendedCommands, interPartitionSends, decision);
      return lastResult;
    }
  }
}
```

- [ ] **Step 2: Confirm the fake compiles in isolation (test-compile not yet possible)**

Run: `./mvnw -pl zeebe/engine compile -Dquickly -T1C`
Expected: same as before this step — failure in the five task impls. The fake itself compiles successfully.

- [ ] **Step 3: Stage**

Run: `git add zeebe/engine/src/test/java/io/camunda/zeebe/engine/processing/scheduled/runtime/FakeTaskContext.java`

**Note:** Do not commit yet. The fake is unused until at least one task migrates; commit it together with the first task migration (Task 5) so each commit leaves the test sources at least nominally compilable for the migrated tasks.

---

## Task 5: Migrate `MessageTimeToLiveCheckScheduler` (`<Void>`, simplest)

**Files:**
- Modify: `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/message/MessageTimeToLiveCheckScheduler.java`
- Modify: `zeebe/engine/src/test/java/io/camunda/zeebe/engine/processing/message/MessageTimeToLiveCheckSchedulerTest.java`

The simplest migration. `MessageTimeToLiveCheckScheduler` returns `Idle` always; the only side effect is at most one `EXPIRE` append. This task lands together with the `FakeTaskContext` from Task 4.

- [ ] **Step 1: Rewrite the test first (refactor TDD: drive the new API by exercising it)**

Replace the entire file `zeebe/engine/src/test/java/io/camunda/zeebe/engine/processing/message/MessageTimeToLiveCheckSchedulerTest.java` with:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.processing.scheduled.api.Result;
import io.camunda.zeebe.engine.processing.scheduled.api.Result.Decision;
import io.camunda.zeebe.engine.processing.scheduled.runtime.FakeTaskContext;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.protocol.record.intent.MessageBatchIntent;
import org.junit.jupiter.api.Test;

final class MessageTimeToLiveCheckSchedulerTest {

  @Test
  void shouldNotEmitCommandWhenNoExpiredMessages() {
    // given
    final MessageState state = mock(MessageState.class);
    when(state.visitMessagesWithDeadlineBeforeTimestamp(anyLong(), any(), any())).thenReturn(false);
    final var scheduler = new MessageTimeToLiveCheckScheduler(state);
    final var ctx = FakeTaskContext.create().withClockMillis(1_000L);

    // when
    final Result result = scheduler.run(ctx);

    // then
    assertThat(result.decision()).isEqualTo(Decision.IDLE);
    assertThat(result.appendedCommands()).isEmpty();
  }

  @Test
  void shouldEmitExpireCommandWhenAtLeastOneMessageExpired() {
    // given
    final MessageState state = mock(MessageState.class);
    when(state.visitMessagesWithDeadlineBeforeTimestamp(anyLong(), any(), any())).thenReturn(true);
    final var scheduler = new MessageTimeToLiveCheckScheduler(state);
    final var ctx = FakeTaskContext.create().withClockMillis(1_000L);

    // when
    final Result result = scheduler.run(ctx);

    // then
    assertThat(result.decision()).isEqualTo(Decision.IDLE);
    assertThat(result.appendedCommands()).hasSize(1);
    assertThat(result.appendedCommands().get(0).intent()).isEqualTo(MessageBatchIntent.EXPIRE);
  }
}
```

- [ ] **Step 2: Rewrite the implementation**

Replace `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/message/MessageTimeToLiveCheckScheduler.java` with:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.processing.scheduled.api.Result;
import io.camunda.zeebe.engine.processing.scheduled.api.ScheduledTask;
import io.camunda.zeebe.engine.processing.scheduled.api.TaskContext;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageBatchRecord;
import io.camunda.zeebe.protocol.record.intent.MessageBatchIntent;

/**
 * Detects expired message deadlines and writes a single {@link MessageBatchIntent#EXPIRE} command
 * when any are found. The actual expiry work is done by {@code MessageBatchExpireProcessor}.
 *
 * <p>Lifecycle, scheduling cadence (default fallback interval 1 minute), error handling, logging
 * and metrics are provided by {@code ManagedScheduledTask}.
 */
public final class MessageTimeToLiveCheckScheduler implements ScheduledTask<Void> {

  private final MessageState messageState;

  public MessageTimeToLiveCheckScheduler(final MessageState messageState) {
    this.messageState = messageState;
  }

  @Override
  public String name() {
    return "message-ttl-check";
  }

  @Override
  public Result run(final TaskContext<Void> ctx) {
    final Result.Builder<Void> result = ctx.result();
    final boolean hasExpired =
        messageState.visitMessagesWithDeadlineBeforeTimestamp(
            ctx.clock().millis(), null, (deadline, key) -> false);
    if (hasExpired) {
      result.append(MessageBatchIntent.EXPIRE, new MessageBatchRecord());
    }
    return result.idle();
  }
}
```

- [ ] **Step 3: Run the test class**

Run:

```bash
./mvnw verify -pl zeebe/engine -Dtest=MessageTimeToLiveCheckSchedulerTest -DskipTests=false -DskipITs -Dquickly -T1C
```

Expected: 2 tests pass. The rest of the module still won't compile (other tasks pending), so test-compile of the broader test sources may emit unrelated errors — Surefire still runs the requested class. If the requested test is reported as not found, it means test-compile failed broadly; in that case it's safer to verify with a JUnit 5 explicit selector after Task 9, when all tests compile.

If the test is not reachable yet (broad test-compile fails), skip this verification and run it as part of the rolling green-up at Task 9.

- [ ] **Step 4: Stage and commit (with `FakeTaskContext` from Task 4)**

```bash
git add zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/message/MessageTimeToLiveCheckScheduler.java \
        zeebe/engine/src/test/java/io/camunda/zeebe/engine/processing/message/MessageTimeToLiveCheckSchedulerTest.java
git commit -m "$(cat <<'EOF'
refactor: migrate MessageTimeToLiveCheckScheduler to Result

Declares the task as ScheduledTask<Void> and produces its scheduling
decision via TaskContext.result(). Brings FakeTaskContext over to the
Result API so this and the remaining task migrations can run their
unit tests.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Migrate `JobBackoffCheckScheduler` (`<Void>`, awaitDueAt)

**Files:**
- Modify: `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/job/JobBackoffCheckScheduler.java`
- Modify: `zeebe/engine/src/test/java/io/camunda/zeebe/engine/processing/job/JobBackoffCheckSchedulerTest.java`

Similar to Task 5 but exercises `awaitDueAt`.

- [ ] **Step 1: Rewrite the test**

Replace `zeebe/engine/src/test/java/io/camunda/zeebe/engine/processing/job/JobBackoffCheckSchedulerTest.java` with:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.processing.scheduled.api.Result;
import io.camunda.zeebe.engine.processing.scheduled.api.Result.Decision;
import io.camunda.zeebe.engine.processing.scheduled.runtime.FakeTaskContext;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import java.util.function.BiPredicate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

final class JobBackoffCheckSchedulerTest {

  @Test
  void shouldReturnIdleWhenNoBackedOffJobsFound() {
    // given
    final JobState state = mock(JobState.class);
    when(state.findBackedOffJobs(anyLong(), any())).thenReturn(0L);
    final var scheduler = new JobBackoffCheckScheduler(state);

    // when
    final Result result = scheduler.run(FakeTaskContext.create().withClockMillis(123L));

    // then
    assertThat(result.decision()).isEqualTo(Decision.IDLE);
  }

  @Test
  void shouldReturnAwaitDueAtWhenNextBackoffKnown() {
    // given
    final JobState state = mock(JobState.class);
    when(state.findBackedOffJobs(anyLong(), any())).thenReturn(5_000L);
    final var scheduler = new JobBackoffCheckScheduler(state);

    // when
    final Result result = scheduler.run(FakeTaskContext.create().withClockMillis(1_000L));

    // then
    assertThat(result.decision()).isInstanceOf(Decision.AwaitDueAt.class);
    assertThat(((Decision.AwaitDueAt) result.decision()).timestampMs()).isEqualTo(5_000L);
  }

  @Test
  void shouldAppendRecurAfterBackoffCommandPerJob() {
    // given
    final JobState state = mock(JobState.class);
    final ArgumentCaptor<BiPredicate<Long, JobRecord>> visitor = visitorCaptor();
    when(state.findBackedOffJobs(anyLong(), visitor.capture())).thenReturn(0L);
    final var scheduler = new JobBackoffCheckScheduler(state);
    final var ctx = FakeTaskContext.create().withClockMillis(1_000L);

    // when
    scheduler.run(ctx);
    final var record = new JobRecord();
    visitor.getValue().test(42L, record);
    visitor.getValue().test(43L, record);

    // then
    assertThat(ctx.lastResult().appendedCommands()).hasSize(2);
    assertThat(ctx.lastResult().appendedCommands().get(0).key()).isEqualTo(42L);
    assertThat(ctx.lastResult().appendedCommands().get(0).intent())
        .isEqualTo(JobIntent.RECUR_AFTER_BACKOFF);
    assertThat(ctx.lastResult().appendedCommands().get(1).key()).isEqualTo(43L);
  }

  @SuppressWarnings("unchecked")
  private static ArgumentCaptor<BiPredicate<Long, JobRecord>> visitorCaptor() {
    return ArgumentCaptor.forClass(BiPredicate.class);
  }
}
```

- [ ] **Step 2: Rewrite the implementation**

Replace `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/job/JobBackoffCheckScheduler.java` with:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.processing.scheduled.api.Result;
import io.camunda.zeebe.engine.processing.scheduled.api.ScheduledTask;
import io.camunda.zeebe.engine.processing.scheduled.api.TaskContext;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.protocol.record.intent.JobIntent;

/**
 * Re-activates jobs whose backoff period has elapsed by writing a {@link
 * JobIntent#RECUR_AFTER_BACKOFF} command for each.
 *
 * <p>On-demand: returns {@link Result.Builder#awaitDueAt} for the next due-date so the runtime
 * sleeps until then. External callers (e.g. {@code JobFailProcessor}) call {@code
 * managed.requestRun(dueDate)} when a new job enters backoff to wake the task earlier if needed.
 */
public final class JobBackoffCheckScheduler implements ScheduledTask<Void> {

  /**
   * Minimum resolution in millis between consecutive runs of this scheduler. Used by the runtime's
   * {@code minResolution} and exposed for tests that need to time-travel past it.
   */
  public static final long BACKOFF_RESOLUTION = 100L;

  private final JobState jobState;

  public JobBackoffCheckScheduler(final JobState jobState) {
    this.jobState = jobState;
  }

  @Override
  public String name() {
    return "job-backoff-check";
  }

  @Override
  public Result run(final TaskContext<Void> ctx) {
    final Result.Builder<Void> result = ctx.result();
    final long nextDueDate =
        jobState.findBackedOffJobs(
            ctx.clock().millis(),
            (key, record) -> result.append(key, JobIntent.RECUR_AFTER_BACKOFF, record));

    return nextDueDate > 0 ? result.awaitDueAt(nextDueDate) : result.idle();
  }
}
```

- [ ] **Step 3: Stage and commit**

```bash
git add zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/job/JobBackoffCheckScheduler.java \
        zeebe/engine/src/test/java/io/camunda/zeebe/engine/processing/job/JobBackoffCheckSchedulerTest.java
git commit -m "$(cat <<'EOF'
refactor: migrate JobBackoffCheckScheduler to Result

Declares the task as ScheduledTask<Void>. The awaitDueAt path now flows
through Result.Builder; the previous Outcome.AwaitDueAt allocation is
replaced by a builder terminal.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: Migrate `DueDateTimerCheckScheduler` (`<Void>`, yieldNow no-arg)

**Files:**
- Modify: `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/timer/DueDateTimerCheckScheduler.java`
- Modify: `zeebe/engine/src/test/java/io/camunda/zeebe/engine/processing/timer/DueDateTimerCheckSchedulerTest.java`

Mid-iteration yield without a cursor; uses `result.yieldNow()` no-arg.

- [ ] **Step 1: Rewrite the test**

Replace `zeebe/engine/src/test/java/io/camunda/zeebe/engine/processing/timer/DueDateTimerCheckSchedulerTest.java` with:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.timer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.processing.scheduled.api.Result;
import io.camunda.zeebe.engine.processing.scheduled.api.Result.Decision;
import io.camunda.zeebe.engine.processing.scheduled.runtime.FakeTaskContext;
import io.camunda.zeebe.engine.state.immutable.TimerInstanceState;
import io.camunda.zeebe.engine.state.immutable.TimerInstanceState.TimerVisitor;
import io.camunda.zeebe.engine.state.instance.TimerInstance;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

final class DueDateTimerCheckSchedulerTest {

  @Test
  void shouldReturnAwaitDueAtForNextTimer() {
    // given
    final var state = mock(TimerInstanceState.class);
    when(state.processTimersWithDueDateBefore(anyLong(), any())).thenReturn(2_500L);
    final var scheduler = new DueDateTimerCheckScheduler(state);

    // when
    final Result result = scheduler.run(FakeTaskContext.create().withClockMillis(1_000L));

    // then
    assertThat(result.decision()).isInstanceOf(Decision.AwaitDueAt.class);
    assertThat(((Decision.AwaitDueAt) result.decision()).timestampMs()).isEqualTo(2_500L);
  }

  @Test
  void shouldReturnIdleWhenNoTimersAndNoYield() {
    // given
    final var state = mock(TimerInstanceState.class);
    when(state.processTimersWithDueDateBefore(anyLong(), any())).thenReturn(-1L);
    final var scheduler = new DueDateTimerCheckScheduler(state);

    // when
    final Result result = scheduler.run(FakeTaskContext.create().withClockMillis(1_000L));

    // then
    assertThat(result.decision()).isEqualTo(Decision.IDLE);
  }

  @Test
  void shouldYieldWhenContextSignalsYieldAndNoNextDueDate() {
    // given
    final var state = mock(TimerInstanceState.class);
    when(state.processTimersWithDueDateBefore(anyLong(), any())).thenReturn(-1L);
    final var scheduler = new DueDateTimerCheckScheduler(state);
    final var ctx = FakeTaskContext.create().withClockMillis(1_000L).withShouldYield(true);

    // when
    final Result result = scheduler.run(ctx);

    // then — yield with null cursor since this task is <Void>
    assertThat(result.decision()).isInstanceOf(Decision.YieldNow.class);
    assertThat(((Decision.YieldNow) result.decision()).cursor()).isNull();
  }

  @Test
  void shouldEmitTimerTriggerCommandsForVisitedTimers() {
    // given
    final var state = mock(TimerInstanceState.class);
    final ArgumentCaptor<TimerVisitor> visitor = ArgumentCaptor.forClass(TimerVisitor.class);
    when(state.processTimersWithDueDateBefore(anyLong(), visitor.capture())).thenReturn(-1L);
    final var scheduler = new DueDateTimerCheckScheduler(state);
    final var ctx = FakeTaskContext.create().withClockMillis(1_000L);

    // when
    scheduler.run(ctx);
    final TimerInstance timer = new TimerInstance();
    timer.setKey(7L);
    timer.setElementInstanceKey(11L);
    timer.setProcessInstanceKey(13L);
    timer.setDueDate(900L);
    timer.setRepetitions(1);
    timer.setProcessDefinitionKey(17L);
    final boolean accepted = visitor.getValue().visit(timer);

    // then
    assertThat(accepted).isTrue();
    assertThat(ctx.lastResult().appendedCommands()).hasSize(1);
    assertThat(ctx.lastResult().appendedCommands().get(0).key()).isEqualTo(7L);
    assertThat(ctx.lastResult().appendedCommands().get(0).intent()).isEqualTo(TimerIntent.TRIGGER);
  }

  @Test
  void shouldStopVisitingWhenYieldRequested() {
    // given
    final var state = mock(TimerInstanceState.class);
    final ArgumentCaptor<TimerVisitor> visitor = ArgumentCaptor.forClass(TimerVisitor.class);
    when(state.processTimersWithDueDateBefore(anyLong(), visitor.capture())).thenReturn(-1L);
    final var scheduler = new DueDateTimerCheckScheduler(state);
    final var ctx = FakeTaskContext.create().withClockMillis(1_000L).withShouldYield(true);

    // when
    scheduler.run(ctx);
    final boolean accepted = visitor.getValue().visit(new TimerInstance());

    // then
    assertThat(accepted).isFalse();
    assertThat(ctx.lastResult().appendedCommands()).isEmpty();
  }
}
```

- [ ] **Step 2: Rewrite the implementation**

Replace `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/timer/DueDateTimerCheckScheduler.java` with:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.timer;

import io.camunda.zeebe.engine.processing.scheduled.api.Result;
import io.camunda.zeebe.engine.processing.scheduled.api.ScheduledTask;
import io.camunda.zeebe.engine.processing.scheduled.api.TaskContext;
import io.camunda.zeebe.engine.state.immutable.TimerInstanceState;
import io.camunda.zeebe.protocol.impl.record.value.timer.TimerRecord;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;

/**
 * Triggers BPMN timers (start events, boundary events, intermediate catch events) once their due
 * date is reached.
 *
 * <p>On-demand: returns {@link Result.Builder#awaitDueAt} for the next due-date so the runtime
 * sleeps until then. External callers ({@code TimerStartEventSubscriptionManager} and friends) call
 * {@code managed.requestRun(dueDate)} via {@code scheduleTimer} whenever a new timer is created
 * with an earlier due-date than the currently-scheduled run.
 *
 * <p>Cooperative yielding: when many timers are due at once, the task polls
 * {@link TaskContext#shouldYield()} between entries and returns {@link Result.Builder#yieldNow()}
 * so the actor thread can serve other work. The schedule's yield budget controls how long a
 * single run may consume.
 */
public final class DueDateTimerCheckScheduler implements ScheduledTask<Void> {

  private final TimerInstanceState timerInstanceState;

  // Reused across iterations within a single run to avoid garbage.
  private final TimerRecord timerRecord = new TimerRecord();

  public DueDateTimerCheckScheduler(final TimerInstanceState timerInstanceState) {
    this.timerInstanceState = timerInstanceState;
  }

  @Override
  public String name() {
    return "timer-due-date-check";
  }

  @Override
  public Result run(final TaskContext<Void> ctx) {
    final Result.Builder<Void> result = ctx.result();
    final long now = ctx.clock().millis();

    final long nextDueDate =
        timerInstanceState.processTimersWithDueDateBefore(
            now,
            timer -> {
              if (ctx.shouldYield()) {
                return false;
              }
              timerRecord.reset();
              timerRecord
                  .setElementInstanceKey(timer.getElementInstanceKey())
                  .setProcessInstanceKey(timer.getProcessInstanceKey())
                  .setDueDate(timer.getDueDate())
                  .setTargetElementId(timer.getHandlerNodeId())
                  .setRepetitions(timer.getRepetitions())
                  .setProcessDefinitionKey(timer.getProcessDefinitionKey())
                  .setTenantId(timer.getTenantId());
              return result.append(timer.getKey(), TimerIntent.TRIGGER, timerRecord);
            });

    if (nextDueDate > 0) {
      return result.awaitDueAt(nextDueDate);
    }
    return ctx.shouldYield() ? result.yieldNow() : result.idle();
  }
}
```

- [ ] **Step 3: Stage and commit**

```bash
git add zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/timer/DueDateTimerCheckScheduler.java \
        zeebe/engine/src/test/java/io/camunda/zeebe/engine/processing/timer/DueDateTimerCheckSchedulerTest.java
git commit -m "$(cat <<'EOF'
refactor: migrate DueDateTimerCheckScheduler to Result

Declares the task as ScheduledTask<Void>. The yield path now uses the
no-arg yieldNow() shorthand on Result.Builder<Void>; cooperative
yielding semantics carry over unchanged.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: Migrate `CommandRedistributionScheduler` (`<Void>`, sendInterPartition)

**Files:**
- Modify: `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/distribution/CommandRedistributionScheduler.java`
- Modify: `zeebe/engine/src/test/java/io/camunda/zeebe/engine/processing/distribution/CommandRedistributionSchedulerTest.java`

Per the spec, `retryCyclesPerDistribution` is accumulated state across runs, not a resume cursor — it stays as an instance field. The migration keeps the field; only the API call sites change. The test currently does not exercise sends through the context; it stays focused on the retry-cycle logic. We update the assertion shape (`Outcome.IDLE` → `Decision.IDLE`).

- [ ] **Step 1: Rewrite the test**

Replace `zeebe/engine/src/test/java/io/camunda/zeebe/engine/processing/distribution/CommandRedistributionSchedulerTest.java` with:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.distribution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.scheduled.api.Result;
import io.camunda.zeebe.engine.processing.scheduled.api.Result.Decision;
import io.camunda.zeebe.engine.processing.scheduled.runtime.FakeTaskContext;
import io.camunda.zeebe.engine.state.immutable.DistributionState.PendingDistributionVisitor;
import io.camunda.zeebe.engine.state.routing.RoutingInfo;
import io.camunda.zeebe.protocol.impl.record.value.distribution.CommandDistributionRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import java.time.Duration;
import org.junit.jupiter.api.Test;

final class CommandRedistributionSchedulerTest {

  private static final int RECEIVER_PARTITION = 2;
  private static final long DISTRIBUTION_KEY = 123L;

  @Test
  void shouldNotRetryOnFirstVisit() {
    // given — first cycle is 0; bitCount(0) != 1 -> no retry
    final var distributionBehavior = mock(CommandDistributionBehavior.class);
    final var record = newRecord();
    stubBehaviorToVisit(distributionBehavior, record);
    final var scheduler = newScheduler(distributionBehavior, /* scaling */ false);

    // when
    final Result result = scheduler.run(FakeTaskContext.create().withClockMillis(1_000L));

    // then
    assertThat(result.decision()).isEqualTo(Decision.IDLE);
    verify(distributionBehavior, never()).onScheduledRetry(eq(DISTRIBUTION_KEY), any());
  }

  @Test
  void shouldRetryOnSecondVisit() {
    // given — second visit -> cycle=1; bitCount(1)==1 -> retry
    final var distributionBehavior = mock(CommandDistributionBehavior.class);
    final var record = newRecord();
    stubBehaviorToVisit(distributionBehavior, record);
    final var scheduler = newScheduler(distributionBehavior, /* scaling */ false);
    scheduler.run(FakeTaskContext.create().withClockMillis(1_000L)); // priming visit

    // when
    scheduler.run(FakeTaskContext.create().withClockMillis(2_000L));

    // then
    verify(distributionBehavior, times(1)).onScheduledRetry(eq(DISTRIBUTION_KEY), eq(record));
  }

  @Test
  void shouldSkipDistributionsForScalingPartition() {
    // given
    final var distributionBehavior = mock(CommandDistributionBehavior.class);
    final var record = newRecord();
    stubBehaviorToVisit(distributionBehavior, record);
    final var scheduler = newScheduler(distributionBehavior, /* scaling */ true);

    // when — second visit would normally retry, but partition is scaling
    scheduler.run(FakeTaskContext.create().withClockMillis(1_000L));
    scheduler.run(FakeTaskContext.create().withClockMillis(2_000L));

    // then
    verify(distributionBehavior, never()).onScheduledRetry(anyLong(), any());
  }

  private static CommandDistributionRecord newRecord() {
    return new CommandDistributionRecord()
        .setPartitionId(RECEIVER_PARTITION)
        .setValueType(ValueType.DEPLOYMENT)
        .setIntent(DeploymentIntent.CREATE);
  }

  private static void stubBehaviorToVisit(
      final CommandDistributionBehavior behavior, final CommandDistributionRecord record) {
    doAnswer(
            inv -> {
              final PendingDistributionVisitor visitor = inv.getArgument(0);
              visitor.visit(DISTRIBUTION_KEY, record);
              return null;
            })
        .when(behavior)
        .foreachRetriableDistribution(any());
  }

  private static CommandRedistributionScheduler newScheduler(
      final CommandDistributionBehavior distributionBehavior, final boolean scaling) {
    final var routing = mock(RoutingInfo.class);
    when(routing.isPartitionScaling(RECEIVER_PARTITION)).thenReturn(scaling);
    final var config =
        new EngineConfiguration()
            .setCommandRedistributionInterval(Duration.ofSeconds(10))
            .setCommandRedistributionMaxBackoff(Duration.ofMinutes(5));
    return new CommandRedistributionScheduler(distributionBehavior, routing, config);
  }
}
```

- [ ] **Step 2: Rewrite the implementation**

Replace `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/distribution/CommandRedistributionScheduler.java` with:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.distribution;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.scheduled.api.Result;
import io.camunda.zeebe.engine.processing.scheduled.api.ScheduledTask;
import io.camunda.zeebe.engine.processing.scheduled.api.TaskContext;
import io.camunda.zeebe.engine.state.routing.RoutingInfo;
import io.camunda.zeebe.protocol.impl.record.value.distribution.CommandDistributionRecord;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Retries sending {@link CommandDistributionRecord}s to other partitions, using exponential backoff
 * per distribution.
 *
 * <p>Periodic, fixed cadence: returns {@link Result.Builder#idle()} on every run; the runtime fires
 * us again at the configured fallback interval. Inter-partition sends go through the (legacy)
 * {@link io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior}, which
 * retains its own {@link io.camunda.zeebe.stream.api.InterPartitionCommandSender} reference for
 * historical reasons.
 *
 * <p>{@code retryCyclesPerDistribution} is accumulated state across runs, not a resume cursor — it
 * survives between runs as an instance field rather than via {@code TaskContext.resumeCursor()}.
 *
 * <p>Whether to skip running entirely (operator pause) is enforced at wiring time: when {@code
 * config.isCommandDistributionPaused()} is set, the runtime is simply not registered.
 */
public final class CommandRedistributionScheduler implements ScheduledTask<Void> {

  private static final Logger LOG = LoggerFactory.getLogger(CommandRedistributionScheduler.class);

  private final CommandDistributionBehavior distributionBehavior;
  private final RoutingInfo routingInfo;
  private final long maxRetryCycles;
  private final Map<RetriableDistribution, Long> retryCyclesPerDistribution = new HashMap<>();

  public CommandRedistributionScheduler(
      final CommandDistributionBehavior distributionBehavior,
      final RoutingInfo routingInfo,
      final EngineConfiguration config) {
    this.distributionBehavior = distributionBehavior;
    this.routingInfo = routingInfo;
    final Duration interval = config.getCommandRedistributionInterval();
    maxRetryCycles = config.getCommandRedistributionMaxBackoff().dividedBy(interval);
  }

  @Override
  public String name() {
    return "command-redistribution";
  }

  @Override
  public Result run(final TaskContext<Void> ctx) {
    final Result.Builder<Void> result = ctx.result();
    final HashSet<RetriableDistribution> visited = new HashSet<>();
    distributionBehavior.foreachRetriableDistribution(
        (distributionKey, record) -> {
          if (routingInfo.isPartitionScaling(record.getPartitionId())) {
            LOG.debug(
                "Excluding distribution {} for partition {} as it is currently scaling up.",
                distributionKey,
                record.getPartitionId());
            return true;
          }

          final RetriableDistribution retriable =
              RetriableDistribution.from(distributionKey, record);
          final long retryCycle = updateRetryCycle(retriable);

          if (retriable.shouldRetryNow(retryCycle, maxRetryCycles)) {
            LOG.info(
                "Retrying to distribute retriable command {} ({}.{}) to partition {} (Cycle: #{})",
                distributionKey,
                record.getValueType(),
                record.getIntent(),
                record.getPartitionId(),
                retryCycle);
            distributionBehavior.onScheduledRetry(distributionKey, record);
          }

          visited.add(retriable);
          return true;
        });

    // Drop tracking for distributions that have been completed since the last run.
    retryCyclesPerDistribution.keySet().removeIf(Predicate.not(visited::contains));

    return result.idle();
  }

  private long updateRetryCycle(final RetriableDistribution retriable) {
    return retryCyclesPerDistribution.compute(
        retriable, (k, retryCycles) -> retryCycles != null ? retryCycles + 1 : 0L);
  }

  private record RetriableDistribution(long distributionKey, int partitionId) {
    boolean shouldRetryNow(final long retryCycle, final long maxRetryCycles) {
      if (retryCycle >= maxRetryCycles) {
        return retryCycle % maxRetryCycles == 0;
      }
      return Long.bitCount(retryCycle) == 1;
    }

    static RetriableDistribution from(
        final long distributionKey, final CommandDistributionRecord record) {
      return new RetriableDistribution(distributionKey, record.getPartitionId());
    }
  }
}
```

- [ ] **Step 3: Stage and commit**

```bash
git add zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/distribution/CommandRedistributionScheduler.java \
        zeebe/engine/src/test/java/io/camunda/zeebe/engine/processing/distribution/CommandRedistributionSchedulerTest.java
git commit -m "$(cat <<'EOF'
refactor: migrate CommandRedistributionScheduler to Result

Declares the task as ScheduledTask<Void>. retryCyclesPerDistribution is
accumulated state across runs (not a resume cursor) and stays as a
field, per the design spec.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: Migrate `JobTimeoutCheckScheduler` (`<JobTimeoutCursor>`, the cursor case)

**Files:**
- Create: `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/job/JobTimeoutCursor.java`
- Modify: `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/job/JobTimeoutCheckScheduler.java`
- Modify: `zeebe/engine/src/test/java/io/camunda/zeebe/engine/processing/job/JobTimeoutCheckSchedulerTest.java`

The cursor migration. The previous `executionTimestamp = -1` and `startAtIndex = null` instance fields are deleted and replaced by a public record bundling `(executionTimestamp, resumeFrom)`. The `executionTimestamp` MUST travel inside the cursor — recomputing it on resume would break the "all entries within one continuation evaluated against one `now`" guarantee that the existing `shouldYieldAndKeepCursorWhenStoppedEarly` test asserts.

`JobTimeoutCursor` is its own top-level public class so the implementation, the test, and (anticipated) future consumers can reference it.

- [ ] **Step 1: Create `JobTimeoutCursor.java`**

Write this exact content to `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/job/JobTimeoutCursor.java`:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.state.immutable.JobState.DeadlineIndex;

/**
 * Resume cursor for {@link JobTimeoutCheckScheduler}. Carries both the entry to resume from and
 * the execution timestamp the previous run started with — keeping the timestamp inside the cursor
 * preserves the invariant that all entries within one continuation are evaluated against the same
 * {@code now}.
 */
public record JobTimeoutCursor(long executionTimestamp, DeadlineIndex resumeFrom) {}
```

- [ ] **Step 2: Rewrite the test**

Replace `zeebe/engine/src/test/java/io/camunda/zeebe/engine/processing/job/JobTimeoutCheckSchedulerTest.java` with:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.processing.scheduled.api.Result;
import io.camunda.zeebe.engine.processing.scheduled.api.Result.Decision;
import io.camunda.zeebe.engine.processing.scheduled.runtime.FakeTaskContext;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.JobState.DeadlineIndex;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import java.util.function.BiPredicate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

final class JobTimeoutCheckSchedulerTest {

  @Test
  void shouldReturnIdleWhenQueueDrains() {
    // given
    final JobState state = mock(JobState.class);
    when(state.forEachTimedOutEntry(anyLong(), any(), any())).thenReturn(null);
    final var scheduler = new JobTimeoutCheckScheduler(state, 100);

    // when
    final Result result =
        scheduler.run(
            FakeTaskContext.createFor(JobTimeoutCursor.class).withClockMillis(1_000L));

    // then
    assertThat(result.decision()).isEqualTo(Decision.IDLE);
  }

  @Test
  void shouldYieldAndKeepCursorWhenStoppedEarly() {
    // given
    final JobState state = mock(JobState.class);
    final DeadlineIndex resumeFrom = new DeadlineIndex(2_000L, 99L);
    when(state.forEachTimedOutEntry(anyLong(), any(), any())).thenReturn(resumeFrom);
    final var scheduler = new JobTimeoutCheckScheduler(state, 100);

    // when — first run yields and produces a cursor
    final Result first =
        scheduler.run(
            FakeTaskContext.createFor(JobTimeoutCursor.class).withClockMillis(1_000L));

    // then
    assertThat(first.decision()).isInstanceOf(Decision.YieldNow.class);
    final JobTimeoutCursor cursor =
        (JobTimeoutCursor) ((Decision.YieldNow) first.decision()).cursor();
    assertThat(cursor.resumeFrom()).isEqualTo(resumeFrom);
    assertThat(cursor.executionTimestamp()).isEqualTo(1_000L);

    // and a follow-up run started with that cursor resumes at the saved index, preserving the
    // original executionTimestamp even when the wall-clock has advanced
    final ArgumentCaptor<Long> tsCaptor = ArgumentCaptor.forClass(Long.class);
    final ArgumentCaptor<DeadlineIndex> startAt = ArgumentCaptor.forClass(DeadlineIndex.class);
    when(state.forEachTimedOutEntry(tsCaptor.capture(), startAt.capture(), any())).thenReturn(null);

    scheduler.run(
        FakeTaskContext.createFor(JobTimeoutCursor.class)
            .withClockMillis(5_000L)
            .withResumeCursor(cursor));

    assertThat(startAt.getValue()).isEqualTo(resumeFrom);
    assertThat(tsCaptor.getValue()).isEqualTo(1_000L);
  }

  @Test
  void shouldEmitTimeOutCommandsUpToBatchLimit() {
    // given
    final JobState state = mock(JobState.class);
    final ArgumentCaptor<BiPredicate<Long, JobRecord>> visitor = visitorCaptor();
    when(state.forEachTimedOutEntry(anyLong(), any(), visitor.capture())).thenReturn(null);
    final var scheduler = new JobTimeoutCheckScheduler(state, 2);
    final var ctx = FakeTaskContext.createFor(JobTimeoutCursor.class).withClockMillis(1_000L);

    // when
    scheduler.run(ctx);
    final var record = new JobRecord();
    final boolean firstAccepted = visitor.getValue().test(1L, record);
    final boolean secondAccepted = visitor.getValue().test(2L, record);
    final boolean thirdAccepted = visitor.getValue().test(3L, record);

    // then — first two commands appended, third rejected because batch limit was reached
    assertThat(firstAccepted).isTrue();
    assertThat(secondAccepted).isTrue();
    assertThat(thirdAccepted).isFalse();
    assertThat(ctx.lastResult().appendedCommands()).hasSize(2);
    assertThat(ctx.lastResult().appendedCommands())
        .allMatch(c -> c.intent() == JobIntent.TIME_OUT);
  }

  @Test
  void shouldUseClockNowAsExecutionTimestampOnFirstRun() {
    // given — no resume cursor on the context
    final JobState state = mock(JobState.class);
    final ArgumentCaptor<Long> tsCaptor = ArgumentCaptor.forClass(Long.class);
    when(state.forEachTimedOutEntry(tsCaptor.capture(), eq(null), any())).thenReturn(null);
    final var scheduler = new JobTimeoutCheckScheduler(state, 10);

    // when
    scheduler.run(FakeTaskContext.createFor(JobTimeoutCursor.class).withClockMillis(7_500L));

    // then
    assertThat(tsCaptor.getValue()).isEqualTo(7_500L);
  }

  @SuppressWarnings("unchecked")
  private static ArgumentCaptor<BiPredicate<Long, JobRecord>> visitorCaptor() {
    return ArgumentCaptor.forClass(BiPredicate.class);
  }
}
```

- [ ] **Step 3: Rewrite the implementation**

Replace `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/job/JobTimeoutCheckScheduler.java` with:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.processing.scheduled.api.Result;
import io.camunda.zeebe.engine.processing.scheduled.api.ScheduledTask;
import io.camunda.zeebe.engine.processing.scheduled.api.TaskContext;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.JobState.DeadlineIndex;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import org.agrona.collections.MutableInteger;

/**
 * Times out activated jobs whose deadline has passed by writing a {@link JobIntent#TIME_OUT}
 * command for each.
 *
 * <p>Periodic with continuation: returns {@link Result.Builder#yieldNow(Object) yieldNow(cursor)}
 * when the configured batch limit is hit; the runtime stores the {@link JobTimeoutCursor} and
 * hands it back via {@link TaskContext#resumeCursor()} on the next run, so iteration resumes where
 * this run left off and is evaluated against the same execution timestamp. When the queue drains,
 * the run terminates with {@link Result.Builder#idle()} and the runtime clears the cursor.
 */
public final class JobTimeoutCheckScheduler implements ScheduledTask<JobTimeoutCursor> {

  private final JobState state;
  private final int batchLimit;

  public JobTimeoutCheckScheduler(final JobState state, final int batchLimit) {
    this.state = state;
    this.batchLimit = batchLimit;
  }

  @Override
  public String name() {
    return "job-timeout-check";
  }

  @Override
  public Result run(final TaskContext<JobTimeoutCursor> ctx) {
    final Result.Builder<JobTimeoutCursor> result = ctx.result();
    final JobTimeoutCursor saved = ctx.resumeCursor();
    final long executionTimestamp =
        saved != null ? saved.executionTimestamp() : ctx.clock().millis();
    final DeadlineIndex startAt = saved != null ? saved.resumeFrom() : null;

    final MutableInteger counter = new MutableInteger(0);

    final DeadlineIndex lastVisited =
        state.forEachTimedOutEntry(
            executionTimestamp,
            startAt,
            (key, record) -> {
              if (counter.getAndIncrement() >= batchLimit || ctx.shouldYield()) {
                return false;
              }
              return result.append(key, JobIntent.TIME_OUT, record);
            });

    if (lastVisited != null) {
      // Stopped early — keep the cursor so the runtime can resume on the next run.
      return result.yieldNow(new JobTimeoutCursor(executionTimestamp, lastVisited));
    }

    // Queue drained for this round.
    return result.idle();
  }
}
```

- [ ] **Step 4: Run all five scheduler tests**

By this point all five task migrations are done; the module should compile (apart from `ManagedScheduledTaskTest` and the registration sites, both updated in subsequent tasks — but those haven't been touched yet so they still reference `Outcome` and the non-generic constructor). To verify the migrated tests in isolation:

Run:

```bash
./mvnw verify -pl zeebe/engine \
  -Dtest='MessageTimeToLiveCheckSchedulerTest,JobBackoffCheckSchedulerTest,DueDateTimerCheckSchedulerTest,CommandRedistributionSchedulerTest,JobTimeoutCheckSchedulerTest' \
  -DskipTests=false -DskipITs -Dquickly -T1C
```

Expected: 16 tests pass (3 + 3 + 5 + 3 + 4 — note `JobTimeoutCheckSchedulerTest` gains a 4th test for the first-run-uses-clock-now assertion). If test-compile fails because `ManagedScheduledTaskTest` or the registration sites still reference deleted types, that's expected — Surefire will refuse to run anything until test sources compile. In that case, proceed to Task 10 first; the scheduler tests will be exercised by the green-up at the end of Task 12.

- [ ] **Step 5: Stage and commit**

```bash
git add zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/job/JobTimeoutCursor.java \
        zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/job/JobTimeoutCheckScheduler.java \
        zeebe/engine/src/test/java/io/camunda/zeebe/engine/processing/job/JobTimeoutCheckSchedulerTest.java
git commit -m "$(cat <<'EOF'
refactor: migrate JobTimeoutCheckScheduler to Result

Replaces the executionTimestamp/startAtIndex instance fields with a
JobTimeoutCursor record produced via Result.Builder.yieldNow and read
back from TaskContext.resumeCursor on the next run. The execution
timestamp travels inside the cursor so all entries in one continuation
remain evaluated against the same now.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 10: Update registration sites to `ManagedScheduledTask<>(...)`

**Files:**
- Modify: `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/EngineProcessors.java` (lines ~130, ~767)
- Modify: `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/job/JobEventProcessors.java` (lines ~58, ~65)
- Modify: `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/message/MessageEventProcessors.java` (line ~152)

`ManagedScheduledTask` is now generic. Diamond inference picks `<C>` from the `ScheduledTask<C>` argument, so the only edit per call site is `new ManagedScheduledTask(` → `new ManagedScheduledTask<>(`.

- [ ] **Step 1: Edit `EngineProcessors.java` line ~130 (DueDateTimerCheckScheduler)**

Open `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/EngineProcessors.java`. At the line currently reading `new ManagedScheduledTask(` (around line 130, the call wrapping `DueDateTimerCheckScheduler`), change to `new ManagedScheduledTask<>(`. Keep all subsequent lines unchanged.

- [ ] **Step 2: Edit `EngineProcessors.java` line ~767 (CommandRedistributionScheduler)**

Same file. The second call to `new ManagedScheduledTask(` (around line 767, wrapping `task` which is the `CommandRedistributionScheduler`) — change to `new ManagedScheduledTask<>(`.

- [ ] **Step 3: Edit `JobEventProcessors.java` lines ~58 and ~65**

Open `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/job/JobEventProcessors.java`. There are two `new ManagedScheduledTask(` calls (the `JobBackoffCheckScheduler` registration around line 58 and the `JobTimeoutCheckScheduler` registration around line 65). Change both to `new ManagedScheduledTask<>(`.

- [ ] **Step 4: Edit `MessageEventProcessors.java` line ~152**

Open `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/message/MessageEventProcessors.java`. The fully-qualified call `new io.camunda.zeebe.engine.processing.scheduled.runtime.ManagedScheduledTask(` (around line 152) — change to `new io.camunda.zeebe.engine.processing.scheduled.runtime.ManagedScheduledTask<>(`.

- [ ] **Step 5: Verify the main sources compile**

Run: `./mvnw -pl zeebe/engine compile -Dquickly -T1C`
Expected: `BUILD SUCCESS`. The runtime, the API package, all five task implementations, and all registration sites are in sync. Only `ManagedScheduledTaskTest` is still on the old API; that's the next task.

- [ ] **Step 6: Stage and commit**

```bash
git add zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/EngineProcessors.java \
        zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/job/JobEventProcessors.java \
        zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/message/MessageEventProcessors.java
git commit -m "$(cat <<'EOF'
refactor: pass cursor type to ManagedScheduledTask call sites

Updates the five ManagedScheduledTask registration sites to use diamond
inference now that the runtime is generic on the task's cursor type.
No behaviour change.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 11: Migrate `ManagedScheduledTaskTest` to the `Result` API

**Files:**
- Modify: `zeebe/engine/src/test/java/io/camunda/zeebe/engine/processing/scheduled/runtime/ManagedScheduledTaskTest.java`

The runtime's own test. Two things change:
1. `Outcome` references become `Result.Decision` references; the test helpers create tasks that return a pre-built `Result` rather than a pre-built `Outcome`.
2. The two assertions that currently exercise `ctx.sink().append(...)` and `ctx.sink().sendInterPartition(...)` move to `ctx.result().append(...)` and `ctx.result().sendInterPartition(...)` respectively.

The test class is a faithful translation; no new assertions yet (those come in Task 12).

- [ ] **Step 1: Rewrite `ManagedScheduledTaskTest.java`**

Replace the entire file with:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.scheduled.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.processing.scheduled.api.Result;
import io.camunda.zeebe.engine.processing.scheduled.api.Result.Decision;
import io.camunda.zeebe.engine.processing.scheduled.api.Schedule;
import io.camunda.zeebe.engine.processing.scheduled.api.ScheduledTask;
import io.camunda.zeebe.engine.processing.scheduled.api.TaskContext;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.stream.api.InterPartitionCommandSender;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamClock;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.camunda.zeebe.stream.api.scheduling.SimpleProcessingScheduleService;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class ManagedScheduledTaskTest {

  private static final long FIXED_NOW_MS = 1_700_000_000_000L;

  private ProcessingScheduleService scheduleService;
  private InterPartitionCommandSender interPartitionSender;
  private ReadonlyStreamProcessorContext context;
  private StreamClock clock;

  @BeforeEach
  void setUp() {
    scheduleService = mock(ProcessingScheduleService.class);
    interPartitionSender = mock(InterPartitionCommandSender.class);
    context = mock(ReadonlyStreamProcessorContext.class);
    clock = StreamClock.uncontrolled(InstantSource.fixed(Instant.ofEpochMilli(FIXED_NOW_MS)));

    when(context.getScheduleService()).thenReturn(scheduleService);
    when(context.getClock()).thenReturn(clock);
    when(context.getPartitionId()).thenReturn(1);
    when(scheduleService.runAt(anyLong(), any(Task.class)))
        .thenReturn(mock(SimpleProcessingScheduleService.ScheduledTask.class));
    when(scheduleService.runAtAsync(anyLong(), any(Task.class)))
        .thenReturn(mock(SimpleProcessingScheduleService.ScheduledTask.class));
  }

  @Test
  void shouldScheduleImmediatelyOnRecoveredWhenFallbackConfigured() {
    // given
    final var task = newRecordingTask(Result.Builder::idle);
    final var managed =
        new ManagedScheduledTask<>(
            task,
            Schedule.fixedRate(Duration.ofSeconds(30)),
            interPartitionSender,
            new SimpleMeterRegistry());

    // when
    managed.onRecovered(context);

    // then
    verify(scheduleService).runAt(anyLong(), any(Task.class));
  }

  @Test
  void shouldScheduleInitialRunOnRecoveredEvenWhenPureOnDemand() {
    // given
    final var task = newRecordingTask(Result.Builder::idle);
    final var managed =
        new ManagedScheduledTask<>(
            task,
            Schedule.onDemand(Duration.ofMillis(100)),
            interPartitionSender,
            new SimpleMeterRegistry());

    // when
    managed.onRecovered(context);

    // then
    verify(scheduleService).runAt(eq(FIXED_NOW_MS + 100), any(Task.class));
  }

  @Test
  void shouldUseAsyncSchedulingWhenAsyncIsTrue() {
    // given
    final var task = newRecordingTask(Result.Builder::idle);
    final var managed =
        new ManagedScheduledTask<>(
            task,
            Schedule.fixedRate(Duration.ofSeconds(30)).withAsync(true),
            interPartitionSender,
            new SimpleMeterRegistry());

    // when
    managed.onRecovered(context);

    // then
    verify(scheduleService).runAtAsync(anyLong(), any(Task.class));
    verify(scheduleService, never()).runAt(anyLong(), any(Task.class));
  }

  @Test
  void shouldScheduleAtAwaitDueAtTimestamp() {
    // given
    final long dueAt = FIXED_NOW_MS + 60_000;
    final var task = newRecordingTask(builder -> builder.awaitDueAt(dueAt));
    final var managed =
        new ManagedScheduledTask<>(
            task,
            Schedule.onDemand(Duration.ofMillis(10)),
            interPartitionSender,
            new SimpleMeterRegistry());
    managed.onRecovered(context);
    clearInvocations(scheduleService);

    // when
    managed.execute(mock(TaskResultBuilder.class));

    // then
    verify(scheduleService).runAt(eq(dueAt), any(Task.class));
  }

  @Test
  void shouldRescheduleImmediatelyOnYieldNow() {
    // given
    final var task = newRecordingTask(Result.Builder::yieldNow);
    final var managed =
        new ManagedScheduledTask<>(
            task,
            Schedule.fixedRate(Duration.ofSeconds(30)).withYieldBudget(Duration.ofMillis(50)),
            interPartitionSender,
            new SimpleMeterRegistry());
    managed.onRecovered(context);
    clearInvocations(scheduleService);

    // when
    managed.execute(mock(TaskResultBuilder.class));

    // then
    verify(scheduleService)
        .runAt(eq(FIXED_NOW_MS + Schedule.DEFAULT_MIN_RESOLUTION.toMillis()), any(Task.class));
  }

  @Test
  void shouldRescheduleAtFallbackOnIdle() {
    // given
    final Duration fallback = Duration.ofSeconds(30);
    final var task = newRecordingTask(Result.Builder::idle);
    final var managed =
        new ManagedScheduledTask<>(
            task, Schedule.fixedRate(fallback), interPartitionSender, new SimpleMeterRegistry());
    managed.onRecovered(context);
    clearInvocations(scheduleService);

    // when
    managed.execute(mock(TaskResultBuilder.class));

    // then
    verify(scheduleService).runAt(eq(FIXED_NOW_MS + fallback.toMillis()), any(Task.class));
  }

  @Test
  void shouldNotRescheduleOnIdleWhenPureOnDemand() {
    // given
    final var task = newRecordingTask(Result.Builder::idle);
    final var managed =
        new ManagedScheduledTask<>(
            task,
            Schedule.onDemand(Duration.ofMillis(10)),
            interPartitionSender,
            new SimpleMeterRegistry());
    managed.onRecovered(context);
    clearInvocations(scheduleService);

    // when
    managed.execute(mock(TaskResultBuilder.class));

    // then
    verifyNoInteractions(scheduleService);
  }

  @Test
  void shouldHonorRequestRunAfterInitialRunConsumed() {
    // given
    final var task = newRecordingTask(Result.Builder::idle);
    final var managed =
        new ManagedScheduledTask<>(
            task,
            Schedule.onDemand(Duration.ofMillis(10)),
            interPartitionSender,
            new SimpleMeterRegistry());
    managed.onRecovered(context);
    managed.execute(mock(TaskResultBuilder.class));
    clearInvocations(scheduleService);

    // when
    managed.requestRun(FIXED_NOW_MS + 1_000);

    // then
    verify(scheduleService).runAt(eq(FIXED_NOW_MS + 1_000), any(Task.class));
  }

  @Test
  void shouldClampRequestRunToMinResolution() {
    // given
    final var task = newRecordingTask(Result.Builder::idle);
    final var managed =
        new ManagedScheduledTask<>(
            task,
            Schedule.onDemand(Duration.ofMillis(100)),
            interPartitionSender,
            new SimpleMeterRegistry());
    managed.onRecovered(context);
    managed.execute(mock(TaskResultBuilder.class));
    clearInvocations(scheduleService);

    // when
    managed.requestRun(FIXED_NOW_MS);

    // then
    verify(scheduleService).runAt(eq(FIXED_NOW_MS + 100), any(Task.class));
  }

  @Test
  void shouldRescheduleEarlierAndCancelPrevious() {
    // given
    final SimpleProcessingScheduleService.ScheduledTask initialHandle =
        mock(SimpleProcessingScheduleService.ScheduledTask.class);
    final SimpleProcessingScheduleService.ScheduledTask firstHandle =
        mock(SimpleProcessingScheduleService.ScheduledTask.class);
    final SimpleProcessingScheduleService.ScheduledTask secondHandle =
        mock(SimpleProcessingScheduleService.ScheduledTask.class);
    when(scheduleService.runAt(anyLong(), any(Task.class)))
        .thenReturn(initialHandle)
        .thenReturn(firstHandle)
        .thenReturn(secondHandle);
    final var task = newRecordingTask(Result.Builder::idle);
    final var managed =
        new ManagedScheduledTask<>(
            task,
            Schedule.onDemand(Duration.ofMillis(10)),
            interPartitionSender,
            new SimpleMeterRegistry());
    managed.onRecovered(context);
    managed.execute(mock(TaskResultBuilder.class));

    // when
    managed.requestRun(FIXED_NOW_MS + 1_000);
    managed.requestRun(FIXED_NOW_MS + 100);

    // then
    verify(scheduleService).runAt(eq(FIXED_NOW_MS + 1_000), any(Task.class));
    verify(scheduleService).runAt(eq(FIXED_NOW_MS + 100), any(Task.class));
    verify(firstHandle).cancel();
    verify(secondHandle, never()).cancel();
  }

  @Test
  void shouldNotRescheduleWhenAlreadyScheduledEarlier() {
    // given
    final var task = newRecordingTask(Result.Builder::idle);
    final var managed =
        new ManagedScheduledTask<>(
            task,
            Schedule.onDemand(Duration.ofMillis(10)),
            interPartitionSender,
            new SimpleMeterRegistry());
    managed.onRecovered(context);
    managed.execute(mock(TaskResultBuilder.class));
    clearInvocations(scheduleService);

    // when
    managed.requestRun(FIXED_NOW_MS + 100);
    managed.requestRun(FIXED_NOW_MS + 1_000);

    // then
    verify(scheduleService, times(1)).runAt(anyLong(), any(Task.class));
  }

  @Test
  void shouldIgnoreRequestRunWhenNotEnabled() {
    // given
    final var task = newRecordingTask(Result.Builder::idle);
    final var managed =
        new ManagedScheduledTask<>(
            task,
            Schedule.onDemand(Duration.ofMillis(10)),
            interPartitionSender,
            new SimpleMeterRegistry());

    // when
    managed.requestRun(FIXED_NOW_MS + 1_000);

    // then
    verifyNoInteractions(scheduleService);
  }

  @Test
  void shouldDisableAndCancelOnPaused() {
    // given
    final SimpleProcessingScheduleService.ScheduledTask handle =
        mock(SimpleProcessingScheduleService.ScheduledTask.class);
    when(scheduleService.runAt(anyLong(), any(Task.class))).thenReturn(handle);
    final var task = newRecordingTask(Result.Builder::idle);
    final var managed =
        new ManagedScheduledTask<>(
            task,
            Schedule.onDemand(Duration.ofMillis(10)),
            interPartitionSender,
            new SimpleMeterRegistry());
    managed.onRecovered(context);
    managed.requestRun(FIXED_NOW_MS + 100);

    // when
    managed.onPaused();

    // then
    verify(handle).cancel();
    clearInvocations(scheduleService);
    managed.requestRun(FIXED_NOW_MS + 100);
    verifyNoInteractions(scheduleService);
  }

  @Test
  void shouldReenableAndScheduleOnResumed() {
    // given
    final var task = newRecordingTask(Result.Builder::idle);
    final var managed =
        new ManagedScheduledTask<>(
            task,
            Schedule.fixedRate(Duration.ofSeconds(30)),
            interPartitionSender,
            new SimpleMeterRegistry());
    managed.onRecovered(context);
    managed.onPaused();
    clearInvocations(scheduleService);

    // when
    managed.onResumed();

    // then
    verify(scheduleService).runAt(anyLong(), any(Task.class));
  }

  @Test
  void shouldDisableOnCloseAndOnFailed() {
    // given
    final var task = newRecordingTask(Result.Builder::idle);
    final var managed =
        new ManagedScheduledTask<>(
            task,
            Schedule.onDemand(Duration.ofMillis(10)),
            interPartitionSender,
            new SimpleMeterRegistry());
    managed.onRecovered(context);

    // when
    managed.onClose();

    // then
    clearInvocations(scheduleService);
    managed.requestRun(FIXED_NOW_MS + 100);
    verifyNoInteractions(scheduleService);

    managed.onResumed();
    managed.onFailed();
    clearInvocations(scheduleService);
    managed.requestRun(FIXED_NOW_MS + 100);
    verifyNoInteractions(scheduleService);
  }

  @Test
  void shouldCatchExceptionsFromTaskAndReschedulePerFallback() {
    // given
    final ScheduledTask<Void> task =
        new ScheduledTask<>() {
          @Override
          public String name() {
            return "boom";
          }

          @Override
          public Result run(final TaskContext<Void> ctx) {
            throw new RuntimeException("boom");
          }
        };
    final Duration fallback = Duration.ofSeconds(30);
    final var managed =
        new ManagedScheduledTask<>(
            task, Schedule.fixedRate(fallback), interPartitionSender, new SimpleMeterRegistry());
    managed.onRecovered(context);
    clearInvocations(scheduleService);

    // when
    final TaskResultBuilder builder = mock(TaskResultBuilder.class);
    when(builder.build()).thenReturn(mock(TaskResult.class));
    final TaskResult result = managed.execute(builder);

    // then
    assertThat(result).isNotNull();
    verify(scheduleService).runAt(eq(FIXED_NOW_MS + fallback.toMillis()), any(Task.class));
  }

  @Test
  void shouldExposeContextToTask() {
    // given
    final AtomicReference<TaskContext<Void>> seen = new AtomicReference<>();
    final ScheduledTask<Void> task =
        new ScheduledTask<>() {
          @Override
          public String name() {
            return "capture";
          }

          @Override
          public Result run(final TaskContext<Void> ctx) {
            seen.set(ctx);
            return ctx.result().idle();
          }
        };
    final var managed =
        new ManagedScheduledTask<>(
            task,
            Schedule.fixedRate(Duration.ofSeconds(30)),
            interPartitionSender,
            new SimpleMeterRegistry());
    managed.onRecovered(context);

    // when
    managed.execute(mock(TaskResultBuilder.class));

    // then
    assertThat(seen.get()).isNotNull();
    assertThat(seen.get().clock().millis()).isEqualTo(FIXED_NOW_MS);
    assertThat(seen.get().partitionId()).isEqualTo(1);
    assertThat(seen.get().shouldYield()).isFalse();
  }

  @Test
  void shouldDelegateAppendToTaskResultBuilder() {
    // given
    final var record = new JobRecord();
    final TaskResultBuilder builder = mock(TaskResultBuilder.class);
    when(builder.appendCommandRecord(anyLong(), any(), any())).thenReturn(true);
    when(builder.build()).thenReturn(mock(TaskResult.class));
    final ScheduledTask<Void> task =
        new ScheduledTask<>() {
          @Override
          public String name() {
            return "appender";
          }

          @Override
          public Result run(final TaskContext<Void> ctx) {
            final Result.Builder<Void> r = ctx.result();
            r.append(42L, JobIntent.TIME_OUT, record);
            return r.idle();
          }
        };
    final var managed =
        new ManagedScheduledTask<>(
            task,
            Schedule.fixedRate(Duration.ofSeconds(30)),
            interPartitionSender,
            new SimpleMeterRegistry());
    managed.onRecovered(context);

    // when
    managed.execute(builder);

    // then
    verify(builder).appendCommandRecord(eq(42L), eq(JobIntent.TIME_OUT), eq(record));
  }

  @Test
  void shouldDelegateInterPartitionSendToSender() {
    // given
    final var record = new JobRecord();
    final ScheduledTask<Void> task =
        new ScheduledTask<>() {
          @Override
          public String name() {
            return "sender";
          }

          @Override
          public Result run(final TaskContext<Void> ctx) {
            final Result.Builder<Void> r = ctx.result();
            r.sendInterPartition(2, ValueType.JOB, JobIntent.CANCEL, 7L, record);
            return r.idle();
          }
        };
    final var managed =
        new ManagedScheduledTask<>(
            task,
            Schedule.fixedRate(Duration.ofSeconds(30)),
            interPartitionSender,
            new SimpleMeterRegistry());
    managed.onRecovered(context);

    // when
    managed.execute(mock(TaskResultBuilder.class));

    // then
    verify(interPartitionSender)
        .sendCommand(eq(2), eq(ValueType.JOB), eq(JobIntent.CANCEL), eq(7L), eq(record), eq(null));
  }

  @Test
  void shouldExposeShouldYieldOnceBudgetElapsed() {
    // given
    final var controlClock = StreamClock.controllable(InstantSource.system());
    controlClock.pinAt(Instant.ofEpochMilli(FIXED_NOW_MS));
    when(context.getClock()).thenReturn(controlClock);

    final AtomicReference<Boolean> beforeBudget = new AtomicReference<>();
    final AtomicReference<Boolean> afterBudget = new AtomicReference<>();
    final ScheduledTask<Void> task =
        new ScheduledTask<>() {
          @Override
          public String name() {
            return "yielder";
          }

          @Override
          public Result run(final TaskContext<Void> ctx) {
            beforeBudget.set(ctx.shouldYield());
            controlClock.pinAt(Instant.ofEpochMilli(FIXED_NOW_MS + 100));
            afterBudget.set(ctx.shouldYield());
            return ctx.result().yieldNow();
          }
        };
    final var managed =
        new ManagedScheduledTask<>(
            task,
            Schedule.fixedRate(Duration.ofSeconds(30)).withYieldBudget(Duration.ofMillis(50)),
            interPartitionSender,
            new SimpleMeterRegistry());
    managed.onRecovered(context);

    // when
    managed.execute(mock(TaskResultBuilder.class));

    // then
    assertThat(beforeBudget.get()).isFalse();
    assertThat(afterBudget.get()).isTrue();
  }

  @Test
  void shouldClearPendingNextRunBeforeExecutingTaskBody() {
    // given
    final var task = newRecordingTask(Result.Builder::idle);
    final var managed =
        new ManagedScheduledTask<>(
            task,
            Schedule.fixedRate(Duration.ofSeconds(30)),
            interPartitionSender,
            new SimpleMeterRegistry());
    managed.onRecovered(context);

    // when
    managed.execute(mock(TaskResultBuilder.class));
    managed.requestRun(FIXED_NOW_MS + 100);

    // then
    verify(scheduleService, atLeastOnce()).runAt(eq(FIXED_NOW_MS + 100), any(Task.class));
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /**
   * Build a {@link ScheduledTask} that always returns the same {@link Result}, picked by applying
   * {@code terminal} to {@code ctx.result()}. Used wherever the previous test built an
   * {@code Outcome} singleton (e.g. {@code Outcome.IDLE}, {@code Outcome.YIELD_NOW}).
   */
  private static ScheduledTask<Void> newRecordingTask(
      final Function<Result.Builder<Void>, Result> terminal) {
    return new ScheduledTask<>() {
      @Override
      public String name() {
        return "recording";
      }

      @Override
      public Result run(final TaskContext<Void> ctx) {
        return terminal.apply(ctx.result());
      }
    };
  }
}
```

- [ ] **Step 2: Run the test class**

Run:

```bash
./mvnw verify -pl zeebe/engine -Dtest=ManagedScheduledTaskTest -DskipTests=false -DskipITs -Dquickly -T1C
```

Expected: 22 tests pass (same count as before — pure translation, no new tests in this commit).

- [ ] **Step 3: Stage and commit**

```bash
git add zeebe/engine/src/test/java/io/camunda/zeebe/engine/processing/scheduled/runtime/ManagedScheduledTaskTest.java
git commit -m "$(cat <<'EOF'
test: translate ManagedScheduledTaskTest to Result API

Pure translation: every Outcome reference becomes a Result.Decision
reference; tasks that previously returned Outcome singletons now return
the Result produced by a Result.Builder terminal. No new assertions —
those land in the next commit.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 12: Add cursor-lifecycle tests to `ManagedScheduledTaskTest`

**Files:**
- Modify: `zeebe/engine/src/test/java/io/camunda/zeebe/engine/processing/scheduled/runtime/ManagedScheduledTaskTest.java`

Per the spec: assert that the runtime owns the cursor lifecycle correctly. Four new tests:

1. `yieldNow(c)` saves the cursor — the next run sees it on `TaskContext.resumeCursor()`.
2. `idle()` clears the cursor — after a `yieldNow(c)` then an `idle()`, the next run sees `null`.
3. `awaitDueAt(t)` clears the cursor.
4. `onPaused` clears the cursor — after a `yieldNow(c)` then `onPaused` then `onResumed`, the next run sees `null`.

These exercise behaviour the runtime newly owns and that no other test currently asserts.

- [ ] **Step 1: Add the four new tests**

Append the following methods to `ManagedScheduledTaskTest` immediately before the `// Helpers` section divider. Add the import `import java.util.ArrayList;` and `import java.util.List;` to the file's imports if they are not already present.

```java
  @Test
  void shouldExposeSavedCursorOnNextRunAfterYieldNow() {
    // given — a task that yields with a String cursor on the first run, then asserts the
    // runtime hands the same cursor back on the second run.
    final List<String> seenCursors = new ArrayList<>();
    final boolean[] firstRun = {true};
    final ScheduledTask<String> task =
        new ScheduledTask<>() {
          @Override
          public String name() {
            return "cursor-yield";
          }

          @Override
          public Result run(final TaskContext<String> ctx) {
            seenCursors.add(ctx.resumeCursor());
            if (firstRun[0]) {
              firstRun[0] = false;
              return ctx.result().yieldNow("saved");
            }
            return ctx.result().idle();
          }
        };
    final var managed =
        new ManagedScheduledTask<>(
            task,
            Schedule.fixedRate(Duration.ofSeconds(30)).withYieldBudget(Duration.ofMillis(50)),
            interPartitionSender,
            new SimpleMeterRegistry());
    managed.onRecovered(context);

    // when — run twice
    managed.execute(mock(TaskResultBuilder.class));
    managed.execute(mock(TaskResultBuilder.class));

    // then — first run saw null (no cursor yet), second run saw the saved cursor
    assertThat(seenCursors).containsExactly(null, "saved");
  }

  @Test
  void shouldClearSavedCursorAfterIdle() {
    // given — first run yields with a cursor, second run terminates with idle, third run should
    // see a null cursor again.
    final List<String> seenCursors = new ArrayList<>();
    final int[] runIndex = {0};
    final ScheduledTask<String> task =
        new ScheduledTask<>() {
          @Override
          public String name() {
            return "cursor-clear-on-idle";
          }

          @Override
          public Result run(final TaskContext<String> ctx) {
            seenCursors.add(ctx.resumeCursor());
            return switch (runIndex[0]++) {
              case 0 -> ctx.result().yieldNow("saved");
              case 1 -> ctx.result().idle();
              default -> ctx.result().idle();
            };
          }
        };
    final var managed =
        new ManagedScheduledTask<>(
            task,
            Schedule.fixedRate(Duration.ofSeconds(30)).withYieldBudget(Duration.ofMillis(50)),
            interPartitionSender,
            new SimpleMeterRegistry());
    managed.onRecovered(context);

    // when — run three times
    managed.execute(mock(TaskResultBuilder.class));
    managed.execute(mock(TaskResultBuilder.class));
    managed.execute(mock(TaskResultBuilder.class));

    // then — null, "saved" (carried from yield), null (cleared by idle)
    assertThat(seenCursors).containsExactly(null, "saved", null);
  }

  @Test
  void shouldClearSavedCursorAfterAwaitDueAt() {
    // given
    final List<String> seenCursors = new ArrayList<>();
    final int[] runIndex = {0};
    final ScheduledTask<String> task =
        new ScheduledTask<>() {
          @Override
          public String name() {
            return "cursor-clear-on-await";
          }

          @Override
          public Result run(final TaskContext<String> ctx) {
            seenCursors.add(ctx.resumeCursor());
            return switch (runIndex[0]++) {
              case 0 -> ctx.result().yieldNow("saved");
              case 1 -> ctx.result().awaitDueAt(FIXED_NOW_MS + 60_000);
              default -> ctx.result().idle();
            };
          }
        };
    final var managed =
        new ManagedScheduledTask<>(
            task,
            Schedule.fixedRate(Duration.ofSeconds(30)).withYieldBudget(Duration.ofMillis(50)),
            interPartitionSender,
            new SimpleMeterRegistry());
    managed.onRecovered(context);

    // when — run three times
    managed.execute(mock(TaskResultBuilder.class));
    managed.execute(mock(TaskResultBuilder.class));
    managed.execute(mock(TaskResultBuilder.class));

    // then — cursor cleared by awaitDueAt
    assertThat(seenCursors).containsExactly(null, "saved", null);
  }

  @Test
  void shouldClearSavedCursorOnPaused() {
    // given — yield with a cursor, then pause+resume; next run should see null.
    final List<String> seenCursors = new ArrayList<>();
    final int[] runIndex = {0};
    final ScheduledTask<String> task =
        new ScheduledTask<>() {
          @Override
          public String name() {
            return "cursor-clear-on-pause";
          }

          @Override
          public Result run(final TaskContext<String> ctx) {
            seenCursors.add(ctx.resumeCursor());
            return switch (runIndex[0]++) {
              case 0 -> ctx.result().yieldNow("saved");
              default -> ctx.result().idle();
            };
          }
        };
    final var managed =
        new ManagedScheduledTask<>(
            task,
            Schedule.fixedRate(Duration.ofSeconds(30)).withYieldBudget(Duration.ofMillis(50)),
            interPartitionSender,
            new SimpleMeterRegistry());
    managed.onRecovered(context);
    managed.execute(mock(TaskResultBuilder.class)); // first run: yields with "saved"

    // when — pause clears the cursor; resume re-arms; second run starts fresh
    managed.onPaused();
    managed.onResumed();
    managed.execute(mock(TaskResultBuilder.class));

    // then
    assertThat(seenCursors).containsExactly(null, null);
  }
```

- [ ] **Step 2: Run the test class**

Run:

```bash
./mvnw verify -pl zeebe/engine -Dtest=ManagedScheduledTaskTest -DskipTests=false -DskipITs -Dquickly -T1C
```

Expected: 26 tests pass (22 translated + 4 new cursor-lifecycle tests).

- [ ] **Step 3: Run the full module's unit tests**

Run:

```bash
./mvnw verify -pl zeebe/engine -DskipTests=false -DskipITs -Dquickly -T1C
```

Expected: `BUILD SUCCESS`. All scheduler unit tests + the runtime test pass. The five integration-style assertion paths from the prior PoC are intact.

If any unrelated test fails, investigate before continuing — do not skip or quarantine.

- [ ] **Step 4: Stage and commit**

```bash
git add zeebe/engine/src/test/java/io/camunda/zeebe/engine/processing/scheduled/runtime/ManagedScheduledTaskTest.java
git commit -m "$(cat <<'EOF'
test: cover cursor lifecycle in ManagedScheduledTaskTest

Adds four tests that pin the runtime's ownership of the resume cursor:
yieldNow(c) saves it; idle and awaitDueAt clear it; onPaused clears it.
These are the new behaviours the Result API contract guarantees and
were not asserted before.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 13: Format, full module verification, push, and open PR

**Files:** all touched in this PR (no further code changes; just tooling).

- [ ] **Step 1: Format**

Run:

```bash
./mvnw license:format spotless:apply -pl zeebe/engine -T1C
```

Expected: `BUILD SUCCESS`. License headers normalized, Google Java Format applied. If this command introduces changes (formatting drift), the changes are mechanical and safe.

- [ ] **Step 2: Inspect formatting changes (if any)**

Run: `git status` and `git diff --stat`
Expected: either no diff, or whitespace-only diffs. If there's a substantive diff (e.g. an import re-ordered), that's a sign of a manual error — review carefully.

If formatting changed any files, amend the most recent commit only when the change is purely formatting and confined to files already touched by that commit. Otherwise create a new commit:

```bash
git add zeebe/engine
git commit -m "$(cat <<'EOF'
style: spotless apply for Result API refactor

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step 3: Build + test the full module one more time**

Run:

```bash
./mvnw install -pl zeebe/engine -am -DskipTests=false -DskipITs -Dquickly -T1C
```

Expected: `BUILD SUCCESS`. The `-am` flag ensures upstream module changes (none expected) are picked up. This is the final pre-push gate.

- [ ] **Step 4: Push the branch**

Run:

```bash
git push -u origin korthout-camunda-8991-scheduled-tasks-result-api
```

Expected: branch created on origin, no force-push needed (this is a fresh branch).

- [ ] **Step 5: Open the PR**

The PR base is `main` (sibling PoC to PR #52604, not a stack on top of it). Use the repo PR template (`.github/pull_request_template.md` — fill every section; this is a strict project rule). Use `curl` against the GitHub REST API to avoid the local `gh` TLS issue.

First, capture the PR template:

```bash
PR_TEMPLATE=$(cat .github/pull_request_template.md)
echo "$PR_TEMPLATE"  # sanity-check: shows the section headings
```

Then construct the PR body filling every section. The PR title is short (≤ 70 chars) with no conventional-commit prefix per project convention.

Suggested title: `Sibling PoC: ScheduledTask Result API`

Suggested body skeleton (fill each section by reading the template's exact headings; do not invent sections that aren't in the template, do not omit sections that are):

```
## Description

Sibling PoC to #52604. Same base branch (`main`), so the two PoCs can be reviewed
side-by-side as alternative shapes for the new ScheduledTask API.

This branch starts from #52604's tip (`4ef27d30cc2`) and applies four changes:

1. ScheduledTask.run returns a single `Result` that carries appended commands,
   inter-partition sends, and the scheduling decision in one object — replacing
   the separate `Sink` (mutating side channel) + `Outcome` (return value) split.
2. The resume cursor moves from per-task instance fields onto the runtime, exposed
   via `TaskContext<C>.resumeCursor()`. Tasks become stateless across runs.
3. The cursor type is documented at the type level: `ScheduledTask<C>` declares
   the resume-state shape; tasks without continuation use `<Void>`.
4. Cursor lifetime is implicit, tied to the builder's terminal:
   `yieldNow(c)` saves; `idle()` / `awaitDueAt(t)` / lifecycle transitions clear.

Design spec: `docs/superpowers/specs/2026-05-07-scheduled-task-result-api-design.md`.

## Related issues

related to #8991

## Definition of Done

[fill from the template's checklist]

## Pull Request Checklist

[fill from the template's checklist]
```

To open the PR (replace `<owner>/<repo>` if needed; assume `camunda/camunda` based on the project):

```bash
TITLE="Sibling PoC: ScheduledTask Result API"
BODY=$(cat <<'EOF'
[paste the filled-in body here, with all template sections present]
EOF
)
JSON=$(jq -n --arg title "$TITLE" --arg body "$BODY" --arg head "korthout-camunda-8991-scheduled-tasks-result-api" --arg base "main" \
  '{title: $title, body: $body, head: $head, base: $base, draft: true}')
curl -s -X POST "https://api.github.com/repos/camunda/camunda/pulls" \
  -H "Authorization: token $(gh auth token)" \
  -H "Accept: application/vnd.github+json" \
  -d "$JSON"
```

Expected: HTTP 201; the response contains the new PR's `html_url`. Print that URL so the user can review.

If `jq` or template sections are unclear, prefer asking the user to confirm the body content before posting — opening a PR is visible-to-others, so confirm first if anything is ambiguous.

- [ ] **Step 6: Hand off**

Print the PR URL and a one-line summary of test counts (`./mvnw` output: e.g. `Tests run: 42, Failures: 0, Errors: 0`).

---

## Self-Review (Run Before Handoff)

This block is the plan-author's self-review run inline before declaring the plan complete.

**Spec coverage check (each spec section maps to at least one task):**
- Goals 1–4 (Result, runtime cursor, generic `<C>`, file-and-test scope parity) → Tasks 1–12.
- API surface (`ScheduledTask<C>`, `TaskContext<C>`, `Result` + `Result.Builder<C>`) → Task 1, Task 2.
- Cursor lifecycle table (idle/awaitDueAt/yieldNow + onPaused/onClose/onFailed) → Task 3 (`updateResumeCursor` + `disableAndCancel` clear), Task 12 (tests).
- Runtime sketch (Steps 1–7 of `execute`) → Task 3.
- Task migrations (5 implementations, 4 `<Void>` + 1 `<JobTimeoutCursor>`) → Tasks 5–9.
- `JobTimeoutCursor` record placement → Task 9 (top-level public class, not nested).
- Mechanical translation pattern for `DueDateTimerCheckScheduler` → Task 7 mirrors the spec snippet.
- Cursor-flow sketch for `JobTimeoutCheckScheduler` (executionTimestamp inside cursor) → Task 9 implementation matches; Task 9 test asserts the timestamp is preserved across yields.
- Test updates (`FakeTaskContext<C>`, `withResumeCursor`, accessors `appendedCommands` / `interPartitionSends` / `decision`) → Task 4. The fake also exposes `lastResult()` so tests can read both the records and the decision from one object.
- Branching (`korthout-camunda-8991-scheduled-tasks-result-api`, base `main`) → branch already exists; Task 13 pushes and opens the PR against `main`.
- Risk: generic propagation to registration sites → Task 10 (5 sites updated).
- Risk: `yieldNow()` no-arg shorthand on `<C>` taskies → Task 1's javadoc warns and Task 7 uses it correctly on a `<Void>` task.
- Risk: `JobTimeoutCheckScheduler` semantic preservation (timestamp in cursor) → Task 9 implementation + dedicated test (`shouldYieldAndKeepCursorWhenStoppedEarly` asserts captured timestamp).

**Placeholder scan:** no "TBD", no "implement later", no "similar to Task N" — every code block is fully expanded. ✅

**Type / signature consistency:**
- `ScheduledTask<C>` and `TaskContext<C>` are used identically in tasks 1, 2, 3, 4, 5–9, 11.
- `Result.Decision` (sealed: `Idle` / `AwaitDueAt` / `YieldNow`) is referenced as `Decision.IDLE`, `Decision.AwaitDueAt`, `Decision.YieldNow` everywhere.
- `Result.Builder<C>` terminals: `idle()` / `awaitDueAt(long)` / `yieldNow(C)` / `yieldNow()`. No accidental renames.
- `Result` accessors: `appendedCommands()`, `interPartitionSends()`, `decision()`. Used identically in `FakeTaskContext.lastResult()` and in every migrated test.
- `JobTimeoutCursor` accessors: `executionTimestamp()`, `resumeFrom()`. Used consistently in Task 9.
- `FakeTaskContext.create()` returns `FakeTaskContext<Void>`; `FakeTaskContext.createFor(Class<C>)` returns `FakeTaskContext<C>`. Both used correctly across tasks 5–9.

**Build / test gate placement:**
- After Task 1 (Result-only commit): module compiles. ✅
- After Tasks 2 + 3 (combined commit): module is broken-compile (intentional, called out). ✅
- After Tasks 5–9: scheduler tests run, but full module test-compile may still fail because of `ManagedScheduledTaskTest` and registration sites — Task 9 step 4 calls this out.
- After Task 10: main sources fully compile.
- After Task 11: full module unit-test compile passes; runtime test passes.
- After Task 12: full module green.
- Task 13: format + final build + push + PR.

**Open question:** Step 5 of Task 13 expects the user (or executing agent) to fill the PR template body. The plan instructs to read the template and warns to confirm with the user before posting. That's intentional rather than placeholder — the template's exact section list is auto-loaded from `.github/pull_request_template.md` and may evolve.

No issues found that require fixing inline.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-05-07-scheduled-task-result-api.md`. Two execution options:

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — I execute tasks in this session using executing-plans, batch execution with checkpoints.

Which approach?
