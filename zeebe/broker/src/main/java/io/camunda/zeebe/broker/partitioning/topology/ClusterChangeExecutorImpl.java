/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.topology;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.changes.ClusterChangeExecutor;
import io.camunda.zeebe.dynamic.nodeid.NodeIdProvider;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class ClusterChangeExecutorImpl implements ClusterChangeExecutor {

  private final ConcurrencyControl concurrencyControl;
  private final NodeIdProvider nodeIdProvider;
  private final Optional<String> zone;

  public ClusterChangeExecutorImpl(
      final ConcurrencyControl concurrencyControl,
      final NodeIdProvider nodeIdProvider,
      final Optional<String> zone) {
    this.concurrencyControl = concurrencyControl;
    this.nodeIdProvider = Objects.requireNonNull(nodeIdProvider);
    this.zone = zone;
  }

  @Override
  public ActorFuture<Void> preScaling(
      final int currentClusterSize, final Set<MemberId> clusterMembers) {
    final ActorFuture<Void> result = concurrencyControl.createFuture();

    if (currentClusterSize >= clusterMembers.size()) {
      // No scaling up, so no need to call the NodeIdProvider
      result.complete(null);
      return result;
    }

    concurrencyControl.run(
        () -> {
          try {
            nodeIdProvider
                .scale(membersInZone(clusterMembers))
                .thenAcceptAsync(ignore -> result.complete(null), concurrencyControl)
                .exceptionallyAsync(
                    e -> {
                      result.completeExceptionally(e);
                      return null;
                    },
                    concurrencyControl);
          } catch (final Exception e) {
            result.completeExceptionally(e);
          }
        });

    return result;
  }

  @Override
  public ActorFuture<Void> postScaling(final Set<MemberId> clusterMembers) {
    final ActorFuture<Void> result = concurrencyControl.createFuture();
    // Here it is ok to execute even if the cluster size did not change.
    // For scale up this will be a no-op as the leases are already created, and for scale down
    // additional leases will be removed.
    concurrencyControl.run(
        () -> {
          try {
            nodeIdProvider
                .scale(membersInZone(clusterMembers))
                .thenAcceptAsync(ignore -> result.complete(null), concurrencyControl)
                .exceptionallyAsync(
                    e -> {
                      result.completeExceptionally(e);
                      return null;
                    },
                    concurrencyControl);
          } catch (final Exception e) {
            result.completeExceptionally(e);
          }
        });

    return result;
  }

  /**
   * @return the number of members in the same zone as this node
   */
  private int membersInZone(final Set<MemberId> clusterMembers) {
    return (int)
        clusterMembers.stream().filter(m -> Optional.ofNullable(m.zone()).equals(zone)).count();
  }
}
