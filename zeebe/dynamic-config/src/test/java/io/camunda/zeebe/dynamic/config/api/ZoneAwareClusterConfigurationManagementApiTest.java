/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import static io.camunda.zeebe.dynamic.config.util.ZoneFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.AddZoneRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ForceZoneRemoveRequest;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberRemoveOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.UpdatePartitionDistributorConfigOperation;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneAwareConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneSpec;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionForceReconfigureOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.util.ZoneFixtures;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Covers {@code forceRemoveZone} and {@code addZone}, which only make sense for an already
 * zone-aware cluster. The coordinator's physical id is {@link ZoneFixtures.ZONE_A_0} so that a
 * fully zone-aware topology (whose lowest member is always zone-a's first broker) routes correctly
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
    recordingCoordinator.setCurrentTopology(currentTopology);
    final var request = new ForceZoneRemoveRequest(ZONE_A, false);

    // when
    final var changeStatus = clientApi.forceRemoveZone(request).join().get();

    // then
    assertThat(changeStatus.plannedChanges())
        .containsExactlyInAnyOrder(
            new PartitionForceReconfigureOperation(ZONE_B_0, 1, Set.of(ZONE_B_0)),
            new PartitionForceReconfigureOperation(ZONE_B_1, 2, Set.of(ZONE_B_1)),
            new MemberRemoveOperation(ZONE_B_0, ZONE_A_0),
            new MemberRemoveOperation(ZONE_B_0, ZONE_A_1),
            new UpdatePartitionDistributorConfigOperation(
                ZONE_B_0, new ZoneAwareConfig(List.of(new ZoneSpec(ZONE_B, 2, 100)))));
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
    recordingCoordinator.setCurrentTopology(currentTopology);
    final var request = new AddZoneRequest(ZONE_B, 1, 2, Set.of(ZONE_B_0), false);

    // when
    final var changeStatus = clientApi.addZone(request).join().get();

    // then
    assertThat(changeStatus.plannedChanges())
        .containsExactly(
            new MemberJoinOperation(ZONE_B_0),
            new UpdatePartitionDistributorConfigOperation(
                ZONE_A_0,
                new ZoneAwareConfig(
                    List.of(new ZoneSpec(ZONE_A, 1, 1), new ZoneSpec(ZONE_B, 1, 2)))),
            new PartitionJoinOperation(ZONE_B_0, 1, 2),
            new PartitionLeaveOperation(ZONE_A_1, 1, 1));
  }
}
