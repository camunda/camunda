/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.api.scheduling;

import java.time.Duration;

public interface ProcessingScheduleService extends SimpleProcessingScheduleService {

  /**
   * Schedule a task to execute at a fixed rate. After an initial delay, the task is executed. Once
   * the task is executed, it is rescheduled with the same delay again.
   *
   * <p>The execution of the scheduled task is running asynchronously/concurrently to task scheduled
   * through non-async methods. While other, non-async methods guarantee the execution order of
   * scheduled tasks and always execute scheduled tasks on the same thread, this method does not
   * guarantee this.
   *
   * <p>Note that time-traveling in tests only affects the delay of the currently scheduled next
   * task and not any of the iterations after. This is because the next task is scheduled with the
   * delay counted from the new time (i.e. the time after time traveling + task execution duration +
   * delay duration = scheduled time of the next task).
   *
   * @param delay The delay to wait initially and between each run
   * @param task The task to execute at the fixed rate
   */
  void runAtFixedRateAsync(final Duration delay, final Task task, final Pool pool);

  /**
   * Schedule a task to execute with a specific delay. After that delay, the task is executed.
   *
   * <p>The execution of the scheduled task is running asynchronously/concurrently to task scheduled
   * through non-async methods. While other, non-async methods guarantee the execution order of
   * scheduled tasks and always execute scheduled tasks on the same thread, this method does not
   * guarantee this.
   *
   * @param delay The delay to wait before executing the task
   * @param task The task to execute after the delay
   * @implNote If the delay is short, cancellation via {@link ScheduledTask} may happen after
   *     execution and have no effect.
   */
  ScheduledTask runDelayedAsync(final Duration delay, final Task task, final Pool pool);

  /**
   * Schedule a task to execute at or after a specific timestamp. The task is executed after the
   * timestamp is passed. No guarantee is provided that the task is run exactly at the timestamp,
   * but we guarantee that it is not executed before.
   *
   * <p>The execution of the scheduled task is running asynchronously/concurrently to task scheduled
   * through non-async methods. While other, non-async methods guarantee the execution order of
   * scheduled tasks and always execute scheduled tasks on the same thread, this method does not
   * guarantee this.
   *
   * @param timestamp Unix epoch timestamp in milliseconds
   * @param task The task to execute at or after the timestamp
   * @implNote If the delay is short, cancellation via {@link ScheduledTask} may happen after
   *     execution and have no effect.
   */
  ScheduledTask runAtAsync(final long timestamp, final Task task, final Pool pool);

  enum Pool {
    REALTIME,
    BACKGROUND
  }
}
