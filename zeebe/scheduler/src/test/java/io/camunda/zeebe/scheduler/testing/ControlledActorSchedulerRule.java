/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler.testing;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorMetrics;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.ActorScheduler.ActorSchedulerBuilder;
import io.camunda.zeebe.scheduler.ActorScheduler.ActorThreadFactory;
import io.camunda.zeebe.scheduler.ActorThread;
import io.camunda.zeebe.scheduler.ActorThreadGroup;
import io.camunda.zeebe.scheduler.ActorTimerQueue;
import io.camunda.zeebe.scheduler.TaskScheduler;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.scheduler.clock.ControlledActorClock;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.agrona.concurrent.IdleStrategy;
import org.junit.rules.ExternalResource;

public final class ControlledActorSchedulerRule extends ExternalResource {
  private final ActorScheduler actorScheduler;
  private final ControlledActorThread controlledActorTaskRunner;
  private final ControlledActorClock clock = new ControlledActorClock();

  public ControlledActorSchedulerRule() {
    final ControlledActorThreadFactory actorTaskRunnerFactory = new ControlledActorThreadFactory();
    final ActorTimerQueue timerQueue = new ActorTimerQueue(clock, 1);
    final ActorSchedulerBuilder builder =
        ActorScheduler.newActorScheduler()
            .setActorClock(clock)
            .setCpuBoundActorThreadCount(1)
            .setIoBoundActorThreadCount(0)
            .setActorThreadFactory(actorTaskRunnerFactory)
            .setActorTimerQueue(timerQueue);

    actorScheduler = builder.build();
    controlledActorTaskRunner = actorTaskRunnerFactory.controlledThread;
  }

  @Override
  protected void before() {
    actorScheduler.start();
    controlledActorTaskRunner.waitUntilDone();
  }

  @Override
  protected void after() {
    try {
      actorScheduler.stop().get(5000, MILLISECONDS);
    } catch (final InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  public ActorFuture<Void> submitActor(final Actor actor) {
    return actorScheduler.submitActor(actor);
  }

  public ActorScheduler get() {
    return actorScheduler;
  }

  public void workUntilDone() {
    controlledActorTaskRunner.workUntilDone();
  }

  public <T> ActorFuture<T> call(final Callable<T> callable) {
    final ActorFuture<T> future = new CompletableActorFuture<>();
    submitActor(new CallingActor(future, callable));
    return future;
  }

  public ControlledActorClock getClock() {
    return clock;
  }

  static final class ControlledActorThreadFactory implements ActorThreadFactory {
    private ControlledActorThread controlledThread;

    @Override
    public ActorThread newThread(
        final String name,
        final int id,
        final ActorThreadGroup threadGroup,
        final TaskScheduler taskScheduler,
        final ActorClock clock,
        final ActorTimerQueue timerQueue,
        final ActorMetrics actorMetrics,
        final IdleStrategy idleStrategy) {
      controlledThread =
          new ControlledActorThread(
              name, id, threadGroup, taskScheduler, clock, timerQueue, idleStrategy);
      return controlledThread;
    }
  }

  static class CallingActor<T> extends Actor {
    private final ActorFuture<T> future;
    private final Callable<T> callable;

    CallingActor(final ActorFuture<T> future, final Callable<T> callable) {
      this.future = future;
      this.callable = callable;
    }

    @Override
    protected void onActorStarted() {
      actor.run(
          () -> {
            try {
              future.complete(callable.call());
            } catch (final Exception e) {
              future.completeExceptionally(e);
            }
          });
    }
  }
}
