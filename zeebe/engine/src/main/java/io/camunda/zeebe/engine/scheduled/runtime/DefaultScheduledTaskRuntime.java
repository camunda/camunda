/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.scheduled.runtime;

import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import java.time.InstantSource;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DefaultScheduledTaskRuntime implements ScheduledTaskRuntime {

  private final BackPressureSignal backPressureSignal;
  private final Map<String, RegisteredTask> tasks = new LinkedHashMap<>();
  private ProcessingScheduleService scheduleService;
  private InstantSource clock;
  private boolean recovered;
  private boolean schedulingEnabled = true;

  public DefaultScheduledTaskRuntime(final BackPressureSignal backPressureSignal) {
    this.backPressureSignal = backPressureSignal;
  }

  @Override
  public Handle register(
      final String name,
      final Schedule schedule,
      final ScheduledTask task,
      final TaskOptions options) {
    if (tasks.containsKey(name)) {
      throw new IllegalArgumentException("Task already registered: " + name);
    }
    tasks.put(name, new RegisteredTask(name, schedule, task, options));
    return runAtOrBefore -> nudge(name, runAtOrBefore);
  }

  @Override
  public void nudge(final String name, final long runAtOrBefore) {
    final var task = tasks.get(name);
    if (task == null) {
      return;
    }
    if (runAtOrBefore >= task.latestNudgeAtOrBefore) {
      return;
    }
    task.latestNudgeAtOrBefore = runAtOrBefore;
    if (task.currentScheduled != null) {
      task.currentScheduled.cancel();
      task.currentScheduled = null;
    }
    armNextRun(task);
  }

  @Override
  public void pause(final String name) {
    final var task = tasks.get(name);
    if (task == null || task.paused) {
      return;
    }
    task.paused = true;
    if (task.currentScheduled != null) {
      task.currentScheduled.cancel();
      task.currentScheduled = null;
    }
  }

  @Override
  public void resume(final String name) {
    final var task = tasks.get(name);
    if (task == null || !task.paused) {
      return;
    }
    task.paused = false;
    armNextRun(task);
  }

  @Override
  public void throttle(final String name, final ThrottlePolicy policy) {
    final var task = tasks.get(name);
    if (task == null) {
      return;
    }
    task.throttle = policy;
    if (task.currentScheduled != null) {
      task.currentScheduled.cancel();
      task.currentScheduled = null;
      armNextRun(task);
    }
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext processingContext) {
    scheduleService = processingContext.getScheduleService();
    clock = processingContext.getClock();
    recovered = true;
    for (final var task : tasks.values()) {
      armWithRecoverySweep(task);
    }
  }

  @Override
  public void onPaused() {
    schedulingEnabled = false;
    cancelAll();
  }

  @Override
  public void onClose() {
    schedulingEnabled = false;
    cancelAll();
  }

  @Override
  public void onFailed() {
    schedulingEnabled = false;
    cancelAll();
  }

  @Override
  public void onResumed() {
    schedulingEnabled = true;
    for (final var task : tasks.values()) {
      armWithRecoverySweep(task);
    }
  }

  /**
   * Arms a task on recovery or resume. An on-demand task backed by durable state must reconcile
   * once here: work may have come due while the runtime was down or paused, and nothing else will
   * nudge it until new work arrives. Pulling its next run forward to now (raised to the resolution
   * floor by {@link #armNextRun}) mirrors the mandatory sweep the former {@code
   * DueDateCheckScheduler} performed via {@code schedule(-1)}. An earlier retained nudge is
   * preserved. Periodic tasks already schedule their first run on recovery, so they need no sweep.
   */
  private void armWithRecoverySweep(final RegisteredTask task) {
    if (task.schedule instanceof Schedule.OnDemand) {
      task.latestNudgeAtOrBefore = Math.min(task.latestNudgeAtOrBefore, clock.millis());
    }
    armNextRun(task);
  }

  private void cancelAll() {
    for (final var task : tasks.values()) {
      if (task.currentScheduled != null) {
        task.currentScheduled.cancel();
        task.currentScheduled = null;
      }
    }
  }

  public void onTaskResultWritten(final String name) {
    final var t = tasks.get(name);
    if (t == null) {
      return;
    }
    t.resultWritten = true;
    if (t.resultProcessed) {
      armNextRun(t);
    }
  }

  public void onTaskResultProcessed(final String name) {
    final var t = tasks.get(name);
    if (t == null) {
      return;
    }
    t.resultProcessed = true;
    if (t.resultWritten) {
      armNextRun(t);
    }
  }

  private void armNextRun(final RegisteredTask task) {
    if (!recovered || !schedulingEnabled || task.paused) {
      return;
    }
    if (!task.resultWritten || !task.resultProcessed) {
      return;
    }
    final long candidate = chooseNextRunAt(task);
    if (candidate == Long.MAX_VALUE) {
      return;
    }
    if (task.currentScheduled != null) {
      task.currentScheduled.cancel();
      task.currentScheduled = null;
    }
    final Task taskAdapter = builder -> runTask(task, builder);
    task.currentScheduled =
        task.options.runAsync()
            ? scheduleService.runAtAsync(candidate, taskAdapter, task.options.taskGroup())
            : scheduleService.runAt(candidate, taskAdapter);
  }

  private long chooseNextRunAt(final RegisteredTask task) {
    final long now = clock.millis();
    long candidate = scheduleCandidate(task, now);
    if (task.latestHint instanceof Hint.NextDueAt next) {
      candidate = Math.min(candidate, next.timestamp());
    }
    if (task.latestHint instanceof Hint.MoreWorkPending) {
      candidate = Math.min(candidate, now);
    }
    candidate = Math.min(candidate, task.latestNudgeAtOrBefore);
    final long floor = now + minResolution(task);
    candidate = Math.max(candidate, floor);
    if (task.throttle instanceof ThrottlePolicy.MinInterval mi && task.lastRunAt >= 0) {
      candidate = Math.max(candidate, task.lastRunAt + mi.minInterval().toMillis());
    }
    return candidate;
  }

  private long scheduleCandidate(final RegisteredTask task, final long now) {
    return switch (task.schedule) {
      case Schedule.Periodic p ->
          task.lastRunAt < 0
              ? now + p.interval().toMillis()
              : task.lastRunAt + p.interval().toMillis();
      case Schedule.OnDemand ignored -> Long.MAX_VALUE;
    };
  }

  private long minResolution(final RegisteredTask task) {
    return switch (task.schedule) {
      case Schedule.Periodic p -> p.interval().toMillis();
      case Schedule.OnDemand o -> o.minDelay().toMillis();
    };
  }

  private TaskResult runTask(final RegisteredTask task, final TaskResultBuilder builder) {
    task.currentScheduled = null;
    task.lastRunAt = clock.millis();
    // The write+processed gate is dormant until production callbacks are wired
    // (see BackPressureSignal Javadoc). Until then resultWritten/resultProcessed
    // stay true so the gate is a no-op for production behavior.
    final var ctx =
        new Context() {
          @Override
          public InstantSource clock() {
            return clock;
          }

          @Override
          public TaskResultBuilder resultBuilder() {
            return builder;
          }
        };
    final Result result = task.task.run(ctx);
    task.latestHint = result.hint();
    task.latestNudgeAtOrBefore = Long.MAX_VALUE;
    armNextRun(task);
    return result.taskResult();
  }
}
