/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.rebalance;

import io.atomix.cluster.MemberId;
import io.atomix.raft.LeadershipTransferCoordinatorCheck;
import io.atomix.raft.LeadershipTransferResult;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationCoordinatorSupplier;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import java.util.Optional;
import java.util.function.Supplier;
import org.jspecify.annotations.NullMarked;

/**
 * Answers, for a partition leader, whether the node asking it to transfer leadership is the
 * cluster's rebalancing coordinator.
 *
 * <p>It applies the same lowest-id rule the coordinator itself is chosen by, over the same
 * committed cluster configuration, so a leader and a coordinator that agree on the configuration
 * agree on who the coordinator is. Where they disagree, the version decides: a coordinator working
 * from a configuration older than the leader's may have missed the membership change that stopped
 * it being the coordinator, so its requests are refused until it catches up.
 */
@NullMarked
public final class ClusterConfigurationCoordinatorCheck
    implements LeadershipTransferCoordinatorCheck {

  private final Supplier<ClusterConfiguration> clusterConfiguration;

  public ClusterConfigurationCoordinatorCheck(
      final Supplier<ClusterConfiguration> clusterConfiguration) {
    this.clusterConfiguration = clusterConfiguration;
  }

  @Override
  public Optional<LeadershipTransferResult> validate(
      final MemberId coordinator, final long configurationVersion) {
    final var configuration = clusterConfiguration.get();
    if (configuration == null || configuration.isUninitialized()) {
      // Without a configuration of our own we have nothing to check the requester against, and
      // cannot tell a real coordinator from a stale one.
      return Optional.of(LeadershipTransferResult.STALE_CONFIGURATION);
    }
    if (configurationVersion < configuration.version()) {
      return Optional.of(LeadershipTransferResult.STALE_CONFIGURATION);
    }
    final var expected =
        ClusterConfigurationCoordinatorSupplier.ofMembers(configuration.members().keySet())
            .getDefaultCoordinator();
    return expected.equals(coordinator)
        ? Optional.empty()
        : Optional.of(LeadershipTransferResult.NOT_COORDINATOR);
  }
}
