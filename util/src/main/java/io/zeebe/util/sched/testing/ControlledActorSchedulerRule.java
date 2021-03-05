/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util.sched.testing;

import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.ActorScheduler.ActorSchedulerBuilder;
import io.zeebe.util.sched.ActorScheduler.ActorThreadFactory;
import io.zeebe.util.sched.ActorThread;
import io.zeebe.util.sched.ActorThreadGroup;
import io.zeebe.util.sched.ActorTimerQueue;
import io.zeebe.util.sched.TaskScheduler;
import io.zeebe.util.sched.clock.ActorClock;
import io.zeebe.util.sched.clock.ControlledActorClock;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.util.concurrent.Callable;
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
  }

  @Override
  protected void after() {
    actorScheduler.stop();
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

  static class ControlledActorThreadFactory implements ActorThreadFactory {
    private ControlledActorThread controlledThread;

    @Override
    public ActorThread newThread(
        final String name,
        final int id,
        final ActorThreadGroup threadGroup,
        final TaskScheduler taskScheduler,
        final ActorClock clock,
        final ActorTimerQueue timerQueue) {
      controlledThread =
          new ControlledActorThread(name, id, threadGroup, taskScheduler, clock, timerQueue);
      return controlledThread;
    }
  }
}
