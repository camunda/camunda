/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker;

import io.atomix.cluster.AtomixCluster;
import io.camunda.eventbridge.broker.bootstrap.BrokerBootstrap;
import io.camunda.eventbridge.broker.partitioning.PartitionDistributor;
import io.camunda.eventbridge.broker.partitioning.PartitionFactory;
import io.camunda.eventbridge.broker.partitioning.RoundRobinPartitionDistributor;
import io.camunda.eventbridge.broker.threading.ExecutorServiceFactory;
import io.camunda.eventbridge.broker.threading.VirtualThreadExecutorFactory;
import io.camunda.eventbridge.core.config.EventBridgeProperties;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.micrometer.core.instrument.MeterRegistry;
import org.agrona.concurrent.IdGenerator;
import org.agrona.concurrent.SnowflakeIdGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class EventBridgeBrokerConfiguration {

  @Bean
  ExecutorServiceFactory executorServiceFactory() {
    return new VirtualThreadExecutorFactory();
  }

  @Bean
  IdGenerator idGenerator(final EventBridgeProperties properties) {
    final var nodeId = Long.parseLong(properties.cluster().nodeId().replaceAll("[^0-9]", ""));
    return new SnowflakeIdGenerator(nodeId);
  }

  @Bean
  PartitionDistributor partitionDistributor() {
    return new RoundRobinPartitionDistributor(PartitionFactory.GROUP_NAME);
  }

  @Bean
  BrokerBootstrap brokerBootstrap(
      final AtomixCluster cluster,
      final ActorScheduler actorScheduler,
      final EventBridgeProperties properties,
      final PartitionDistributor distributor,
      final ExecutorServiceFactory executorServiceFactory,
      final IdGenerator idGenerator,
      final MeterRegistry meterRegistry) {
    return new BrokerBootstrap(
        cluster,
        actorScheduler,
        properties,
        distributor,
        executorServiceFactory,
        idGenerator,
        meterRegistry);
  }

  @Bean
  EventBridgeBrokerLifecycle brokerLifecycle(final BrokerBootstrap brokerBootstrap) {
    return new EventBridgeBrokerLifecycle(brokerBootstrap);
  }
}
