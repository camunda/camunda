/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.scheduled.api;

import java.time.InstantSource;

/**
 * Per-execution context handed to a {@link ScheduledTask#run(TaskContext)}.
 *
 * @param <C> the task's resume-cursor type
 */
public interface TaskContext<C> {

  /** Clock to be used for any "now" comparison. Honors test time-travel. */
  InstantSource clock();

  /** Partition id this task is running on. Useful for partition-scoped behavior. */
  int partitionId();

  /**
   * Returns {@code true} once the cooperative time budget for this run has been spent. Tasks that
   * iterate over many entries should poll this between items and return {@link
   * Result.Builder#yieldNow} to give the actor thread back to other work. The runtime will
   * reschedule immediately.
   *
   * <p>If the schedule does not configure a yield budget, this always returns {@code false}.
   */
  boolean shouldYield();

  /**
   * The cursor saved on the previous run via {@link Result.Builder#yieldNow(Object)}, or {@code
   * null} when no cursor is stored (first run, or the previous run terminated with {@code idle()} /
   * {@code awaitDueAt(...)}). The engine convention is to use {@code null} for "missing"; {@link
   * java.util.Optional} is reserved for lambda-shaped APIs.
   */
  C resumeCursor();

  /**
   * Fresh builder for this run. Tasks accumulate commands and sends on it, then return the {@link
   * Result} produced by one of its terminal methods. The runtime reads the builder's accumulated
   * state when the task returns.
   */
  Result.Builder<C> result();
}
