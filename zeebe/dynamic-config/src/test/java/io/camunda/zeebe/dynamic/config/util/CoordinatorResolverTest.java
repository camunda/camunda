/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class CoordinatorResolverTest {

  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();

  // --- Non-zone-aware (legacy) cluster tests ---

  @Test
  void shouldSelectLowestMemberInNonZoneAwareCluster() {
    // given
    final var config =
        ClusterConfiguration.init()
            .addMember(MemberId.from("0"), MemberState.initializeAsActive(Map.of()))
            .addMember(MemberId.from("1"), MemberState.initializeAsActive(Map.of()))
            .addMember(MemberId.from("2"), MemberState.initializeAsActive(Map.of()));

    // when
    final var coordinator = CoordinatorResolver.resolveCoordinator(config);

    // then
    assertThat(coordinator).contains(MemberId.from("0"));
  }

  @Test
  void shouldReturnEmptyForEmptyConfiguration() {
    // given
    final var config = ClusterConfiguration.init();

    // when
    final var coordinator = CoordinatorResolver.resolveCoordinator(config);

    // then
    assertThat(coordinator).isEmpty();
  }

  @Test
  void shouldReturnTrueForCoordinatorInNonZoneAwareCluster() {
    // given
    final var config =
        ClusterConfiguration.init()
            .addMember(MemberId.from("0"), MemberState.initializeAsActive(Map.of()))
            .addMember(MemberId.from("1"), MemberState.initializeAsActive(Map.of()));

    // when / then
    assertThat(CoordinatorResolver.isCoordinator(MemberId.from("0"), config)).isTrue();
    assertThat(CoordinatorResolver.isCoordinator(MemberId.from("1"), config)).isFalse();
  }

  // --- Zone-aware cluster tests ---

  @Test
  void shouldSelectNodeZeroInPrimaryZone() {
    // given — "primary" zone has higher priorities than "secondary"
    final var config =
        ClusterConfiguration.init()
            .addMember(
                MemberId.from("primary", 0),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(3, partitionConfig))))
            .addMember(
                MemberId.from("primary", 1),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(2, partitionConfig))))
            .addMember(
                MemberId.from("secondary", 0),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(1, partitionConfig))))
            .addMember(
                MemberId.from("secondary", 1),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(1, partitionConfig))));

    // when
    final var coordinator = CoordinatorResolver.resolveCoordinator(config);

    // then
    assertThat(coordinator).contains(MemberId.from("primary", 0));
  }

  @Test
  void shouldSelectOnlyOneCoordinatorInZoneAwareCluster() {
    // given — both zones have nodeId 0
    final var config =
        ClusterConfiguration.init()
            .addMember(
                MemberId.from("primary", 0),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(3, partitionConfig))))
            .addMember(
                MemberId.from("secondary", 0),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(1, partitionConfig))));

    // when / then — only primary/0 is coordinator, not secondary/0
    assertThat(CoordinatorResolver.isCoordinator(MemberId.from("primary", 0), config)).isTrue();
    assertThat(CoordinatorResolver.isCoordinator(MemberId.from("secondary", 0), config)).isFalse();
  }

  @Test
  void shouldTransferCoordinatorWhenZonePrioritiesChange() {
    // given — initially "primary" zone has higher priorities
    final var initialConfig =
        ClusterConfiguration.init()
            .addMember(
                MemberId.from("primary", 0),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(3, partitionConfig))))
            .addMember(
                MemberId.from("secondary", 0),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(1, partitionConfig))));

    assertThat(CoordinatorResolver.isCoordinator(MemberId.from("primary", 0), initialConfig))
        .isTrue();

    // when — priorities are swapped: "secondary" now has higher priority
    final var updatedConfig =
        ClusterConfiguration.init()
            .addMember(
                MemberId.from("primary", 0),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(1, partitionConfig))))
            .addMember(
                MemberId.from("secondary", 0),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(3, partitionConfig))));

    // then — coordinator transfers to secondary/0
    assertThat(CoordinatorResolver.isCoordinator(MemberId.from("primary", 0), updatedConfig))
        .isFalse();
    assertThat(CoordinatorResolver.isCoordinator(MemberId.from("secondary", 0), updatedConfig))
        .isTrue();
  }

  @Test
  void shouldDeterminePrimaryZoneByTotalPriorities() {
    // given — "secondary" zone has a single member with very high priority
    //         "primary" zone has two members with lower individual priorities but higher total
    final var config =
        ClusterConfiguration.init()
            .addMember(
                MemberId.from("primary", 0),
                MemberState.initializeAsActive(
                    Map.of(
                        1, PartitionState.active(3, partitionConfig),
                        2, PartitionState.active(3, partitionConfig))))
            .addMember(
                MemberId.from("primary", 1),
                MemberState.initializeAsActive(
                    Map.of(
                        1, PartitionState.active(2, partitionConfig),
                        2, PartitionState.active(2, partitionConfig))))
            .addMember(
                MemberId.from("secondary", 0),
                MemberState.initializeAsActive(
                    Map.of(
                        1, PartitionState.active(1, partitionConfig),
                        2, PartitionState.active(1, partitionConfig))))
            .addMember(
                MemberId.from("secondary", 1),
                MemberState.initializeAsActive(
                    Map.of(
                        1, PartitionState.active(1, partitionConfig),
                        2, PartitionState.active(1, partitionConfig))));

    // when
    final var primaryZone = CoordinatorResolver.determinePrimaryZone(config.members());

    // then — "primary" has total = 3+3+2+2 = 10, "secondary" has total = 1+1+1+1 = 4
    assertThat(primaryZone).contains("primary");
  }

  @Test
  void shouldUseDeterministicTieBreaking() {
    // given — both zones have equal total priorities
    final var config =
        ClusterConfiguration.init()
            .addMember(
                MemberId.from("alpha", 0),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(3, partitionConfig))))
            .addMember(
                MemberId.from("beta", 0),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(3, partitionConfig))));

    // when
    final var coordinator = CoordinatorResolver.resolveCoordinator(config);

    // then — on tie, alphabetically first zone wins
    assertThat(coordinator).contains(MemberId.from("alpha", 0));
  }

  @Test
  void shouldIgnoreLeftMembersWhenDeterminingPrimaryZone() {
    // given — a LEFT member in "primary" zone has high priority, but only active members count
    final var config =
        ClusterConfiguration.init()
            .addMember(
                MemberId.from("primary", 0),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(1, partitionConfig))))
            .addMember(
                MemberId.from("secondary", 0),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(3, partitionConfig))))
            .updateMember(MemberId.from("primary", 0), m -> m.toLeaving().toLeft());

    // when
    final var coordinator = CoordinatorResolver.resolveCoordinator(config);

    // then — primary/0 is LEFT so secondary is the primary zone
    assertThat(coordinator).contains(MemberId.from("secondary", 0));
  }

  @Test
  void shouldResolveCoordinatorFromMemberCollection() {
    // given
    final var members =
        List.of(
            MemberId.from("primary", 0),
            MemberId.from("primary", 1),
            MemberId.from("secondary", 0));

    // when — without priority info, picks nodeId 0 from alphabetically first zone
    final var coordinator = CoordinatorResolver.resolveCoordinatorFrom(members);

    // then
    assertThat(coordinator).contains(MemberId.from("primary", 0));
  }

  @Test
  void shouldResolveCoordinatorFromNonZoneMemberCollection() {
    // given
    final var members = List.of(MemberId.from("0"), MemberId.from("1"), MemberId.from("2"));

    // when
    final var coordinator = CoordinatorResolver.resolveCoordinatorFrom(members);

    // then
    assertThat(coordinator).contains(MemberId.from("0"));
  }

  @Test
  void shouldReturnEmptyForEmptyMemberCollection() {
    // when
    final var coordinator = CoordinatorResolver.resolveCoordinatorFrom(List.of());

    // then
    assertThat(coordinator).isEqualTo(Optional.empty());
  }

  @Test
  void shouldHandleMultiplePartitionsAcrossZones() {
    // given — 3 partitions, replication factor 2 across 2 zones
    final var config =
        ClusterConfiguration.init()
            .addMember(
                MemberId.from("east", 0),
                MemberState.initializeAsActive(
                    Map.of(
                        1, PartitionState.active(2, partitionConfig),
                        2, PartitionState.active(1, partitionConfig),
                        3, PartitionState.active(2, partitionConfig))))
            .addMember(
                MemberId.from("east", 1),
                MemberState.initializeAsActive(
                    Map.of(
                        1, PartitionState.active(1, partitionConfig),
                        2, PartitionState.active(2, partitionConfig),
                        3, PartitionState.active(1, partitionConfig))))
            .addMember(
                MemberId.from("west", 0),
                MemberState.initializeAsActive(
                    Map.of(
                        1, PartitionState.active(1, partitionConfig),
                        2, PartitionState.active(1, partitionConfig),
                        3, PartitionState.active(1, partitionConfig))))
            .addMember(
                MemberId.from("west", 1),
                MemberState.initializeAsActive(
                    Map.of(
                        1, PartitionState.active(1, partitionConfig),
                        2, PartitionState.active(1, partitionConfig),
                        3, PartitionState.active(1, partitionConfig))));

    // when
    final var coordinator = CoordinatorResolver.resolveCoordinator(config);

    // then — east total = (2+1+2)+(1+2+1) = 9, west total = 3+3 = 6
    assertThat(coordinator).contains(MemberId.from("east", 0));
  }
}
