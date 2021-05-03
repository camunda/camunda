/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util.sched;

import io.zeebe.util.sched.ActorScheduler.ActorSchedulerBuilder;

/** Thread group for the non-blocking, CPU bound, tasks. */
public final class CpuThreadGroup extends ActorThreadGroup {

  public CpuThreadGroup(final ActorSchedulerBuilder builder) {
    super(
        String.format("%s-%s", builder.getSchedulerName(), "zb-actors"),
        builder.getCpuBoundActorThreadCount(),
        builder.getPriorityQuotas().length,
        builder);
  }

  @Override
  protected TaskScheduler createTaskScheduler(
      final MultiLevelWorkstealingGroup tasks, final ActorSchedulerBuilder builder) {
    return new PriorityScheduler(tasks::getNextTask, builder.getPriorityQuotas());
  }

  @Override
  protected int getLevel(final ActorTask actorTask) {
    return actorTask.getPriority();
  }
}
