/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.partitioning;

import static com.google.common.base.MoreObjects.toStringHelper;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.primitive.partition.ManagedPartitionGroup;
import io.atomix.primitive.partition.ManagedPartitionService;
import io.atomix.primitive.partition.impl.DefaultPartitionService;
import io.atomix.raft.partition.RaftPartitionGroup;
import io.atomix.utils.concurrent.Futures;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class PartitionManagerImpl implements PartitionManager {

  protected volatile CompletableFuture<Void> closeFuture;
  private ManagedPartitionService partitions;
  private ManagedPartitionGroup partitionGroup;

  public PartitionManagerImpl(
      final RaftPartitionGroup partitionGroup,
      final ClusterMembershipService membershipService,
      final ClusterCommunicationService communicationService) {

    this.partitionGroup = Objects.requireNonNull(partitionGroup);

    partitions = buildPartitionService(membershipService, communicationService);
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
                    }
                    return null;
                  });
    }

    return closeFuture;
  }

  @Override
  public String toString() {
    return toStringHelper(this).add("partitionGroup", partitionGroup).toString();
  }

  /** Builds a partition service. */
  private ManagedPartitionService buildPartitionService(
      final ClusterMembershipService clusterMembershipService,
      final ClusterCommunicationService messagingService) {

    return new DefaultPartitionService(clusterMembershipService, messagingService, partitionGroup);
  }
}
