/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util.sched;

/**
 * Maintains multiple levels of queues for each thread. Levels can be used for priorities (each
 * thread maintains a queue for each priority) or other things like IO-devices.
 */
public final class MultiLevelWorkstealingGroup {
  private final WorkStealingGroup[] workStealingGroups;

  public MultiLevelWorkstealingGroup(final int numOfThreads, final int levels) {
    workStealingGroups = new WorkStealingGroup[levels];
    for (int i = 0; i < levels; i++) {
      workStealingGroups[i] = new WorkStealingGroup(numOfThreads);
    }
  }

  public ActorTask getNextTask(final int level) {
    return workStealingGroups[level].getNextTask();
  }

  public void submit(final ActorTask task, final int level, final int threadId) {
    workStealingGroups[level].submit(task, threadId);
  }
}
