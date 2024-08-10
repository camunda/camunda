/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.api.scheduling;

import java.time.Duration;

public interface SimpleProcessingScheduleService {

  /**
   * Schedules the task to run after the given delay.
   *
   * @implNote Can be silently ignored if the scheduling service is not ready.
   * @return A representation of the scheduled task.
   */
  ScheduledTask runDelayed(Duration delay, Runnable task);

  /**
   * Schedules the task to run after the given delay.
   *
   * @implNote Can be silently ignored if the scheduling service is not ready.
   * @return A representation of the scheduled task.
   */
  ScheduledTask runDelayed(Duration delay, Task task);

  /**
   * Schedules the task to run at or after the given timestamp.
   *
   * @implNote Can be silently ignored if the scheduling service is not ready.
   * @return A representation of the scheduled task.
   */
  ScheduledTask runAt(long timestamp, Task task);

  /**
   * Schedules the task to run at or after the given timestamp.
   *
   * @implNote Can be silently ignored if the scheduling service is not ready.
   * @return A representation of the scheduled task.
   */
  ScheduledTask runAt(long timestamp, Runnable task);

  /**
   * Schedule a task to execute at a fixed rate. After an initial delay, the task is executed. Once
   * the task is executed, it is rescheduled with the same delay again.
   *
   * <p>Note that time-traveling in tests only affects the delay of the currently scheduled next
   * task and not any of the iterations after. This is because the next task is scheduled with the
   * delay counted from the new time (i.e. the time after time traveling + task execution duration +
   * delay duration = scheduled time of the next task).
   *
   * @param delay The delay to wait initially and between each run
   * @param task The task to execute at the fixed rate
   */
  default void runAtFixedRate(final Duration delay, final Runnable task) {
    runDelayed(
        delay,
        () -> {
          try {
            task.run();
          } finally {
            runAtFixedRate(delay, task);
          }
        });
  }

  void runAtFixedRate(final Duration delay, final Task task);

  /***
   * A task scheduled by {@link SimpleProcessingScheduleService} to give the caller control over the
   * task, i.e. for cancellation.
   */
  @FunctionalInterface
  interface ScheduledTask {

    /**
     * Cancels the scheduled execution of this task.
     *
     * @implNote can be a noop if the task already ran or if the task scheduling was silently
     *     ignored.
     */
    void cancel();
  }
}
