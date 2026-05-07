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
 * <p>Provides the cross-cutting concerns once for every task that uses the new API: full lifecycle,
 * scheduling cadence translation, cooperative yielding, error handling, slow-run logging, per-task
 * metrics, external wake-up via {@link #requestRun(long)}, and resume-cursor storage between
 * yields.
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
      decision = Decision.Idle.INSTANCE;
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
    resumeCursor = decision instanceof Decision.YieldNow yield ? (C) yield.cursor() : null;
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

    RunContext(final long yieldAfterMs, final X resumeCursor, final Result.Builder<X> builder) {
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
   * inter-partition sends to {@link InterPartitionCommandSender}. The terminal methods produce the
   * immutable {@link Result} with the accumulated state.
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
      return new Result(appendedCommands, interPartitionSends, Decision.Idle.INSTANCE);
    }

    @Override
    public Result awaitDueAt(final long timestampMs) {
      return new Result(
          appendedCommands, interPartitionSends, new Decision.AwaitDueAt(timestampMs));
    }

    @Override
    public Result yieldNow(final X cursor) {
      return new Result(appendedCommands, interPartitionSends, new Decision.YieldNow(cursor));
    }
  }
}
