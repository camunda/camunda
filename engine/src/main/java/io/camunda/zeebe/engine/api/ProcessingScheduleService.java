/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.api;

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
  void runAtFixedRateAsync(final Duration delay, final Task task);

  /**
   * Schedule a task to execute with a specific delay. After that delay, the task is executed.
   *
   * <p>The execution of the scheduled task is running asynchronously/concurrent to other scheduled
   * tasks. Other methods will guarantee ordering of scheduled tasks and running always in same
   * thread, these guarantees don't apply to this method.
   *
   * @param delay The delay to wait before executing the task
   * @param task The task to execute after the delay
   */
  void runDelayedAsync(final Duration delay, final Task task);
}
