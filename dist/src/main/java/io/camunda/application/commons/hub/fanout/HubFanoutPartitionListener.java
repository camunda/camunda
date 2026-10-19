/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.hub.fanout;

import io.camunda.zeebe.broker.PartitionListener;
import io.camunda.zeebe.engine.state.QueryService;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.util.function.Supplier;

/**
 * Runs the fan-out puller of one physical tenant on the leader of that tenant's deployment
 * partition, which processes all identity commands. Raft leadership gives a single puller per
 * tenant and failover for free.
 */
final class HubFanoutPartitionListener implements PartitionListener {

  private final Supplier<HubFanoutPuller> pullerFactory;
  private HubFanoutPuller puller;

  HubFanoutPartitionListener(final Supplier<HubFanoutPuller> pullerFactory) {
    this.pullerFactory = pullerFactory;
  }

  @Override
  public ActorFuture<Void> onBecomingFollower(final int partitionId, final long term) {
    if (partitionId == Protocol.DEPLOYMENT_PARTITION) {
      stop();
    }
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Void> onBecomingLeader(
      final int partitionId,
      final long term,
      final LogStream logStream,
      final QueryService queryService) {
    if (partitionId == Protocol.DEPLOYMENT_PARTITION) {
      // Leader -> leader with a new term is possible; never run two pullers.
      stop();
      start();
    }
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Void> onBecomingInactive(final int partitionId, final long term) {
    if (partitionId == Protocol.DEPLOYMENT_PARTITION) {
      stop();
    }
    return CompletableActorFuture.completed(null);
  }

  synchronized boolean isPulling() {
    return puller != null;
  }

  private synchronized void start() {
    puller = pullerFactory.get();
    puller.start();
  }

  synchronized void stop() {
    if (puller != null) {
      puller.stop();
      puller = null;
    }
  }
}
