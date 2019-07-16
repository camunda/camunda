/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util.sched;

import io.zeebe.util.sched.clock.ActorClock;

/**
 * TaskScheduler implementation of IoActors. Currently, this implementation does nothing special. In
 * the future, more sophisticated logic can be implemented such as limiting concurrency by Io Device
 * or similar schemes.
 */
public class IoScheduler implements TaskScheduler {

  private MultiLevelWorkstealingGroup tasks;

  public IoScheduler(MultiLevelWorkstealingGroup tasks) {
    this.tasks = tasks;
  }

  @Override
  public ActorTask getNextTask(ActorClock now) {
    return tasks.getNextTask(0);
  }
}
