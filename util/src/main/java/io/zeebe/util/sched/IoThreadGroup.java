/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util.sched;

import io.zeebe.util.sched.ActorScheduler.ActorSchedulerBuilder;

public class IoThreadGroup extends ActorThreadGroup {

  public IoThreadGroup(ActorSchedulerBuilder builder) {
    super(
        String.format("%s-%s", builder.getSchedulerName(), "zb-fs-workers"),
        builder.getIoBoundActorThreadCount(),
        1,
        builder);
  }

  @Override
  protected TaskScheduler createTaskScheduler(
      MultiLevelWorkstealingGroup tasks, ActorSchedulerBuilder builder) {
    return new IoScheduler(tasks);
  }

  @Override
  protected int getLevel(ActorTask actorTask) {
    return 0;
  }
}
