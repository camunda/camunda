/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler.testing;

import io.camunda.zeebe.scheduler.ActorMetrics;
import io.camunda.zeebe.scheduler.ActorThread;
import io.camunda.zeebe.scheduler.ActorThreadGroup;
import io.camunda.zeebe.scheduler.ActorTimerQueue;
import io.camunda.zeebe.scheduler.TaskScheduler;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Phaser;
import org.agrona.concurrent.IdleStrategy;

public final class ControlledActorThread extends ActorThread {
  private final Phaser phaser = new Phaser(2);

  public ControlledActorThread(
      final String name,
      final int id,
      final ActorThreadGroup threadGroup,
      final TaskScheduler taskScheduler,
      final ActorClock clock,
      final ActorTimerQueue timerQueue,
      final IdleStrategy idleStrategy) {
    super(
        name,
        id,
        threadGroup,
        taskScheduler,
        clock,
        timerQueue,
        ActorMetrics.disabled(),
        idleStrategy);
    this.idleStrategy = new ControlledIdleStrategy(idleStrategy);
  }

  @Override
  public CompletableFuture<Void> close() {
    phaser.arriveAndDeregister();
    return super.close();
  }

  public void resumeTasks() {
    phaser.arriveAndAwaitAdvance();
  }

  public void waitUntilDone() {
    while (phaser.getArrivedParties() < 1) {
      // spin until thread is idle again
      Thread.yield();
    }
  }

  public void workUntilDone() {
    resumeTasks();
    waitUntilDone();
  }

  private final class ControlledIdleStrategy extends ActorTaskRunnerIdleStrategy {

    private ControlledIdleStrategy(final IdleStrategy idleStrategy) {
      super(idleStrategy);
    }

    @Override
    protected void onIdle() {
      super.onIdle();
      phaser.arriveAndAwaitAdvance();
    }
  }
}
