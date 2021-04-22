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
import io.zeebe.util.sched.clock.ActorClock;
import io.zeebe.util.sched.future.ActorFuture;
import org.agrona.LangUtil;
import org.junit.rules.ExternalResource;

public final class ActorSchedulerRule extends ExternalResource {

  private final int numOfIoThreads;
  private final int numOfThreads;
  private final ActorClock clock;

  private ActorSchedulerBuilder builder;
  private ActorScheduler actorScheduler;

  public ActorSchedulerRule(final int numOfThreads, final ActorClock clock) {
    this(numOfThreads, 2, clock);
  }

  public ActorSchedulerRule(
      final int numOfThreads, final int numOfIoThreads, final ActorClock clock) {

    this.numOfIoThreads = numOfIoThreads;
    this.numOfThreads = numOfThreads;
    this.clock = clock;
  }

  public ActorSchedulerRule(final int numOfThreads) {
    this(numOfThreads, null);
  }

  public ActorSchedulerRule(final ActorClock clock) {
    this(Math.max(1, Runtime.getRuntime().availableProcessors() - 2), clock);
  }

  public ActorSchedulerRule() {
    this(null);
  }

  @Override
  public void before() {
    builder =
        ActorScheduler.newActorScheduler()
            .setCpuBoundActorThreadCount(numOfThreads)
            .setIoBoundActorThreadCount(numOfIoThreads)
            .setActorClock(clock);

    actorScheduler = builder.build();
    actorScheduler.start();
  }

  @Override
  public void after() {
    try {
      actorScheduler.close();
    } catch (Exception e) {
      LangUtil.rethrowUnchecked(e);
    }

    actorScheduler = null;
    builder = null;
  }

  public ActorFuture<Void> submitActor(final Actor actor) {
    return actorScheduler.submitActor(actor);
  }

  public ActorScheduler get() {
    return actorScheduler;
  }

  public ActorSchedulerBuilder getBuilder() {
    return builder;
  }
}
