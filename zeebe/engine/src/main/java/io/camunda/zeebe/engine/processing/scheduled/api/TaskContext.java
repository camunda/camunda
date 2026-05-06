/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.scheduled.api;

import java.time.InstantSource;

/** Per-execution context handed to a {@link ScheduledTask#run(TaskContext)}. */
public interface TaskContext {

  /** Clock to be used for any "now" comparison. Honors test time-travel. */
  InstantSource clock();

  /** Output channel for follow-up commands and inter-partition sends. */
  Sink sink();

  /**
   * Returns {@code true} once the cooperative time budget for this run has been spent. Tasks that
   * iterate over many entries should poll this between items and return {@link Outcome.YieldNow} to
   * give the actor thread back to other work. The runtime will reschedule immediately.
   *
   * <p>If the schedule does not configure a yield budget, this always returns {@code false}.
   */
  boolean shouldYield();

  /** Partition id this task is running on. Useful for partition-scoped behavior. */
  int partitionId();
}
