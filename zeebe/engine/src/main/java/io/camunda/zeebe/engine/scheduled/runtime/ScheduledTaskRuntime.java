/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.scheduled.runtime;

import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;

/**
 * Owns the scheduling decisions for every registered {@link ScheduledTask}. A single instance is
 * registered as the only {@link StreamProcessorLifecycleAware} related to scheduled work;
 * individual tasks are not lifecycle-aware.
 */
public interface ScheduledTaskRuntime extends StreamProcessorLifecycleAware {

  /**
   * Registers a task under a unique {@code name}. Must be called before {@link
   * StreamProcessorLifecycleAware#onRecovered}. Throws if {@code name} is already registered.
   */
  Handle register(String name, Schedule schedule, ScheduledTask task, TaskOptions options);

  /**
   * Signals that the named task may have work to do at or before {@code runAtOrBefore} (epoch ms).
   * Pulls the next scheduled run forward to {@code min(currentSchedule, runAtOrBefore)}, subject to
   * the resolution floor and throttle policy. A later timestamp than the currently scheduled run is
   * ignored. Safe to call concurrently. No-op for unknown names.
   */
  void nudge(String name, long runAtOrBefore);

  /**
   * Cancels any scheduled execution of the named task and prevents new ones until {@link
   * #resume(String)}. Retains latest hints and nudges. No-op for unknown names or already-paused
   * tasks.
   */
  void pause(String name);

  /**
   * Re-enables scheduling for a paused task and recomputes its next run from retained state. No-op
   * for unknown names or tasks that are not paused.
   */
  void resume(String name);

  /** Applies a {@link ThrottlePolicy} for the named task. No-op for unknown names. */
  void throttle(String name, ThrottlePolicy policy);

  /** Per-task handle returned from {@link #register}. */
  interface Handle {
    /** Equivalent to {@code runtime.nudge(name, runAtOrBefore)} for this handle's task. */
    void nudge(long runAtOrBefore);
  }
}
