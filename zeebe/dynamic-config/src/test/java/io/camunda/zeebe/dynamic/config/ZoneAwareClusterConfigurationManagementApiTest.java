/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import static io.camunda.zeebe.dynamic.config.util.ZoneFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.AddZoneRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RemoveZoneRequest;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse.ErrorCode;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberRemoveOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.PostScalingOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.PreScalingOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.UpdatePartitionDistributorConfigOperation;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneAwareConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneSpec;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionDemoteOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionForceReconfigureOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionPromoteOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.util.ZoneFixtures;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Covers {@code removeZone} and {@code addZone}, which only make sense for an already zone-aware
 * cluster. The coordinator's physical id is {@link ZoneFixtures.ZONE_A_0} so that a fully
 * zone-aware topology (whose lowest member is always zone-a's first broker) routes correctly
 * through the real {@code communicationService}.
 */
final class ZoneAwareClusterConfigurationManagementApiTest
    extends ClusterConfigurationManagementApiTestBase {

  ZoneAwareClusterConfigurationManagementApiTest() {
    super(idx -> MemberId.from(ZONE_A, idx));
  }

  @Override
  protected List<MemberId> extraPhysicalMembers() {
    // shouldForceRemoveZone removes zone-a, so the coordinator resolved at request time is
    // zone-b_0 (lowest member outside the removed zone), not the physical coordinator node
    // (zone-a_0); start it so communicationService can route to it.
    return List.of(ZONE_B_0);
  }

  @Override
  @Test
  void shouldForceRemoveZone() {
    // given
    final var currentTopology =
        ClusterConfiguration.init()
            .addMember(ZONE_A_0, MemberState.initializeAsActive(Map.of()))
            .addMember(ZONE_A_1, MemberState.initializeAsActive(Map.of()))
            .addMember(ZONE_B_0, MemberState.initializeAsActive(Map.of()))
            .addMember(ZONE_B_1, MemberState.initializeAsActive(Map.of()))
            .updateMember(
                ZONE_B_0, m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .updateMember(
                ZONE_A_0, m -> m.addPartition(1, PartitionState.active(2, partitionConfig)))
            .updateMember(
                ZONE_B_1, m -> m.addPartition(2, PartitionState.active(1, partitionConfig)))
            .updateMember(
                ZONE_A_1, m -> m.addPartition(2, PartitionState.active(2, partitionConfig)))
            .setPartitionDistributorConfig(new ZoneAwareConfig(DUAL_REGION));
    setCurrentTopology(currentTopology);
    final var request = new RemoveZoneRequest(ZONE_A, false, true);

    // when
    final var changeStatus = clientApi.removeZone(request).join().get();

    // then
    assertThat(changeStatus.legacyResponse().plannedChanges())
        .containsExactlyInAnyOrder(
            new PartitionForceReconfigureOperation(ZONE_B_0, 1, Set.of(ZONE_B_0)),
            new PartitionForceReconfigureOperation(ZONE_B_1, 2, Set.of(ZONE_B_1)),
            new MemberRemoveOperation(ZONE_B_0, ZONE_A_0),
            new MemberRemoveOperation(ZONE_B_0, ZONE_A_1),
            new UpdatePartitionDistributorConfigOperation(
                ZONE_B_0, new ZoneAwareConfig(List.of(new ZoneSpec(ZONE_B, 2, 100)))));
  }

  /**
   * The graceful counterpart of the test above, through the same request pipeline: the force flag
   * has to survive serialization and reach the transformer, or an operator who did not ask for a
   * forced removal silently gets one. Targets zone-b rather than zone-a so that the elected
   * coordinator (zone-a_0) survives the removal — see {@link
   * #shouldRejectGracefulRemovalOfTheZoneHoldingTheElectedCoordinator()} for that case.
   */
  @Test
  void shouldRemoveZoneGracefullyWhenForceIsNotRequested() {
    // given
    final var currentTopology =
        ClusterConfiguration.init()
            .addMember(ZONE_A_0, MemberState.initializeAsActive(Map.of()))
            .addMember(ZONE_A_1, MemberState.initializeAsActive(Map.of()))
            .addMember(ZONE_B_0, MemberState.initializeAsActive(Map.of()))
            .addMember(ZONE_B_1, MemberState.initializeAsActive(Map.of()))
            .updateMember(
                ZONE_B_0, m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .updateMember(
                ZONE_A_0, m -> m.addPartition(1, PartitionState.active(2, partitionConfig)))
            .updateMember(
                ZONE_B_1, m -> m.addPartition(2, PartitionState.active(1, partitionConfig)))
            .updateMember(
                ZONE_A_1, m -> m.addPartition(2, PartitionState.active(2, partitionConfig)))
            .setPartitionDistributorConfig(new ZoneAwareConfig(DUAL_REGION));
    setCurrentTopology(currentTopology);
    final var request = new RemoveZoneRequest(ZONE_B, false, false);

    // when
    final var changeStatus = clientApi.removeZone(request).join().get();

    // then: the zone's replicas are handed to zone-a before its brokers leave, rather than being
    // written off by a force-reconfigure
    assertThat(changeStatus.legacyResponse().plannedChanges())
        .containsExactly(
            new UpdatePartitionDistributorConfigOperation(
                ZONE_A_0, new ZoneAwareConfig(List.of(new ZoneSpec(ZONE_A, 2, 100)))),
            new PreScalingOperation(ZONE_A_0, Set.of(ZONE_A_0, ZONE_A_1)),
            new PartitionJoinOperation(ZONE_A_1, 1, 1, true),
            new PartitionPromoteOperation(ZONE_A_1, 1),
            new PartitionDemoteOperation(ZONE_B_0, 1),
            new PartitionLeaveOperation(ZONE_B_0, 1, 1),
            new PartitionJoinOperation(ZONE_A_0, 2, 1, true),
            new PartitionPromoteOperation(ZONE_A_0, 2),
            new PartitionDemoteOperation(ZONE_B_1, 2),
            new PartitionLeaveOperation(ZONE_B_1, 2, 1),
            new MemberLeaveOperation(ZONE_B_0),
            new MemberLeaveOperation(ZONE_B_1),
            new PostScalingOperation(ZONE_A_0, Set.of(ZONE_A_0, ZONE_A_1)));
  }

  /**
   * The elected coordinator (lowest member id, zone-a_0) sits inside the zone being removed here.
   * Rather than let the coordinator remove itself as the last step of the plan — a path with no
   * live-cluster coverage — the request is rejected up front and the operator is told to accept the
   * forced-removal trade-offs instead.
   */
  @Test
  void shouldRejectGracefulRemovalOfTheZoneHoldingTheElectedCoordinator() {
    // given
    final var currentTopology =
        ClusterConfiguration.init()
            .addMember(ZONE_A_0, MemberState.initializeAsActive(Map.of()))
            .addMember(ZONE_A_1, MemberState.initializeAsActive(Map.of()))
            .addMember(ZONE_B_0, MemberState.initializeAsActive(Map.of()))
            .addMember(ZONE_B_1, MemberState.initializeAsActive(Map.of()))
            .updateMember(
                ZONE_B_0, m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .updateMember(
                ZONE_A_0, m -> m.addPartition(1, PartitionState.active(2, partitionConfig)))
            .updateMember(
                ZONE_B_1, m -> m.addPartition(2, PartitionState.active(1, partitionConfig)))
            .updateMember(
                ZONE_A_1, m -> m.addPartition(2, PartitionState.active(2, partitionConfig)))
            .setPartitionDistributorConfig(new ZoneAwareConfig(DUAL_REGION));
    setCurrentTopology(currentTopology);
    final var request = new RemoveZoneRequest(ZONE_A, false, false);

    // when
    final var changeStatus = clientApi.removeZone(request).join();

    // then
    EitherAssert.assertThat(changeStatus)
        .isLeft()
        .left()
        .satisfies(
            error -> {
              assertThat(error.code()).isEqualTo(ErrorCode.INVALID_REQUEST);
              assertThat(error.message()).contains("elected coordinator").contains("force=true");
            });
  }

  @Override
  @Test
  void shouldAddZone() {
    // given
    final var currentTopology =
        ClusterConfiguration.init()
            .addMember(ZONE_A_0, MemberState.initializeAsActive(Map.of()))
            .addMember(ZONE_A_1, MemberState.initializeAsActive(Map.of()))
            .updateMember(
                ZONE_A_0, m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .updateMember(
                ZONE_A_1, m -> m.addPartition(1, PartitionState.active(2, partitionConfig)))
            .setPartitionDistributorConfig(
                new ZoneAwareConfig(List.of(new ZoneSpec(ZONE_A, 1, 1))));
    setCurrentTopology(currentTopology);
    final var request = new AddZoneRequest(ZONE_B, 1, 2, Set.of(ZONE_B_0), false);

    // when
    final var changeStatus = clientApi.addZone(request).join().get();

    // then
    assertThat(changeStatus.legacyResponse().plannedChanges())
        .containsExactly(
            new MemberJoinOperation(ZONE_B_0),
            new UpdatePartitionDistributorConfigOperation(
                ZONE_A_0,
                new ZoneAwareConfig(
                    List.of(new ZoneSpec(ZONE_A, 1, 1), new ZoneSpec(ZONE_B, 1, 2)))),
            new PartitionJoinOperation(ZONE_B_0, 1, 2, true),
            new PartitionPromoteOperation(ZONE_B_0, 1),
            new PartitionDemoteOperation(ZONE_A_1, 1),
            new PartitionLeaveOperation(ZONE_A_1, 1, 1));
  }
}
