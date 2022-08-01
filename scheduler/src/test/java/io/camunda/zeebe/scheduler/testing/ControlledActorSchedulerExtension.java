/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.scheduler.testing;

import io.camunda.zeebe.scheduler.Actor;
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
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class ControlledActorSchedulerExtension implements BeforeEachCallback, AfterEachCallback {

  private ActorScheduler actorScheduler;
  private ControlledActorThread controlledActorTaskRunner;

  @Override
  public void afterEach(final ExtensionContext extensionContext) throws Exception {
    actorScheduler.stop();
  }

  @Override
  public void beforeEach(final ExtensionContext extensionContext) throws Exception {
    final ControlledActorThreadFactory actorTaskRunnerFactory = new ControlledActorThreadFactory();
    final ControlledActorClock clock = new ControlledActorClock();
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
    actorScheduler.start();
  }

  public ActorFuture<Void> submitActor(final Actor actor) {
    return actorScheduler.submitActor(actor);
  }

  public void workUntilDone() {
    controlledActorTaskRunner.workUntilDone();
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
        final boolean metricsEnabled) {
      controlledThread =
          new ControlledActorThread(name, id, threadGroup, taskScheduler, clock, timerQueue);
      return controlledThread;
    }
  }
}
