/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

import io.atomix.cluster.MemberId;
import io.atomix.raft.LeadershipTransferCoordinatorCheck;
import io.atomix.raft.LeadershipTransferResult;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationCoordinatorSupplier;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import java.util.Optional;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Broker-supplied check that the node asking for a leadership transfer really is the cluster's
 * current coordinator.
 */
public final class ClusterConfigurationCoordinatorCheck
    implements LeadershipTransferCoordinatorCheck {

  private final Supplier<@Nullable CurrentClusterConfiguration> clusterConfiguration;

  public ClusterConfigurationCoordinatorCheck(
      final Supplier<@Nullable CurrentClusterConfiguration> clusterConfiguration) {
    this.clusterConfiguration = clusterConfiguration;
  }

  @Override
  public Optional<LeadershipTransferResult> validate(
      final MemberId coordinator, final long configurationVersion) {
    final var configuration = clusterConfiguration.get();
    if (configuration == null || configuration.isUninitialized()) {
      return Optional.of(LeadershipTransferResult.STALE_CONFIGURATION);
    }
    // A future version this node hasn't seen yet may have changed who the coordinator is, so it
    // must not be validated against this (older) membership either.
    if (configurationVersion != configuration.globalConfiguration().version()) {
      return Optional.of(LeadershipTransferResult.STALE_CONFIGURATION);
    }
    final var expected =
        ClusterConfigurationCoordinatorSupplier.ofMembers(configuration.getMembers())
            .getDefaultCoordinator();
    return expected.equals(coordinator)
        ? Optional.empty()
        : Optional.of(LeadershipTransferResult.NOT_COORDINATOR);
  }
}
