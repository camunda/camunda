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
import io.camunda.zeebe.scheduler.clock.ActorClock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.web.context.annotation.ApplicationScope;

@Configuration(proxyBeanMethods = false)
public final class ActorSchedulerConfiguration {
  private final BrokerCfg brokerCfg;
  private final ActorClock clock;

  @Autowired
  public ActorSchedulerConfiguration(final BrokerCfg brokerCfg, final ActorClock clock) {
    this.brokerCfg = brokerCfg;
    this.clock = clock;
  }

  @Bean(destroyMethod = "") // disable automatically calling close as we will take care of this
  @ApplicationScope(proxyMode = ScopedProxyMode.NO)
  public ActorScheduler scheduler() {
    final ThreadsCfg cfg = brokerCfg.getThreads();

    final int cpuThreads = cfg.getCpuThreadCount();
    final int ioThreads = cfg.getIoThreadCount();
    final boolean metricsEnabled = brokerCfg.getExperimental().getFeatures().isEnableActorMetrics();

    return ActorScheduler.newActorScheduler()
        .setActorClock(clock)
        .setCpuBoundActorThreadCount(cpuThreads)
        .setIoBoundActorThreadCount(ioThreads)
        .setMetricsEnabled(metricsEnabled)
        .setSchedulerName(String.format("Broker-%d", brokerCfg.getCluster().getNodeId()))
        .build();
  }
}
