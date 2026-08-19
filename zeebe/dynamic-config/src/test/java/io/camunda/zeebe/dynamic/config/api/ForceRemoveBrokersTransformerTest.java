/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import static io.camunda.zeebe.dynamic.config.api.TestChangePlan.plannedOperations;
import static io.camunda.zeebe.dynamic.config.util.PhysicalTenantFixtures.TENANT_A;
import static io.camunda.zeebe.dynamic.config.util.PhysicalTenantFixtures.partitionGroupPhase;
import static io.camunda.zeebe.dynamic.config.util.PhysicalTenantFixtures.withMirroredTenant;
import static io.camunda.zeebe.test.util.asserts.EitherAssert.assertThat;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ForceRemoveBrokersRequest;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionForceReconfigureOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.util.ConfigurationUtil;
import java.util.Map;
import java.util.Set;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

final class ForceRemoveBrokersTransformerTest {

  private final MemberId id0 = MemberId.from("0");
  private final MemberId id1 = MemberId.from("1");
  private final MemberId id2 = MemberId.from("2");
  private final MemberId id3 = MemberId.from("3");

  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();

  @Test
  void shouldForceRemoveBrokers() {
    // given
    final ClusterConfiguration currentTopology =
        ClusterConfiguration.init()
            .addMember(id0, MemberState.initializeAsActive(Map.of()))
            .addMember(id1, MemberState.initializeAsActive(Map.of()))
            .addMember(id2, MemberState.initializeAsActive(Map.of()))
            .addMember(id3, MemberState.initializeAsActive(Map.of()))
            .updateMember(id0, m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .updateMember(id1, m -> m.addPartition(1, PartitionState.active(2, partitionConfig)))
            .updateMember(id2, m -> m.addPartition(2, PartitionState.active(1, partitionConfig)))
            .updateMember(id3, m -> m.addPartition(2, PartitionState.active(2, partitionConfig)));

    // when
    final var patchRequest = new ForceRemoveBrokersRequest(Set.of(id1, id3), false);

    // remove members 1 and 3
    final var expectedTopology =
        currentTopology.updateMember(id1, ignore -> null).updateMember(id3, ignore -> null);
    final var expectedDistribution =
        ConfigurationUtil.getPartitionDistributionFrom(expectedTopology, "temp");

    // then
    applyRequestAndVerifyResultingTopology(
        2, Set.of(id0, id2), patchRequest, currentTopology, expectedDistribution);
  }

  /**
   * The request names the brokers that are gone, so the brokers to keep are everything else — and
   * every physical tenant's partitions have to be handed to them, not only the default tenant's.
   */
  @Test
  void shouldForceRemoveBrokersFromEveryPhysicalTenant() {
    // given
    final var configuration =
        withMirroredTenant(
            ClusterConfiguration.init()
                .addMember(id0, MemberState.initializeAsActive(Map.of()))
                .addMember(id1, MemberState.initializeAsActive(Map.of()))
                .updateMember(
                    id0, m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
                .updateMember(
                    id1, m -> m.addPartition(1, PartitionState.active(2, partitionConfig))));

    // when — broker 1 is gone
    final var phases =
        new ForceRemoveBrokersRequestTransformer(Set.of(id1), id0).phases(configuration);

    // then
    assertThat(phases).isRight();
    Assertions.assertThat(partitionGroupPhase(phases.get()).groupOperations())
        .containsOnlyKeys(CurrentClusterConfiguration.DEFAULT_GROUP, TENANT_A)
        .allSatisfy(
            (physicalTenantId, operations) ->
                Assertions.assertThat(operations)
                    .describedAs("partitions of physical tenant '%s'", physicalTenantId)
                    .containsExactly(new PartitionForceReconfigureOperation(id0, 1, Set.of(id0))));
  }

  private void applyRequestAndVerifyResultingTopology(
      final int partitionCount,
      final Set<MemberId> expectedMembers,
      final ForceRemoveBrokersRequest patchRequest,
      final ClusterConfiguration oldClusterTopology,
      final Set<PartitionMetadata> expectedNewDistribution) {

    // when
    final var result =
        plannedOperations(
            new ForceRemoveBrokersRequestTransformer(patchRequest.membersToRemove(), id0),
            oldClusterTopology);
    assertThat(result).isRight();
    final var operations = result.get();

    // apply operations to generate new topology
    final ClusterConfiguration newTopology =
        TestTopologyChangeSimulator.apply(oldClusterTopology, operations);

    // then
    final var newDistribution = ConfigurationUtil.getPartitionDistributionFrom(newTopology, "temp");
    Assertions.assertThat(newDistribution)
        .usingRecursiveComparison()
        .ignoringCollectionOrder()
        .isEqualTo(expectedNewDistribution);
    Assertions.assertThat(newTopology.members().keySet())
        .describedAs("Expected cluster members")
        .containsExactlyInAnyOrderElementsOf(expectedMembers);
    Assertions.assertThat(newTopology.partitionCount()).isEqualTo(partitionCount);
  }
}
