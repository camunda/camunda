/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.scheduled.runtime;

import io.camunda.zeebe.stream.api.scheduling.AsyncTaskGroup;

/**
 * Per-task scheduling options.
 *
 * @param runAsync When true, the runtime schedules the task via {@code
 *     ProcessingScheduleService.runAtAsync}; otherwise via {@code runAt}.
 * @param taskGroup The {@link AsyncTaskGroup} to use when {@code runAsync} is true. Ignored
 *     otherwise.
 */
public record TaskOptions(boolean runAsync, AsyncTaskGroup taskGroup) {

  public static TaskOptions sync() {
    return new TaskOptions(false, AsyncTaskGroup.ASYNC_PROCESSING);
  }

  public static TaskOptions async(final AsyncTaskGroup taskGroup) {
    return new TaskOptions(true, taskGroup);
  }
}
