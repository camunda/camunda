/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway;

import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.shared.ActorClockConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
final class ActorSchedulerComponent {

  private final GatewayCfg config;
  private final ActorClockConfiguration clockConfiguration;

  @Autowired
  ActorSchedulerComponent(
      final GatewayCfg config, final ActorClockConfiguration clockConfiguration) {
    this.config = config;
    this.clockConfiguration = clockConfiguration;
  }

  // disable automatic registration of close as the destroy method, the application will manually
  // close this
  @Bean(destroyMethod = "")
  ActorScheduler actorScheduler() {
    return ActorScheduler.newActorScheduler()
        .setCpuBoundActorThreadCount(config.getThreads().getManagementThreads())
        .setIoBoundActorThreadCount(0)
        .setSchedulerName("gateway-scheduler")
        .setActorClock(clockConfiguration.getClock())
        .build();
  }
}
