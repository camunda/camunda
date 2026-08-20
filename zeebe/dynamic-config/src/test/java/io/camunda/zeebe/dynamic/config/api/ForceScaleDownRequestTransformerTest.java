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
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidRequest;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberRemoveOperation;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionForceReconfigureOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
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

  private final ClusterConfiguration currentTopology =
      ClusterConfiguration.init()
          .addMember(id0, MemberState.initializeAsActive(Map.of()))
          .addMember(id1, MemberState.initializeAsActive(Map.of()))
          .addMember(id2, MemberState.initializeAsActive(Map.of()))
          .addMember(id3, MemberState.initializeAsActive(Map.of()))
          .updateMember(id0, m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
          .updateMember(id1, m -> m.addPartition(1, PartitionState.active(2, partitionConfig)))
          .updateMember(id2, m -> m.addPartition(2, PartitionState.active(1, partitionConfig)))
          .updateMember(id3, m -> m.addPartition(2, PartitionState.active(2, partitionConfig)));

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

    /** A cluster of the four members, where only the given brokers replicate partition 1. */
    private ClusterConfiguration partitionOne(final MemberId... replicas) {
      var topology = ClusterConfiguration.init();
      for (final var member : Set.of(id0, id1, id2, id3)) {
        topology = topology.addMember(member, MemberState.initializeAsActive(Map.of()));
      }
      var priority = 1;
      for (final var replica : replicas) {
        final var partition = PartitionState.active(priority++, partitionConfig);
        topology = topology.updateMember(replica, member -> member.addPartition(1, partition));
      }
      return topology;
    }

    /**
     * Two physical tenants whose partitions are placed differently, unlike the mirrored pair the
     * other tests use: only a tenant that can lose a partition the other one keeps shows that each
     * tenant is judged on its own replicas.
     */
    private CurrentClusterConfiguration twoTenants(
        final ClusterConfiguration defaultTenant, final ClusterConfiguration otherTenant) {
      final var defaultGroup = CurrentClusterConfiguration.DEFAULT_GROUP;
      final var base = CurrentClusterConfiguration.fromLegacy(defaultTenant);
      return new CurrentClusterConfiguration(
          base.version(),
          base.globalConfiguration(),
          Map.of(
              defaultGroup,
              base.partitionGroup(defaultGroup),
              TENANT_A,
              CurrentClusterConfiguration.fromLegacy(otherTenant).partitionGroup(defaultGroup)),
          base.phasedChangeState());
    }
  }
}
