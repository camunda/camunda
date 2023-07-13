/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.scheduled;

import io.camunda.zeebe.engine.scheduled.ScheduledEngineTask.Context;
import io.camunda.zeebe.engine.scheduled.ScheduledEngineTask.Result;
import io.camunda.zeebe.engine.scheduled.ScheduledEngineTask.SchedulingDecision;
import io.camunda.zeebe.engine.scheduled.ScheduledEngineTask.SchedulingDecision.Delayed;
import io.camunda.zeebe.engine.state.immutable.ScheduledTaskState;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages {@link ScheduledEngineTask}s by initially scheduling them and pausing or resuming their
 * execution.
 */
public class ScheduledEngineTaskManager implements StreamProcessorLifecycleAware {

  private static final Logger LOG = LoggerFactory.getLogger(ScheduledEngineTaskManager.class);

  private final ProcessingScheduleService scheduleService;
  private final Supplier<ScheduledTaskState> stateSupplier;
  private final List<EngineTaskAdapter> tasks = new ArrayList<>();

  public ScheduledEngineTaskManager(
      final ProcessingScheduleService scheduleService,
      final Supplier<ScheduledTaskState> stateSupplier) {
    this.scheduleService = scheduleService;
    this.stateSupplier = stateSupplier;
  }

  /** Registers a {@link ScheduledEngineTask} that should be scheduled when recovery is finished. */
  public void register(final ScheduledEngineTask task) {
    tasks.add(new EngineTaskAdapter(task));
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    LOG.debug(
        "Recovered, scheduling {}",
        tasks.stream().map(Objects::toString).collect(Collectors.joining(", ")));
    tasks.forEach(task -> task.schedule(new Delayed(Duration.ZERO)));
  }

  @Override
  public void onClose() {
    LOG.debug("Closing, deactivating all tasks");
    tasks.forEach(EngineTaskAdapter::deactivate);
  }

  @Override
  public void onFailed() {
    LOG.debug("Failed, deactivating all tasks");
    tasks.forEach(EngineTaskAdapter::deactivate);
  }

  @Override
  public void onPaused() {
    LOG.debug("Paused, deactivating all tasks");

    tasks.forEach(EngineTaskAdapter::deactivate);
  }

  @Override
  public void onResumed() {
    LOG.debug("Resumed, activating all tasks");

    tasks.forEach(EngineTaskAdapter::activate);
  }

  /**
   * An adapter between the {@link Task} concept from the stream processor and the Engine. It
   * ensures that the task can be stopped and rescheduled on demand. For every task execution, a new
   * {@link Context} is built. Depending on the execution {@link Result}, the task is scheduled
   * again.
   */
  private final class EngineTaskAdapter implements Task {

    private final ScheduledEngineTask task;
    private final String taskName;
    private final AtomicBoolean isActive = new AtomicBoolean(true);

    public EngineTaskAdapter(final ScheduledEngineTask task) {
      this.task = task;
      taskName = task.getClass().getSimpleName();
    }

    void activate() {
      LOG.debug("Activating {}", taskName);
      if (isActive.compareAndSet(false, true)) {
        schedule(new SchedulingDecision.Delayed(Duration.ZERO));
      }
    }

    void deactivate() {
      LOG.debug("Deactivating {}", taskName);
      isActive.compareAndSet(true, false);
    }

    void schedule(final SchedulingDecision schedulingDecision) {
      if (schedulingDecision instanceof final Delayed r) {
        LOG.debug("Rescheduling {} in {}", this, r.delay());
        scheduleService.runDelayedAsync(r.delay(), this);
      }
    }

    @Override
    public TaskResult execute(final TaskResultBuilder taskResultBuilder) {
      if (!isActive.get()) {
        LOG.debug("Skipping previously scheduled execution of {}", this);
        return taskResultBuilder.build();
      }
      LOG.debug("Executing {}", this);

      final var clock = InstantSource.fixed(Instant.ofEpochMilli(ActorClock.currentTimeMillis()));
      final var context = new Context(clock, stateSupplier.get(), taskResultBuilder);
      final var result = task.execute(context);

      schedule(result.schedulingDecision());

      return result.taskResult();
    }

    @Override
    public String toString() {
      return "EngineTask{" + "task=" + taskName + ", isActive=" + isActive + '}';
    }
  }
}
