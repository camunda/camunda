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

  private final Map<String, RegisteredTask> tasks = new LinkedHashMap<>();
  private ProcessingScheduleService scheduleService;
  private InstantSource clock;
  private boolean recovered;
  private boolean schedulingEnabled = true;

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
    // implemented in Task 12
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext processingContext) {
    scheduleService = processingContext.getScheduleService();
    clock = processingContext.getClock();
    recovered = true;
    for (final var task : tasks.values()) {
      armNextRun(task);
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
      armNextRun(task);
    }
  }

  private void cancelAll() {
    for (final var task : tasks.values()) {
      if (task.currentScheduled != null) {
        task.currentScheduled.cancel();
        task.currentScheduled = null;
      }
    }
  }

  private void armNextRun(final RegisteredTask task) {
    if (!recovered || !schedulingEnabled || task.paused) {
      return;
    }
    final long candidate = chooseNextRunAt(task);
    if (candidate == Long.MAX_VALUE) {
      return;
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
    return Math.max(candidate, floor);
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
