/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.topology;

import io.camunda.zeebe.broker.bootstrap.BrokerStartupContext;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationManager.InconsistentConfigurationListener;
import io.camunda.zeebe.dynamic.config.changes.ClusterChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.PartitionChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.PartitionScalingChangeExecutor;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.scheduler.AsyncClosable;
import io.camunda.zeebe.scheduler.future.ActorFuture;

public interface ClusterConfigurationService extends AsyncClosable {
  PartitionDistribution getPartitionDistribution();

  void registerPartitionChangeExecutors(
      PartitionChangeExecutor partitionChangeExecutor,
      PartitionScalingChangeExecutor partitionScalingChangeExecutor);

  void removePartitionChangeExecutor();

  ActorFuture<Void> start(BrokerStartupContext brokerStartupContext);

  void registerInconsistentConfigurationListener(InconsistentConfigurationListener listener);

  void removeInconsistentConfigurationListener();

  ClusterConfiguration getInitialClusterConfiguration();

  ClusterChangeExecutor getClusterChangeExecutor();
}
