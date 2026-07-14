/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.api.scheduling;

import java.time.Duration;
import org.jspecify.annotations.Nullable;

/** Here the interface is just a suggestion. Can be whatever PDT team thinks is best to work with */
public interface Task {

  TaskResult execute(TaskResultBuilder taskResultBuilder);

  /**
   * How stale the read views this task scans may be, or null (the default) when the task must
   * observe every batch committed before it runs. Only consulted by the experimental layered-state
   * wiring, which prepares view freshness before each asynchronous task execution:
   *
   * <ul>
   *   <li><b>Event-driven tasks</b> (null) derive their next wake-up from the scan — a missed
   *       committed entry is a lost wake-up (e.g. the timer due-date and message-TTL checkers), so
   *       the buffered state is frozen into a fresh view before every execution.
   *   <li><b>Polling tasks</b> (a positive duration, naturally the task's own polling period)
   *       rescan their full range every period and tolerate staleness up to that period: an entry
   *       missed by one poll is picked up by the next. For them the current published view is
   *       reused while it is younger than the returned tolerance, skipping the pre-execution freeze
   *       — every avoided freeze keeps overwrites deduplicating in place in the active overlay
   *       instead of creating cross-segment versions for the pipeline merges.
   * </ul>
   */
  default @Nullable Duration toleratedViewStaleness() {
    return null;
  }
}
