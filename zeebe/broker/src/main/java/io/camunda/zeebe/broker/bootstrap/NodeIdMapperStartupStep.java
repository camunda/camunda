/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.broker.clustering.mapper.NodeIdMapper;
import io.camunda.zeebe.broker.clustering.mapper.S3LeaseConfig;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;

public class NodeIdMapperStartupStep extends AbstractBrokerStartupStep {

  private final NodeIdMapper nodeIdMapper;
  private final S3LeaseConfig s3LeaseConfig;

  public NodeIdMapperStartupStep(final S3LeaseConfig s3LeaseConfig, final int clusterSize) {
    this.s3LeaseConfig = s3LeaseConfig;
    nodeIdMapper = new NodeIdMapper(s3LeaseConfig, clusterSize);
  }

  @Override
  public String getName() {
    return getClass().getSimpleName();
  }

  @Override
  void startupInternal(
      final BrokerStartupContext brokerStartupContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> startupFuture) {
    concurrencyControl.run(
        () -> {
          try {
            final int brokerId = nodeIdMapper.start();
            brokerStartupContext.getBrokerConfiguration().getCluster().setNodeId(brokerId);
            brokerStartupContext.getBrokerInfo().setNodeId(brokerId);
            final var dir = brokerStartupContext.getBrokerConfiguration().getData().getDirectory();
            brokerStartupContext
                .getBrokerConfiguration()
                .getData()
                .setDirectory(dir + "/node-" + brokerId);
            startupFuture.complete(brokerStartupContext);
          } catch (final Exception e) {
            startupFuture.completeExceptionally(e);
          }
        });
  }

  @Override
  void shutdownInternal(
      final BrokerStartupContext brokerShutdownContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> shutdownFuture) {
    concurrencyControl.run(
        () -> {
          nodeIdMapper.shutdown();
          shutdownFuture.complete(brokerShutdownContext);
        });
  }
}
