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
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

public interface ClusterConfigurationCoordinatorSupplier {
  MemberId getDefaultCoordinator();

  MemberId getNextCoordinator(Collection<MemberId> members);

  MemberId getNextCoordinatorExcluding(Set<MemberId> memberIds);

  /**
   * Returns a coordinator that is not a member of the given zone, or empty if no such member
   * exists. Used by zone force-removal, where the zone's brokers (including the default
   * coordinator) may be down and unreachable. An empty result means every member is in the zone,
   * i.e. it is the only remaining zone and removing it is invalid.
   */
  Optional<MemberId> getNextCoordinatorExcludingZone(String zone);

  static ClusterConfigurationCoordinatorSupplier ofMembers(final Set<MemberId> members) {
    return ofMembers(() -> members);
  }

  static ClusterConfigurationCoordinatorSupplier ofMembers(
      final Supplier<Set<MemberId>> memberSupplier) {
    return new ClusterConfigurationCoordinatorSupplier() {

      private MemberId lowestMemberId(final Collection<MemberId> members) {
        if (members.isEmpty()) {
          // if cluster configuration is not initialized, fall back to member 0
          return MemberId.from("0");
        }
        return members.stream().min(MemberId.ID_COMPARATOR).orElseThrow();
      }

      @Override
      public MemberId getDefaultCoordinator() {
        return lowestMemberId(memberSupplier.get());
      }

      @Override
      public MemberId getNextCoordinator(final Collection<MemberId> members) {
        return lowestMemberId(members);
      }

      @Override
      public MemberId getNextCoordinatorExcluding(final Set<MemberId> memberIds) {
        final var currentMembers = memberSupplier.get();
        final var newMembers = currentMembers.stream().filter(m -> !memberIds.contains(m)).toList();
        return lowestMemberId(newMembers);
      }

      @Override
      public Optional<MemberId> getNextCoordinatorExcludingZone(final String zone) {
        final var newMembers =
            memberSupplier.get().stream().filter(m -> !m.isInZone(zone)).toList();
        return newMembers.isEmpty() ? Optional.empty() : Optional.of(lowestMemberId(newMembers));
      }
    };
  }

  static ClusterConfigurationCoordinatorSupplier of(
      final Supplier<ClusterConfiguration> clusterTopologySupplier) {
    return ofMembers(() -> clusterTopologySupplier.get().members().keySet());
  }
}
