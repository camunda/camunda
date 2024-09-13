/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import java.util.Collection;
import java.util.Set;
import java.util.function.Supplier;

public interface ClusterConfigurationCoordinatorSupplier {
  MemberId getDefaultCoordinator();

  MemberId getNextCoordinator(Collection<MemberId> members);

  MemberId getNextCoordinatorExcluding(Set<MemberId> memberIds);

  class ClusterClusterConfigurationAwareCoordinatorSupplier
      implements ClusterConfigurationCoordinatorSupplier {
    private final Supplier<ClusterConfiguration> clusterTopologySupplier;

    public ClusterClusterConfigurationAwareCoordinatorSupplier(
        final Supplier<ClusterConfiguration> clusterTopologySupplier) {
      this.clusterTopologySupplier = clusterTopologySupplier;
    }

    private MemberId lowestMemberId(final Collection<MemberId> members) {
      if (members.isEmpty()) {
        // if cluster configuration is not initialized, fall back to member 0
        return MemberId.from("0");
      }
      return members.stream().min(MemberId::compareTo).orElseThrow();
    }

    @Override
    public MemberId getDefaultCoordinator() {
      final var clusterTopology = clusterTopologySupplier.get();
      return lowestMemberId(clusterTopology.members().keySet());
    }

    @Override
    public MemberId getNextCoordinator(final Collection<MemberId> members) {
      return lowestMemberId(members);
    }

    @Override
    public MemberId getNextCoordinatorExcluding(final Set<MemberId> memberIds) {
      final var currentMembers = clusterTopologySupplier.get().members().keySet();
      final var newMembers = currentMembers.stream().filter(m -> !memberIds.contains(m)).toList();
      return lowestMemberId(newMembers);
    }
  }
}
