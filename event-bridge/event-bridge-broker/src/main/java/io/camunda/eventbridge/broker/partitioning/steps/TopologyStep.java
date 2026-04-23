/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.partitioning.steps;

import io.camunda.eventbridge.broker.partitioning.PartitionContext;
import io.camunda.eventbridge.broker.partitioning.PartitionStartupStep;
import io.camunda.zeebe.broker.partitioning.topology.TopologyManagerImpl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gossips the partition role via SWIM. Last step to activate — gossips LEADER only after all
 * services are ready. First step to deactivate — gossips FOLLOWER before services are torn down.
 */
public final class TopologyStep implements PartitionStartupStep {

  private static final Logger LOG = LoggerFactory.getLogger(TopologyStep.class);

  private final TopologyManagerImpl topologyManager;

  public TopologyStep(final TopologyManagerImpl topologyManager) {
    this.topologyManager = topologyManager;
  }

  @Override
  public String getName() {
    return "Topology";
  }

  @Override
  public ActorFuture<Void> activate(final PartitionContext context) {
    final var partitionId = context.getPartitionId();
    final var term = context.getRaftPartition().getServer().getTerm();

    LOG.info("Partition {} — gossiped LEADER (term {})", partitionId, term);
    return topologyManager.onBecomingLeader(partitionId, term, null, null);
  }

  @Override
  public ActorFuture<Void> deactivate(final PartitionContext context) {
    final var partitionId = context.getPartitionId();
    final var server = context.getRaftPartition().getServer();
    final var term = server != null ? server.getTerm() : 0;

    LOG.info("Partition {} — gossiped FOLLOWER (term {})", partitionId, term);
    return topologyManager.onBecomingFollower(partitionId, term);
  }
}
