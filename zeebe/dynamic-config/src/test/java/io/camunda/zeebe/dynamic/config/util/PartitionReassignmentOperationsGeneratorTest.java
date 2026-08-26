/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExporterState;
import io.camunda.zeebe.dynamic.config.state.ExportingConfig;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionBootstrapOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionDemoteOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionPromoteOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionReconfigurePriorityOperation;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class PartitionReassignmentOperationsGeneratorTest {

  private final DynamicPartitionConfig partitionConfig =
      new DynamicPartitionConfig(
          new ExportingConfig(
              ExportingState.EXPORTING,
              Map.of("expA", new ExporterState(1, ExporterState.State.ENABLED, Optional.empty()))));

  /**
   * A distinct config to prove a brand-new group's bootstrap carries its OWN config, not this one —
   * the field above is only baked into the (unrelated) existing-partition state.
   */
  private final DynamicPartitionConfig newTenantConfig =
      new DynamicPartitionConfig(
          new ExportingConfig(
              ExportingState.EXPORTING,
              Map.of("expB", new ExporterState(1, ExporterState.State.ENABLED, Optional.empty()))));

  @Test
  void shouldBootstrapABrandNewGroupsFirstPartitionWithItsConfigAndNoSnapshot() {
    // given — no existing groups at all
    final var currentConfiguration = configurationWith(Set.of());
    final var target =
        Set.of(
            PartitionMetadataFixtures.partition(
                "tenantX", 1, Set.of(member(0), member(1)), member(0)),
            PartitionMetadataFixtures.partition(
                "tenantX", 2, Set.of(member(1), member(2)), member(1)));

    // when
    final var operations =
        PartitionReassignmentOperationsGenerator.generateOperations(
            currentConfiguration, target, Map.of("tenantX", newTenantConfig));

    // then — partition 1 (the group's lowest id) carries the tenant's config and does NOT
    // initialize from a snapshot; partition 2 inherits the config (empty here) and also skips the
    // snapshot, since the whole group is brand new
    assertThat(operations).containsOnlyKeys("tenantX");
    assertThat(operations.get("tenantX"))
        .containsExactly(
            new PartitionBootstrapOperation(member(0), 1, 2, Optional.of(newTenantConfig), false),
            new PartitionJoinOperation(member(1), 1, 1),
            new PartitionPromoteOperation(member(1), 1),
            new PartitionBootstrapOperation(member(1), 2, 2, Optional.empty(), false),
            new PartitionJoinOperation(member(2), 2, 1),
            new PartitionPromoteOperation(member(2), 2));
  }

  @Test
  void shouldRejectABrandNewGroupWithNoConfigSupplied() {
    // given
    final var currentConfiguration = configurationWith(Set.of());
    final var target =
        Set.of(PartitionMetadataFixtures.partition("tenantX", 1, Set.of(member(0)), member(0)));

    // when/then
    assertThatThrownBy(
            () ->
                PartitionReassignmentOperationsGenerator.generateOperations(
                    currentConfiguration, target, Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldBootstrapANewPartitionInAnExistingGroupWithSnapshotAndNoExplicitConfig() {
    // given — tenantA already has partition 1; partition 2 is new, but the GROUP already exists
    final var existingPartition1 =
        PartitionMetadataFixtures.partition("tenantA", 1, Set.of(member(0), member(1)), member(0));
    final var currentConfiguration = configurationWith(Set.of(existingPartition1));
    final var target =
        Set.of(
            existingPartition1, // unchanged, passed through as-is by a PartitionReassigner
            PartitionMetadataFixtures.partition("tenantA", 2, Set.of(member(2)), member(2)));

    // when — no config needed since tenantA isn't a brand-new group
    final var operations =
        PartitionReassignmentOperationsGenerator.generateOperations(
            currentConfiguration, target, Map.of());

    // then — only partition 2 produces operations; it initializes from a snapshot (scaling up an
    // existing group) and inherits its config from the group's existing partition 1
    assertThat(operations).containsOnlyKeys("tenantA");
    assertThat(operations.get("tenantA"))
        .containsExactly(new PartitionBootstrapOperation(member(2), 2, 1, Optional.empty(), true));
  }

  @Test
  void shouldProduceNoOperationsForAnUnchangedPartition() {
    // given
    final var existingPartition =
        PartitionMetadataFixtures.partition("tenantA", 1, Set.of(member(0), member(1)), member(0));
    final var currentConfiguration = configurationWith(Set.of(existingPartition));

    // when — target is exactly the current distribution
    final var operations =
        PartitionReassignmentOperationsGenerator.generateOperations(
            currentConfiguration, Set.of(existingPartition), Map.of());

    // then
    assertThat(operations).isEmpty();
  }

  @Test
  void shouldGenerateJoinLeaveAndPriorityReconfigureOperationsForAMovedPartition() {
    // given — partition 1: member 1 leaves, member 3 joins, member 2's priority changes
    final var current =
        new PartitionMetadata(
            new PartitionId("tenantA", 1),
            Set.of(member(0), member(1), member(2)),
            Map.of(member(0), 3, member(1), 2, member(2), 1),
            3,
            member(0));
    final var target =
        new PartitionMetadata(
            new PartitionId("tenantA", 1),
            Set.of(member(0), member(2), member(3)),
            Map.of(member(0), 3, member(2), 2, member(3), 1),
            3,
            member(0));
    final var currentConfiguration = configurationWith(Set.of(current));

    // when
    final var operations =
        PartitionReassignmentOperationsGenerator.generateOperations(
            currentConfiguration, Set.of(target), Map.of());

    // then — joins (with their promotions) first, then demotions and leaves, then priority
    // reconfigures
    assertThat(operations).containsOnlyKeys("tenantA");
    assertThat(operations.get("tenantA"))
        .containsExactly(
            new PartitionJoinOperation(member(3), 1, 1),
            new PartitionPromoteOperation(member(3), 1),
            new PartitionDemoteOperation(member(1), 1),
            new PartitionLeaveOperation(member(1), 1, 1),
            new PartitionReconfigurePriorityOperation(member(2), 1, 2));
  }

  @Test
  void shouldGroupOperationsByPartitionGroupAcrossMultipleGroupsInOneCall() {
    // given — tenantA has an unchanged partition and a moved one; tenantB is brand new. Member 0's
    // priority is deliberately kept identical (1) between old and new partition 2, so only the join
    // of member 1 is expected — no incidental priority reconfigure for member 0.
    final var unchangedTenantA =
        PartitionMetadataFixtures.partition("tenantA", 1, Set.of(member(0), member(1)), member(0));
    final var oldTenantAPartition2 =
        new PartitionMetadata(
            new PartitionId("tenantA", 2), Set.of(member(0)), Map.of(member(0), 1), 1, member(0));
    final var newTenantAPartition2 =
        new PartitionMetadata(
            new PartitionId("tenantA", 2),
            Set.of(member(0), member(1)),
            Map.of(member(0), 1, member(1), 1),
            1,
            member(0));
    final Set<PartitionMetadata> existing = new HashSet<>();
    existing.add(unchangedTenantA);
    existing.add(oldTenantAPartition2);
    final var currentConfiguration = configurationWith(existing);

    final var tenantBPartition1 =
        PartitionMetadataFixtures.partition("tenantB", 1, Set.of(member(2)), member(2));
    final Set<PartitionMetadata> target =
        Set.of(unchangedTenantA, newTenantAPartition2, tenantBPartition1);

    // when — only tenantB is brand new, so only it needs an entry in the config map
    final var operations =
        PartitionReassignmentOperationsGenerator.generateOperations(
            currentConfiguration, target, Map.of("tenantB", newTenantConfig));

    // then
    assertThat(operations).containsOnlyKeys("tenantA", "tenantB");
    assertThat(operations.get("tenantA"))
        .containsExactly(
            new PartitionJoinOperation(member(1), 2, 1),
            new PartitionPromoteOperation(member(1), 2));
    assertThat(operations.get("tenantB"))
        .containsExactly(
            new PartitionBootstrapOperation(member(2), 1, 1, Optional.of(newTenantConfig), false));
  }

  @Test
  void shouldRejectATargetDistributionThatOmitsAnExistingPartitionOfATouchedGroup() {
    // given — tenantA already has partitions 1 and 2
    final var existingPartition1 =
        PartitionMetadataFixtures.partition("tenantA", 1, Set.of(member(0), member(1)), member(0));
    final var existingPartition2 =
        PartitionMetadataFixtures.partition("tenantA", 2, Set.of(member(1), member(2)), member(1));
    final var currentConfiguration =
        configurationWith(Set.of(existingPartition1, existingPartition2));
    // target only mentions partition 1 — partition 2 is silently omitted
    final var target = Set.of(existingPartition1);

    // when/then
    assertThatThrownBy(
            () ->
                PartitionReassignmentOperationsGenerator.generateOperations(
                    currentConfiguration, target, Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldNotRejectAnExistingGroupThatIsEntirelyAbsentFromTheTargetDistribution() {
    // given — tenantA already has a partition, but the target distribution only concerns a
    // brand-new tenantB — tenantA is simply left alone, not "removed"
    final var existingTenantA =
        PartitionMetadataFixtures.partition("tenantA", 1, Set.of(member(0), member(1)), member(0));
    final var currentConfiguration = configurationWith(Set.of(existingTenantA));
    final var target =
        Set.of(PartitionMetadataFixtures.partition("tenantB", 1, Set.of(member(2)), member(2)));

    // when — no exception; tenantA is simply not part of this call at all
    final var operations =
        PartitionReassignmentOperationsGenerator.generateOperations(
            currentConfiguration, target, Map.of("tenantB", newTenantConfig));

    // then
    assertThat(operations).containsOnlyKeys("tenantB");
  }

  @Test
  void shouldReturnEmptyMapForEmptyTargetDistribution() {
    // given
    final var currentConfiguration = configurationWith(Set.of());

    // when
    final var operations =
        PartitionReassignmentOperationsGenerator.generateOperations(
            currentConfiguration, Set.of(), Map.of());

    // then
    assertThat(operations).isEmpty();
  }

  private CurrentClusterConfiguration configurationWith(final Set<PartitionMetadata> existing) {
    final Set<MemberId> members = new HashSet<>();
    existing.forEach(metadata -> members.addAll(metadata.members()));
    for (int i = 0; i < 4; i++) {
      members.add(member(i));
    }

    final var tenantConfig =
        new HashSet<>(existing)
            .stream()
                .map(PartitionMetadata::id)
                .map(PartitionId::group)
                .distinct()
                .collect(Collectors.toMap(group -> group, group -> partitionConfig));
    return ConfigurationUtil.getCurrentClusterConfigurationFrom(
        members, existing, tenantConfig, "clusterId");
  }

  private MemberId member(final int id) {
    return MemberId.from(String.valueOf(id));
  }
}
