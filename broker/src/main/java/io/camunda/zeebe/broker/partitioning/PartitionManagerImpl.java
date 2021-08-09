/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.partitioning;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.primitive.partition.ManagedPartitionGroup;
import io.atomix.primitive.partition.ManagedPartitionService;
import io.atomix.primitive.partition.impl.DefaultPartitionService;
import io.atomix.raft.partition.RaftPartitionGroup;
import io.atomix.utils.concurrent.Futures;
import io.camunda.zeebe.broker.PartitionListener;
import io.camunda.zeebe.broker.partitioning.topology.TopologyManager;
import io.camunda.zeebe.broker.partitioning.topology.TopologyManagerImpl;
import io.camunda.zeebe.broker.partitioning.topology.TopologyPartitionListener;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.util.health.HealthStatus;
import io.camunda.zeebe.util.sched.ActorSchedulingService;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class PartitionManagerImpl
    implements PartitionManager, TopologyManager, PartitionListener {

  protected volatile CompletableFuture<Void> closeFuture;
  private final ActorSchedulingService actorSchedulingService;
  private ManagedPartitionService partitions;
  private ManagedPartitionGroup partitionGroup;
  private TopologyManagerImpl topologyManager;

  public PartitionManagerImpl(
      final ActorSchedulingService actorSchedulingService,
      final BrokerInfo localBroker,
      final RaftPartitionGroup partitionGroup,
      final ClusterMembershipService membershipService,
      final ClusterCommunicationService communicationService) {

    this.actorSchedulingService = actorSchedulingService;

    this.partitionGroup = Objects.requireNonNull(partitionGroup);

    partitions = buildPartitionService(membershipService, communicationService);

    topologyManager = new TopologyManagerImpl(membershipService, localBroker);
  }

  @Override
  public ManagedPartitionGroup getPartitionGroup() {
    return partitionGroup;
  }

  public synchronized CompletableFuture<Void> start() {
    if (closeFuture != null) {
      return Futures.exceptionalFuture(
          new IllegalStateException(
              "Atomix instance " + (closeFuture.isDone() ? "shutdown" : "shutting down")));
    }

    actorSchedulingService.submitActor(topologyManager);

    return partitions.start().thenApply(ps -> null);
  }

  public synchronized CompletableFuture<Void> stop() {
    if (closeFuture == null) {
      closeFuture =
          partitions
              .stop()
              .thenApply(
                  ps -> {
                    synchronized (this) {
                      partitionGroup = null;
                      partitions = null;
                      topologyManager.close();
                      topologyManager = null;
                    }
                    return null;
                  });
    }

    return closeFuture;
  }

  /** Builds a partition service. */
  private ManagedPartitionService buildPartitionService(
      final ClusterMembershipService clusterMembershipService,
      final ClusterCommunicationService messagingService) {

    return new DefaultPartitionService(clusterMembershipService, messagingService, partitionGroup);
  }

  @Override
  public String toString() {
    return "PartitionManagerImpl{" + "partitionGroup=" + partitionGroup + '}';
  }

  @Override
  public ActorFuture<Void> onBecomingFollower(final int partitionId, final long term) {
    return topologyManager.onBecomingFollower(partitionId, term);
  }

  @Override
  public ActorFuture<Void> onBecomingLeader(
      final int partitionId, final long term, final LogStream logStream) {
    return topologyManager.onBecomingLeader(partitionId, term, logStream);
  }

  @Override
  public ActorFuture<Void> onBecomingInactive(final int partitionId, final long term) {
    return topologyManager.onBecomingInactive(partitionId, term);
  }

  public void onHealthChanged(final int i, final HealthStatus healthStatus) {
    topologyManager.onHealthChanged(i, healthStatus);
  }

  @Override
  public void removeTopologyPartitionListener(final TopologyPartitionListener listener) {
    topologyManager.removeTopologyPartitionListener(listener);
  }

  @Override
  public void addTopologyPartitionListener(final TopologyPartitionListener listener) {
    topologyManager.addTopologyPartitionListener(listener);
  }
}
