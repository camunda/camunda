/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.scheduled.runtime;

import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DefaultScheduledTaskRuntime implements ScheduledTaskRuntime {

  private final Map<String, RegisteredTask> tasks = new LinkedHashMap<>();

  @Override
  public Handle register(
      final String name,
      final Schedule schedule,
      final ScheduledTask task,
      final TaskOptions options) {
    if (tasks.containsKey(name)) {
      throw new IllegalArgumentException("Task already registered: " + name);
    }
    tasks.put(name, new RegisteredTask(name, schedule, task, options));
    return runAtOrBefore -> nudge(name, runAtOrBefore);
  }

  @Override
  public void nudge(final String name, final long runAtOrBefore) {
    // implemented in Task 9
  }

  @Override
  public void pause(final String name) {
    // implemented in Task 11
  }

  @Override
  public void resume(final String name) {
    // implemented in Task 11
  }

  @Override
  public void throttle(final String name, final ThrottlePolicy policy) {
    // implemented in Task 12
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext processingContext) {
    // implemented in Task 5
  }
}
