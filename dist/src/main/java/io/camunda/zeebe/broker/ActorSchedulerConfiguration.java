/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker;

import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.ThreadsCfg;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.shared.ActorClockConfiguration;
import io.camunda.zeebe.shared.IdleStrategyConfig.IdleStrategySupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public final class ActorSchedulerConfiguration {
  private final BrokerCfg brokerCfg;
  private final ActorClockConfiguration actorClockConfiguration;

  @Autowired
  public ActorSchedulerConfiguration(
      final BrokerCfg brokerCfg, final ActorClockConfiguration actorClockConfiguration) {
    this.brokerCfg = brokerCfg;
    this.actorClockConfiguration = actorClockConfiguration;
  }

  @Bean(destroyMethod = "close")
  public ActorScheduler scheduler(final IdleStrategySupplier idleStrategySupplier) {
    final ThreadsCfg cfg = brokerCfg.getThreads();

    final var cpuThreads = cfg.getCpuThreadCount();
    final var ioThreads = cfg.getIoThreadCount();
    final var metricsEnabled = brokerCfg.getExperimental().getFeatures().isEnableActorMetrics();

    final var scheduler =
        ActorScheduler.newActorScheduler()
            .setActorClock(actorClockConfiguration.getClock().orElse(null))
            .setCpuBoundActorThreadCount(cpuThreads)
            .setIoBoundActorThreadCount(ioThreads)
            .setMetricsEnabled(metricsEnabled)
            .setSchedulerName(String.format("Broker-%d", brokerCfg.getCluster().getNodeId()))
            .setIdleStrategySupplier(idleStrategySupplier)
            .build();
    scheduler.start();
    return scheduler;
  }
}
