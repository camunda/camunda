/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.scheduled.runtime;

import io.camunda.zeebe.engine.processing.scheduled.api.Outcome;
import io.camunda.zeebe.engine.processing.scheduled.api.Schedule;
import io.camunda.zeebe.engine.processing.scheduled.api.ScheduledTask;
import io.camunda.zeebe.engine.processing.scheduled.api.Sink;
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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adapts a {@link ScheduledTask} (the "what") to the stream processor's lifecycle and scheduling
 * service (the "how"). One instance manages a single task and is registered as a lifecycle listener
 * via {@code typedRecordProcessors.withListener(managed)}.
 *
 * <p>Provides the cross-cutting concerns once for every task that uses the new API:
 *
 * <ul>
 *   <li>full lifecycle: {@code onRecovered / onPaused / onResumed / onClose / onFailed}
 *   <li>three legacy patterns expressed as one config: fixed-rate, on-demand, self-rescheduling
 *   <li>cooperative yielding via a per-run time budget exposed on {@link TaskContext}
 *   <li>error handling: exceptions are caught, logged, counted; the task is always rescheduled
 *   <li>logging on lifecycle transitions and slow runs
 *   <li>per-task metrics tagged with {@code task=<name>}
 *   <li>external wake-up via {@link #requestRun(long)}
 * </ul>
 */
public final class ManagedScheduledTask implements StreamProcessorLifecycleAware, Task {

  private static final Logger LOG = LoggerFactory.getLogger(ManagedScheduledTask.class);
  private static final long SLOW_RUN_THRESHOLD_MS = 100;

  private final ScheduledTask task;
  private final Schedule schedule;
  private final InterPartitionCommandSender interPartitionCommandSender;
  private final ScheduledTaskMetrics metrics;

  private InstantSource clock;
  private ProcessingScheduleService scheduleService;
  private int partitionId;

  private volatile boolean enabled;
  private final AtomicReference<NextRun> nextRun = new AtomicReference<>(NextRun.NONE);

  public ManagedScheduledTask(
      final ScheduledTask task,
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

  /**
   * Requests an execution at-or-before {@code timestampMs}. Used by code paths that know they have
   * just produced new work the task should observe (e.g. a newly-deployed timer that becomes due
   * sooner than the currently-scheduled run). No-op if the task is not enabled or if a run is
   * already scheduled at or before the requested timestamp (within {@code minResolution}).
   */
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
    // The pending NextRun is consumed; clear so external requestRun() can schedule fresh.
    nextRun.set(NextRun.NONE);

    final long startMs = clock.millis();
    final long yieldAfterMs =
        schedule.yieldBudget() == null
            ? Long.MAX_VALUE
            : startMs + schedule.yieldBudget().toMillis();

    final RunContext ctx = new RunContext(taskResultBuilder, yieldAfterMs);

    Outcome outcome;
    try {
      outcome = task.run(ctx);
    } catch (final RuntimeException e) {
      metrics.recordError();
      LOG.warn(
          "Scheduled task '{}' threw {}; will reschedule per fallback interval",
          task.name(),
          e.toString(),
          e);
      outcome = Outcome.IDLE;
    }

    final long elapsed = clock.millis() - startMs;
    metrics.recordRun(Duration.ofMillis(elapsed));
    if (elapsed >= SLOW_RUN_THRESHOLD_MS) {
      LOG.info("Scheduled task '{}' run took {}ms", task.name(), elapsed);
    }

    if (outcome instanceof Outcome.YieldNow) {
      metrics.recordYield();
    }

    scheduleNext(outcome);

    return taskResultBuilder.build();
  }

  // ---------------------------------------------------------------------------
  // Scheduling helpers
  // ---------------------------------------------------------------------------

  private void scheduleInitial() {
    // Always run once promptly after recovery/resume so the task can observe entries that became
    // due while we were paused (or that were already in state at startup). For pure on-demand
    // schedules the task itself decides via Outcome.AwaitDueAt / Outcome.IDLE whether to keep
    // firing afterwards.
    rescheduleIfEarlier(0L);
  }

  private void scheduleNext(final Outcome outcome) {
    if (!enabled) {
      return;
    }
    switch (outcome) {
      case Outcome.YieldNow ignored -> rescheduleIfEarlier(0L);
      case Outcome.AwaitDueAt due -> rescheduleIfEarlier(due.timestampMs());
      case Outcome.Idle ignored -> {
        if (schedule.fallbackInterval() != null) {
          rescheduleIfEarlier(clock.millis() + schedule.fallbackInterval().toMillis());
        }
      }
    }
  }

  /**
   * Schedules a run at-or-before {@code requestedAtMs}, but no earlier than {@code now +
   * minResolution}. If a run is already scheduled at-or-before the requested time, this is a no-op.
   * Otherwise the previously-scheduled run is cancelled and replaced.
   */
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
    final NextRun cleared = nextRun.getAndSet(NextRun.NONE);
    cleared.cancel();
  }

  // ---------------------------------------------------------------------------
  // Inner types
  // ---------------------------------------------------------------------------

  /** State of the next pending execution; used for atomic replacement and cancellation. */
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

  /** TaskContext + Sink wrapper for a single run. Counts items emitted for metrics. */
  private final class RunContext implements TaskContext, Sink {

    private final TaskResultBuilder builder;
    private final long yieldAfterMs;

    RunContext(final TaskResultBuilder builder, final long yieldAfterMs) {
      this.builder = builder;
      this.yieldAfterMs = yieldAfterMs;
    }

    @Override
    public InstantSource clock() {
      return clock;
    }

    @Override
    public Sink sink() {
      return this;
    }

    @Override
    public boolean shouldYield() {
      return clock.millis() >= yieldAfterMs;
    }

    @Override
    public int partitionId() {
      return partitionId;
    }

    @Override
    public boolean append(final Intent intent, final UnifiedRecordValue value) {
      final boolean fit = builder.appendCommandRecord(intent, value);
      if (fit) {
        metrics.recordAppend();
      }
      return fit;
    }

    @Override
    public boolean append(final long key, final Intent intent, final UnifiedRecordValue value) {
      final boolean fit = builder.appendCommandRecord(key, intent, value);
      if (fit) {
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
      metrics.recordInterPartitionSend();
    }
  }
}
