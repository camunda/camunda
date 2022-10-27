/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.sched.testing;

import io.camunda.zeebe.util.sched.Actor;
import io.camunda.zeebe.util.sched.ActorScheduler;
import io.camunda.zeebe.util.sched.ActorScheduler.ActorSchedulerBuilder;
import io.camunda.zeebe.util.sched.ActorScheduler.ActorThreadFactory;
import io.camunda.zeebe.util.sched.ActorThread;
import io.camunda.zeebe.util.sched.ActorThreadGroup;
import io.camunda.zeebe.util.sched.ActorTimerQueue;
import io.camunda.zeebe.util.sched.TaskScheduler;
import io.camunda.zeebe.util.sched.clock.ActorClock;
import io.camunda.zeebe.util.sched.clock.ControlledActorClock;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import java.util.Objects;
import java.util.function.Consumer;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class ControlledActorSchedulerExtension implements BeforeEachCallback, AfterEachCallback {

  private final Consumer<ActorSchedulerBuilder> configurator;

  private ActorScheduler actorScheduler;
  private ControlledActorThread controlledActorTaskRunner;

  public ControlledActorSchedulerExtension() {
    this(builder -> {});
  }

  public ControlledActorSchedulerExtension(final Consumer<ActorSchedulerBuilder> configurator) {
    this.configurator = Objects.requireNonNull(configurator, "must specify a configurator");
  }

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

    configurator.accept(builder);
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
        final ActorTimerQueue timerQueue) {
      controlledThread =
          new ControlledActorThread(name, id, threadGroup, taskScheduler, clock, timerQueue);
      return controlledThread;
    }
  }
}
