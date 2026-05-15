/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.timer;

import io.camunda.zeebe.engine.scheduled.runtime.ScheduledTaskRuntime;

/**
 * Thin adapter that lets {@code CatchEventBehavior} and other callers nudge the timer expiry task
 * without holding a reference to the {@link ScheduledTaskRuntime} or knowing the task name.
 */
public final class DueDateTimerCheckScheduler {

  private final ScheduledTaskRuntime.Handle handle;

  public DueDateTimerCheckScheduler(final ScheduledTaskRuntime.Handle handle) {
    this.handle = handle;
  }

  public void scheduleTimer(final long dueDate) {
    handle.nudge(dueDate);
  }
}
