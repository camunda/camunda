/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util.sched;

public class BlockingPollSubscription implements ActorSubscription, Runnable {
  private final ActorJob subscriptionJob;
  private final Runnable blockingAction;
  private final ActorExecutor actorTaskExecutor;

  private volatile boolean isDone;
  private boolean isRecurring;

  public BlockingPollSubscription(
      ActorJob subscriptionJob,
      Runnable blockingAction,
      ActorExecutor actorTaskExecutor,
      boolean isRecurring) {
    this.subscriptionJob = subscriptionJob;
    this.blockingAction = blockingAction;
    this.actorTaskExecutor = actorTaskExecutor;
    this.isRecurring = isRecurring;
  }

  @Override
  public boolean poll() {
    return isDone;
  }

  @Override
  public ActorJob getJob() {
    return subscriptionJob;
  }

  @Override
  public boolean isRecurring() {
    return isRecurring;
  }

  @Override
  public void onJobCompleted() {
    if (isRecurring) {
      // re-submit
      submit();
    }
  }

  @Override
  public void run() {
    try {
      blockingAction.run();
    } catch (Exception e) {
      e.printStackTrace();
      // TODO: what now?
    } finally {
      onBlockingActionCompleted();
    }
  }

  private void onBlockingActionCompleted() {
    isDone = true;
    subscriptionJob.getTask().tryWakeup();
  }

  public void submit() {
    isDone = false;
    actorTaskExecutor.submitBlocking(this);
  }
}
