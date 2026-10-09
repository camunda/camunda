/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import static io.camunda.zeebe.dynamic.config.util.PhysicalTenantFixtures.TENANT_A;
import static io.camunda.zeebe.dynamic.config.util.PhysicalTenantFixtures.partitionGroupPhase;
import static io.camunda.zeebe.dynamic.config.util.PhysicalTenantFixtures.withMirroredTenant;
import static io.camunda.zeebe.test.util.asserts.EitherAssert.assertThat;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ForceRemoveBrokersRequest;
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.BrokerState;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionForceReconfigureOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.RoutingState;
import io.camunda.zeebe.dynamic.config.util.ConfigurationUtil;
import java.util.Map;
import java.util.Set;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

final class ForceRemoveBrokersTransformerTest {

  private static final String GROUP_NAME = "temp";
  private final MemberId id0 = MemberId.from("0");
  private final MemberId id1 = MemberId.from("1");
  private final MemberId id2 = MemberId.from("2");
  private final MemberId id3 = MemberId.from("3");

  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();

  @Test
  void shouldForceRemoveBrokers() {
    // given
    final var currentTopology =
        CurrentClusterConfiguration.init()
            .updateGlobalConfiguration(
                globalConfiguration ->
                    globalConfiguration
                        .addMember(id0, BrokerState.initializeAsActive())
                        .addMember(id1, BrokerState.initializeAsActive())
                        .addMember(id2, BrokerState.initializeAsActive())
                        .addMember(id3, BrokerState.initializeAsActive()))
            .initPartitionGroup(GROUP_NAME)
            .updatePartitionGroupConfig(
                GROUP_NAME,
                partitionGroupConfiguration ->
                    partitionGroupConfiguration
                        .addMember(
                            id0,
                            BrokerPartitionState.initialize(
                                Map.of(1, PartitionState.active(1, partitionConfig))))
                        .addMember(
                            id1,
                            BrokerPartitionState.initialize(
                                Map.of(1, PartitionState.active(2, partitionConfig))))
                        .addMember(
                            id2,
                            BrokerPartitionState.initialize(
                                Map.of(2, PartitionState.active(1, partitionConfig))))
                        .addMember(
                            id3,
                            BrokerPartitionState.initialize(
                                Map.of(2, PartitionState.active(2, partitionConfig))))
                        .setRoutingState(RoutingState.initializeWithPartitionCount(2)));

    // when
    final var patchRequest = new ForceRemoveBrokersRequest(Set.of(id1, id3), false);

    // remove members 1 and 3
    final var expectedTopology =
        currentTopology
            .updateGlobalConfiguration(
                globalConfiguration -> globalConfiguration.updateMember(id1, ignored -> null))
            .updatePartitionGroupConfig(
                GROUP_NAME,
                partitionGroupConfiguration ->
                    partitionGroupConfiguration
                        .updateMember(id1, ignored -> null)
                        .updateMember(id3, ignored -> null));
    final var expectedDistribution =
        ConfigurationUtil.getPartitionDistributionFrom(expectedTopology, GROUP_NAME);

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
            CurrentClusterConfiguration.init()
                .updateGlobalConfiguration(
                    globalConfiguration ->
                        globalConfiguration
                            .addMember(id0, BrokerState.initializeAsActive())
                            .addMember(id1, BrokerState.initializeAsActive()))
                .initPartitionGroup(GROUP_NAME)
                .updatePartitionGroupConfig(
                    GROUP_NAME,
                    partitionGroupConfiguration ->
                        partitionGroupConfiguration
                            .addMember(
                                id0,
                                BrokerPartitionState.initialize(
                                    Map.of(1, PartitionState.active(1, partitionConfig))))
                            .addMember(
                                id1,
                                BrokerPartitionState.initialize(
                                    Map.of(1, PartitionState.active(2, partitionConfig))))));

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
      final CurrentClusterConfiguration oldClusterTopology,
      final Set<PartitionMetadata> expectedNewDistribution) {

    // when
    final var phasesResult =
        new ForceRemoveBrokersRequestTransformer(patchRequest.membersToRemove(), id0)
            .phases(oldClusterTopology);
    assertThat(phasesResult).isRight();
    final var phases = phasesResult.get();
    final var operations = TestChangePlan.flatten(phases);

    // apply phases to generate new topology
    final var newTopology = TestTopologyChangeSimulator.apply(oldClusterTopology, phases);

    // then
    final var newDistribution =
        ConfigurationUtil.getPartitionDistributionFrom(newTopology, GROUP_NAME);
    Assertions.assertThat(newDistribution)
        .usingRecursiveComparison()
        .ignoringCollectionOrder()
        .isEqualTo(expectedNewDistribution);
    Assertions.assertThat(newTopology.getMembers())
        .describedAs("Expected cluster members")
        .containsExactlyInAnyOrderElementsOf(expectedMembers);
    Assertions.assertThat(newTopology.partitionGroup(GROUP_NAME).partitionCount())
        .isEqualTo(partitionCount);
  }
}
