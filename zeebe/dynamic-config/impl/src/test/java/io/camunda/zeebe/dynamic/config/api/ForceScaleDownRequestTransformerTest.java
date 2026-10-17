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
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidRequest;
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.BrokerState;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberRemoveOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionForceReconfigureOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
import io.camunda.zeebe.dynamic.config.state.RoutingState;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ForceScaleDownRequestTransformerTest {
  private final MemberId id0 = MemberId.from("0");
  private final MemberId id1 = MemberId.from("1");
  private final MemberId id2 = MemberId.from("2");
  private final MemberId id3 = MemberId.from("3");

  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();

  private final CurrentClusterConfiguration currentTopology =
      CurrentClusterConfiguration.init()
          .updateGlobalConfiguration(
              globalConfiguration ->
                  globalConfiguration
                      .addMember(id0, BrokerState.initializeAsActive())
                      .addMember(id1, BrokerState.initializeAsActive())
                      .addMember(id2, BrokerState.initializeAsActive())
                      .addMember(id3, BrokerState.initializeAsActive()))
          .initPartitionGroup(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)
          .updatePartitionGroupConfig(
              PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID,
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

  @Test
  void shouldGenerateForceConfigureOperations() {
    // given
    final var membersToRetain = Set.of(MemberId.from("0"), MemberId.from("2"));
    final var forceConfigureTransformer =
        new ForceScaleDownRequestTransformer(membersToRetain, id0);

    // when
    final var result = plannedOperations(forceConfigureTransformer, currentTopology);

    // then
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get())
        .hasSize(4)
        .containsExactlyInAnyOrder(
            new PartitionForceReconfigureOperation(id0, 1, Set.of(id0)),
            new PartitionForceReconfigureOperation(id2, 2, Set.of(id2)),
            new MemberRemoveOperation(id0, id1),
            new MemberRemoveOperation(id0, id3));
  }

  @Test
  void shouldFailWhenRetainingNonExistingMember() {
    // given
    final var membersToRetain = Set.of(MemberId.from("0"), MemberId.from("4"));
    final var forceConfigureTransformer =
        new ForceScaleDownRequestTransformer(membersToRetain, id0);

    // when
    final var result = plannedOperations(forceConfigureTransformer, currentTopology);

    // then
    EitherAssert.assertThat(result).isLeft();
    assertThat(result.getLeft()).isInstanceOf(InvalidRequest.class);
  }

  @Test
  void shouldFailWhenRetainingMemberWithNoPartitions() {
    // given
    final var membersToRetain = Set.of(MemberId.from("0"), MemberId.from("1"));
    final var forceConfigureTransformer =
        new ForceScaleDownRequestTransformer(membersToRetain, id0);

    // when
    final var result = plannedOperations(forceConfigureTransformer, currentTopology);

    // then
    EitherAssert.assertThat(result).isLeft();
    assertThat(result.getLeft()).isInstanceOf(InvalidRequest.class);
  }

  @Nested
  class Phases {

    /**
     * The brokers are gone for every tenant at once, so every tenant's partitions have to be handed
     * to the brokers that survive. A tenant left out keeps replicas on brokers that no longer exist
     * — and the member removal that follows refuses the whole plan over it.
     */
    @Test
    void shouldForceReconfigureEveryPhysicalTenantsPartitions() {
      // given
      final var configuration = withMirroredTenant(currentTopology);

      // when — brokers 1 and 3 are gone
      final var phases =
          new ForceScaleDownRequestTransformer(Set.of(id0, id2), id0).phases(configuration);

      // then
      EitherAssert.assertThat(phases).isRight();
      assertThat(partitionGroupPhase(phases.get()).groupOperations())
          .containsOnlyKeys(CurrentClusterConfiguration.DEFAULT_GROUP, TENANT_A)
          .allSatisfy(
              (physicalTenantId, operations) ->
                  assertThat(operations)
                      .describedAs("partitions of physical tenant '%s'", physicalTenantId)
                      .containsExactly(
                          new PartitionForceReconfigureOperation(id0, 1, Set.of(id0)),
                          new PartitionForceReconfigureOperation(id2, 2, Set.of(id2))));
    }

    /**
     * A broker may only leave the member set once nothing replicates on it any more, so the removal
     * is a phase of its own after the one that reconfigures every tenant.
     */
    @Test
    void shouldRemoveTheBrokersOnlyAfterEveryTenantIsReconfigured() {
      // given
      final var configuration = withMirroredTenant(currentTopology);

      // when
      final var phases =
          new ForceScaleDownRequestTransformer(Set.of(id0, id2), id0).phases(configuration);

      // then
      EitherAssert.assertThat(phases).isRight();
      assertThat(phases.get()).hasSize(2);
      assertThat(((GlobalPhase) phases.get().getLast()).operations())
          .containsExactly(
              new MemberRemoveOperation(id0, id1), new MemberRemoveOperation(id0, id3));
    }

    /**
     * Rejecting is what the default tenant's path already does, and the recoverable answer of the
     * two: the operator can retain one more broker and ask again, where a removal that proceeded
     * cannot be undone. The tenants that would lose a partition are named, because the request that
     * triggers this does not mention them — it names brokers.
     */
    @Test
    void shouldRejectWhenAnyPhysicalTenantsPartitionWouldLoseEveryReplica() {
      // given — the default tenant's partition 1 survives on broker 0, tenant A's does not survive
      // at all
      final var configuration = twoTenants(partitionOne(id0, id1), partitionOne(id1));

      // when — broker 1 is gone
      final var phases =
          new ForceScaleDownRequestTransformer(Set.of(id0, id2, id3), id0).phases(configuration);

      // then
      EitherAssert.assertThat(phases)
          .isLeft()
          .left()
          .isInstanceOf(InvalidRequest.class)
          .satisfies(
              error ->
                  assertThat(error)
                      .hasMessageContaining("having no replicas")
                      .hasMessageContaining("{%s=[1]}".formatted(TENANT_A)));
    }

    /**
     * A partition group of the four members, where only the given brokers replicate partition 1.
     */
    private PartitionGroupConfiguration partitionOne(final MemberId... replicas) {
      var group = PartitionGroupConfiguration.empty(PartitionGroupConfiguration.INITIAL_VERSION);
      var priority = 1;
      for (final var replica : replicas) {
        final var partition = PartitionState.active(priority++, partitionConfig);
        group = group.addMember(replica, BrokerPartitionState.initialize(Map.of(1, partition)));
      }
      return group.setRoutingState(RoutingState.initializeWithPartitionCount(1));
    }

    /**
     * Two physical tenants whose partitions are placed differently, unlike the mirrored pair the
     * other tests use: only a tenant that can lose a partition the other one keeps shows that each
     * tenant is judged on its own replicas.
     */
    private CurrentClusterConfiguration twoTenants(
        final PartitionGroupConfiguration defaultTenant,
        final PartitionGroupConfiguration otherTenant) {
      return CurrentClusterConfiguration.init()
          .updateGlobalConfiguration(
              globalConfiguration ->
                  globalConfiguration
                      .addMember(id0, BrokerState.initializeAsActive())
                      .addMember(id1, BrokerState.initializeAsActive())
                      .addMember(id2, BrokerState.initializeAsActive())
                      .addMember(id3, BrokerState.initializeAsActive()))
          .initPartitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP)
          .updatePartitionGroupConfig(
              CurrentClusterConfiguration.DEFAULT_GROUP, ignored -> defaultTenant)
          .initPartitionGroup(TENANT_A)
          .updatePartitionGroupConfig(TENANT_A, ignored -> otherTenant);
    }
  }
}
