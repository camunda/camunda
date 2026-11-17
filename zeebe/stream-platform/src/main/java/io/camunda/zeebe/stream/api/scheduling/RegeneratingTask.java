/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.api.scheduling;

/**
 * A {@link Task} that can regenerate its result from the current state.
 *
 * <p>Normally, the scheduling service retries the write of a task result in place until the write
 * is accepted. That can keep the actor busy for as long as a rate limit stays exhausted. For a
 * {@code RegeneratingTask}, the scheduling service gives up on the first rate limited attempt and
 * abandons the result, instead of retrying it forever.
 *
 * <p>The contract of an abandoned result:
 *
 * <ul>
 *   <li>Nothing of the result was written.
 *   <li>The commands of the result were removed from the scheduled command cache.
 *   <li>The scheduling service does <b>not</b> run the task again. {@link #onResultAbandoned()} is
 *       called instead, and the task must make sure it runs again. It must do so without having
 *       more than one execution scheduled, for example by rescheduling itself through a method that
 *       replaces the execution it already scheduled.
 * </ul>
 *
 * <p>Implement this only if the task can regenerate what was abandoned. A task that cannot, such as
 * a task that runs once, must stay a plain {@link Task}, so that its result is retried until it is
 * accepted.
 *
 * <p>Scheduling at a fixed rate does not support abandoning. {@link
 * SimpleProcessingScheduleService#runAtFixedRate(java.time.Duration, Task)} and {@link
 * ProcessingScheduleService#runAtFixedRateAsync(java.time.Duration, Task)} wrap the task, so a
 * {@code RegeneratingTask} passed there is retried in place and {@link #onResultAbandoned()} is
 * never called.
 */
public interface RegeneratingTask extends Task {

  /**
   * Called when the result of {@link #execute(TaskResultBuilder)} was abandoned because writing it
   * stayed rate limited.
   *
   * <p>This is called on the actor of the scheduling service, after the abandoned commands were
   * removed from the scheduled command cache.
   */
  void onResultAbandoned();
}
