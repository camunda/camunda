/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler.testing;

import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.ActorScheduler.ActorSchedulerBuilder;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.agrona.LangUtil;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/** JUnit 5 counterpart of {@link ActorSchedulerRule}. */
public final class ActorSchedulerExtension implements BeforeEachCallback, AfterEachCallback {

  private final int numOfIoThreads;
  private final int numOfThreads;
  private final ActorClock clock;

  private ActorSchedulerBuilder builder;
  private ActorScheduler actorScheduler;
  private SimpleMeterRegistry meterRegistry;

  public ActorSchedulerExtension(final int numOfThreads, final ActorClock clock) {
    this(numOfThreads, 2, clock);
  }

  public ActorSchedulerExtension(
      final int numOfThreads, final int numOfIoThreads, final ActorClock clock) {
    this.numOfIoThreads = numOfIoThreads;
    this.numOfThreads = numOfThreads;
    this.clock = clock;
  }

  public ActorSchedulerExtension(final int numOfThreads) {
    this(numOfThreads, null);
  }

  public ActorSchedulerExtension(final ActorClock clock) {
    this(Math.max(1, Runtime.getRuntime().availableProcessors() - 2), clock);
  }

  public ActorSchedulerExtension() {
    this((ActorClock) null);
  }

  @Override
  public void beforeEach(final ExtensionContext extensionContext) {
    meterRegistry = new SimpleMeterRegistry();
    builder =
        ActorScheduler.newActorScheduler()
            .setCpuBoundActorThreadCount(numOfThreads)
            .setIoBoundActorThreadCount(numOfIoThreads)
            .setActorClock(clock)
            .setMeterRegistry(meterRegistry);

    actorScheduler = builder.build();
    actorScheduler.start();
  }

  @Override
  public void afterEach(final ExtensionContext extensionContext) {
    try {
      actorScheduler.close();
    } catch (final Exception e) {
      LangUtil.rethrowUnchecked(e);
    }

    actorScheduler = null;
    builder = null;
    MicrometerUtil.close(meterRegistry);
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
