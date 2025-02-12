/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.test;

import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.scheduler.clock.DefaultActorClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

public final class TestActorSchedulerFactory {
  private TestActorSchedulerFactory() {}

  public static ActorScheduler ofBrokerConfig(final BrokerCfg config) {
    return ofBrokerConfig(config, new DefaultActorClock());
  }

  public static ActorScheduler ofBrokerConfig(final BrokerCfg config, final ActorClock clock) {
    final var threads = config.getThreads();
    final var features = config.getExperimental().getFeatures();

    final var scheduler =
        ActorScheduler.newActorScheduler()
            .setCpuBoundActorThreadCount(threads.getCpuThreadCount())
            .setIoBoundActorThreadCount(threads.getIoThreadCount())
            .setMeterRegistry(features.isEnableActorMetrics() ? new SimpleMeterRegistry() : null)
            .setActorClock(clock)
            .build();
    scheduler.start();
    return scheduler;
  }
}
