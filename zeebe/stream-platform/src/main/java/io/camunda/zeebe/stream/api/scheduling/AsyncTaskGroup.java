/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.api.scheduling;

import io.camunda.zeebe.scheduler.SchedulingHints;

/**
 * Enum to define the different task groups for the {@link
 * io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService}. The task groups are used to
 * specify the actor in which the tasks are executed. *
 *
 * <p>Adding an extra value to the enum guarantees creating a new actor for this group.
 *
 * <p>Each task group has a name and a {@link SchedulingHints} to define the scheduling hints for
 * the group.
 *
 * <p>For example, the {@link #ASYNC_PROCESSING} task group is used for CPU-bound tasks.
 *
 * @see SchedulingHints
 */
public enum AsyncTaskGroup {
  ASYNC_PROCESSING("AsyncProcessingScheduleActor", SchedulingHints.cpuBound());
  private final String name;
  private final SchedulingHints schedulingHints;

  AsyncTaskGroup(final String name, final SchedulingHints schedulingHints) {
    this.name = name;
    this.schedulingHints = schedulingHints;
  }

  public String getName() {
    return name;
  }

  public SchedulingHints getSchedulingHints() {
    return schedulingHints;
  }
}
