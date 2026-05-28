/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.scheduled.runtime;

import io.camunda.zeebe.stream.api.scheduling.SimpleProcessingScheduleService.ScheduledTask;

/** Package-private bookkeeping for one registered {@link ScheduledTask}. */
final class RegisteredTask {
  final String name;
  final Schedule schedule;
  final io.camunda.zeebe.engine.scheduled.runtime.ScheduledTask task;
  final TaskOptions options;

  Hint latestHint = new Hint.Idle();
  long latestNudgeAtOrBefore = Long.MAX_VALUE;
  long lastRunAt = -1;
  boolean paused;
  ThrottlePolicy throttle = ThrottlePolicy.none();
  ScheduledTask currentScheduled;

  RegisteredTask(
      final String name,
      final Schedule schedule,
      final io.camunda.zeebe.engine.scheduled.runtime.ScheduledTask task,
      final TaskOptions options) {
    this.name = name;
    this.schedule = schedule;
    this.task = task;
    this.options = options;
  }
}
