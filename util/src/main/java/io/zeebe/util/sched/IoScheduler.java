/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util.sched;

import io.zeebe.util.sched.clock.ActorClock;

/**
 * TaskScheduler implementation of IoActors. Currently, this implementation does nothing special. In
 * the future, more sophisticated logic can be implemented such as limiting concurrency by Io Device
 * or similar schemes.
 */
public final class IoScheduler implements TaskScheduler {

  private final MultiLevelWorkstealingGroup tasks;

  public IoScheduler(final MultiLevelWorkstealingGroup tasks) {
    this.tasks = tasks;
  }

  @Override
  public ActorTask getNextTask(final ActorClock now) {
    return tasks.getNextTask(0);
  }
}
