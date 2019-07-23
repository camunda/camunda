/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util.sched;

import static org.agrona.UnsafeAccess.UNSAFE;

@SuppressWarnings("restriction")
public class ActorConditionImpl implements ActorCondition, ActorSubscription {
  private static final long TRIGGER_COUNT_OFFSET;

  static {
    try {
      TRIGGER_COUNT_OFFSET =
          UNSAFE.objectFieldOffset(ActorConditionImpl.class.getDeclaredField("triggerCount"));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private final ActorJob job;
  private final String conditionName;
  private final ActorTask task;
  private volatile long triggerCount = 0;
  private long runCount = 0;

  public ActorConditionImpl(String conditionName, ActorJob job) {
    this.conditionName = conditionName;
    this.job = job;
    this.task = job.getTask();
  }

  @Override
  public void signal() {
    UNSAFE.getAndAddInt(this, TRIGGER_COUNT_OFFSET, 1);
    task.tryWakeup();
  }

  @Override
  public void cancel() {
    task.onSubscriptionCancelled(this);
  }

  @Override
  public boolean poll() {
    return triggerCount > runCount;
  }

  @Override
  public ActorJob getJob() {
    return job;
  }

  @Override
  public boolean isRecurring() {
    return true;
  }

  @Override
  public void onJobCompleted() {
    runCount++;
  }

  @Override
  public String toString() {
    return "Condition " + conditionName;
  }
}
