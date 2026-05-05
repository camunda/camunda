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
import io.camunda.zeebe.dynamic.config.util.CoordinatorResolver;
import java.util.Collection;
import java.util.Set;
import java.util.function.Supplier;

public interface ClusterConfigurationCoordinatorSupplier {
  MemberId getDefaultCoordinator();

  MemberId getNextCoordinator(Collection<MemberId> members);

  MemberId getNextCoordinatorExcluding(Set<MemberId> memberIds);

  static ClusterConfigurationCoordinatorSupplier of(
      final Supplier<ClusterConfiguration> clusterTopologySupplier) {

    return new ClusterConfigurationCoordinatorSupplier() {

      @Override
      public MemberId getDefaultCoordinator() {
        final var clusterTopology = clusterTopologySupplier.get();
        return CoordinatorResolver.resolveCoordinator(clusterTopology).orElse(MemberId.from("0"));
      }

      @Override
      public MemberId getNextCoordinator(final Collection<MemberId> members) {
        return CoordinatorResolver.resolveCoordinatorFrom(members).orElse(MemberId.from("0"));
      }

      @Override
      public MemberId getNextCoordinatorExcluding(final Set<MemberId> memberIds) {
        final var currentMembers = clusterTopologySupplier.get().members().keySet();
        final var newMembers = currentMembers.stream().filter(m -> !memberIds.contains(m)).toList();
        return CoordinatorResolver.resolveCoordinatorFrom(newMembers).orElse(MemberId.from("0"));
      }
    };
  }
}
