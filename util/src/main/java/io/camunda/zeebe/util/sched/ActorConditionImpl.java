/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util.sched;

import java.util.concurrent.atomic.AtomicLong;

@SuppressWarnings("restriction")
public final class ActorConditionImpl implements ActorCondition, ActorSubscription {

  private final ActorJob job;
  private final String conditionName;
  private final ActorTask task;
  private final AtomicLong triggerCount;
  private long runCount = 0;

  public ActorConditionImpl(final String conditionName, final ActorJob job) {
    this.conditionName = conditionName;
    this.job = job;
    task = job.getTask();
    triggerCount = new AtomicLong(0);
  }

  @Override
  public void signal() {
    triggerCount.getAndIncrement();
    task.tryWakeup();
  }

  @Override
  public void cancel() {
    task.onSubscriptionCancelled(this);
  }

  @Override
  public boolean poll() {
    return triggerCount.get() > runCount;
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
