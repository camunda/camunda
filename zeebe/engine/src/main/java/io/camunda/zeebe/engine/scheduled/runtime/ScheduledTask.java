/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.scheduled.runtime;

/**
 * A unit of work the {@link ScheduledTaskRuntime} can invoke on a schedule.
 *
 * <p>Tasks are pure work units: they receive a {@link Context} scoped to one invocation and return
 * a {@link Result}. They do not schedule themselves, are not lifecycle-aware, and do not see the
 * stream processor context.
 */
@FunctionalInterface
public interface ScheduledTask {
  Result run(Context context);
}
