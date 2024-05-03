/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.topology.api;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.topology.state.ClusterTopology;
import java.util.Collection;
import java.util.function.Supplier;

public interface TopologyCoordinatorSupplier {
  MemberId getDefaultCoordinator();

  MemberId getNextCoordinator(Collection<MemberId> members);

  class ClusterTopologyAwareCoordinatorSupplier implements TopologyCoordinatorSupplier {
    private final Supplier<ClusterTopology> clusterTopologySupplier;

    public ClusterTopologyAwareCoordinatorSupplier(
        final Supplier<ClusterTopology> clusterTopologySupplier) {
      this.clusterTopologySupplier = clusterTopologySupplier;
    }

    private MemberId lowestMemberId(final Collection<MemberId> members) {
      if (members.isEmpty()) {
        // if cluster topology is not initialized, fall back to member 0
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
  }
}
