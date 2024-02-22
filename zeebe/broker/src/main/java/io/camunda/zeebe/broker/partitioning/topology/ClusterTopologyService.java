/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.partitioning.topology;

import io.camunda.zeebe.broker.bootstrap.BrokerStartupContext;
import io.camunda.zeebe.scheduler.AsyncClosable;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.topology.changes.PartitionChangeExecutor;

public interface ClusterTopologyService extends AsyncClosable {
  PartitionDistribution getPartitionDistribution();

  void registerPartitionChangeExecutor(PartitionChangeExecutor executor);

  void removePartitionChangeExecutor();

  ActorFuture<Void> start(BrokerStartupContext brokerStartupContext);
}
