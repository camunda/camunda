/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import feign.FeignException;
import io.atomix.cluster.MemberId;
import io.camunda.configuration.Zone;
import io.camunda.zeebe.management.cluster.BrokerId;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequest;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequestBrokers;
import io.camunda.zeebe.management.cluster.Operation;
import io.camunda.zeebe.management.cluster.Operation.OperationEnum;
import io.camunda.zeebe.management.cluster.PartitionDistributionConfig;
import io.camunda.zeebe.management.cluster.PartitionDistributionConfig.TypeEnum;
import io.camunda.zeebe.management.cluster.ZoneSpec;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.topology.ClusterActuatorAssert;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

final class ZoneAwareClusterEndpointIT extends ClusterEndpointIT {

  private static final String[] ZONES = {"zoneA", "zoneB"};

  @Override
  protected int brokerCount() {
    return 3;
  }

  @Override
  protected int partitionCount() {
    return 3;
  }

  @Override
  @SuppressWarnings("resource")
  protected TestCluster createCluster(
      final int brokerCount, final int partitionCount, final int replicationFactor) {
    return TestCluster.builder()
        .withEmbeddedGateway(true)
        .withBrokersCount(brokerCount)
        .withPartitionsCount(partitionCount)
        .withReplicationFactor(replicationFactor)
        .multiZone(zoneConfigs(brokerCount, replicationFactor))
        .build()
        .start();
  }

  @Override
  protected int minReplicationFactor() {
    return 2;
  }

  @Override
  protected String zone() {
    return ZONES[0];
  }

  @Override
  protected BrokerId brokerId(final int nodeIdx) {
    return new BrokerId.String(memberIdForBroker(nodeIdx).toString());
  }

  /** 0 -> zoneA_0 1 -> zoneB_0 2 -> zoneA_1 */
  @Override
  protected MemberId memberIdForBroker(final int nodeIdx) {
    return MemberId.from(zoneFor(nodeIdx), nodeIdx / ZONES.length);
  }

  @Override
  protected void assertClusterScaleResponse(
      final ClusterActuator actuator, final ClusterConfigPatchRequest request) {
    assertThatCode(() -> actuator.patchCluster(request, true, false))
        .isInstanceOf(FeignException.BadRequest.class)
        .hasMessageContaining("zone-aware");
  }

  @Override
  protected void assertClusterPatchResponse(
      final ClusterActuator actuator, final ClusterConfigPatchRequest request) {
    assertThatCode(() -> actuator.patchCluster(request, true, false))
        .isInstanceOf(FeignException.BadRequest.class)
        .hasMessageContaining("zone-aware");
  }

  private String zoneFor(final int nodeIdx) {
    return ZONES[nodeIdx % ZONES.length];
  }

  private static List<Zone> zoneConfigs(final int brokerCount, final int replicationFactor) {
    final var replicasZoneB = replicationFactor / 2;
    final var replicasZoneA = replicationFactor - replicasZoneB;
    final var brokersZoneB = brokerCount / ZONES.length;
    final var brokersZoneA = brokerCount - brokersZoneB;
    return List.of(
        new Zone(ZONES[0], brokersZoneA, replicasZoneA, 100),
        new Zone(ZONES[1], brokersZoneB, replicasZoneB, 10));
  }

  @Test
  void shouldRejectBareIntegersWhenScaling() {
    try (final var cluster = createCluster(brokerCount())) {
      // given
      cluster.awaitCompleteTopology();
      final var actuator = ClusterActuator.of(cluster.availableGateway());

      // when - then
      assertThatCode(() -> actuator.scaleBrokers(List.of(0, 1, 2)))
          .isInstanceOf(FeignException.BadRequest.class)
          .hasMessageContaining("bare node ID");
    }
  }

  @Test
  void shouldRejectAddBrokerWithBareInteger() {
    try (final var cluster = createCluster(brokerCount())) {
      // given
      cluster.awaitCompleteTopology();
      final var actuator = ClusterActuator.of(cluster.availableGateway());

      // when - then -- bare integer broker ID is rejected on zone-aware clusters
      assertThatCode(() -> actuator.addBroker(2))
          .isInstanceOf(FeignException.BadRequest.class)
          .hasMessageContaining("bare node ID");
    }
  }

  @Test
  void shouldRejectPartitionJoinOnZoneAwareCluster() {
    try (final var cluster = createCluster(brokerCount())) {
      // given
      cluster.awaitCompleteTopology();
      final var actuator = ClusterActuator.of(cluster.availableGateway());

      // when - then -- partition join is rejected on zone-aware clusters
      assertThatCode(() -> actuator.joinPartition(0, 1, 1))
          .isInstanceOf(FeignException.BadRequest.class)
          .hasMessageContaining("zone-aware");
    }
  }

  @Test
  void shouldRejectPartitionLeaveOnZoneAwareCluster() {
    try (final var cluster = createCluster(brokerCount())) {
      // given
      cluster.awaitCompleteTopology();
      final var actuator = ClusterActuator.of(cluster.availableGateway());

      // when - then -- partition leave is rejected on zone-aware clusters
      assertThatCode(() -> actuator.leavePartition(0, 1))
          .isInstanceOf(FeignException.BadRequest.class)
          .hasMessageContaining("zone-aware");
    }
  }

  @Test
  void shouldUpdatePartitionDistribution() {
    try (final var cluster = createCluster(minReplicationFactor())) {
      // given
      cluster.awaitCompleteTopology();
      final var actuator = ClusterActuator.of(cluster.availableGateway());

      // when - increase zoneA replicas from 1→2 (RF 2→3)
      final var config =
          new PartitionDistributionConfig()
              .type(PartitionDistributionConfig.TypeEnum.ZONE_AWARE)
              .zones(
                  List.of(
                      new ZoneSpec().name(ZONES[0]).numberOfReplicas(2).priority(100),
                      new ZoneSpec().name(ZONES[1]).numberOfReplicas(1).priority(10)));
      final var response = actuator.patchPartitionDistribution(config, false);

      // then - exact planned operations.
      // Before (RF=2): zoneA_0 holds P1,P3 and zoneA_1 holds P2 (1 replica/zone, priority 2+1).
      // After  (RF=3): each partition needs both zoneA brokers; existing zoneA replica gets
      // promoted to priority 3, new zoneA replica joins at priority 2. zoneB unchanged.
      // nodeIdx mapping: 0=zoneA_0, 1=zoneB_0, 2=zoneA_1
      assertThat(response.getPlannedChanges())
          .isEqualTo(
              List.of(
                  new Operation()
                      .operation(OperationEnum.UPDATE_PARTITION_DISTRIBUTOR_CONFIG)
                      // Coordinator
                      .brokerId(brokerId(0))
                      .partitionDistributionConfig(config),
                  new Operation()
                      .operation(OperationEnum.PARTITION_JOIN)
                      .brokerId(brokerId(2))
                      .partitionId(1)
                      .priority(2),
                  new Operation()
                      .operation(OperationEnum.PARTITION_RECONFIGURE_PRIORITY)
                      .brokerId(brokerId(0))
                      .partitionId(1)
                      .priority(3),
                  new Operation()
                      .operation(OperationEnum.PARTITION_JOIN)
                      .brokerId(brokerId(0))
                      .partitionId(2)
                      .priority(2),
                  new Operation()
                      .operation(OperationEnum.PARTITION_RECONFIGURE_PRIORITY)
                      .brokerId(brokerId(2))
                      .partitionId(2)
                      .priority(3),
                  new Operation()
                      .operation(OperationEnum.PARTITION_JOIN)
                      .brokerId(brokerId(2))
                      .partitionId(3)
                      .priority(2),
                  new Operation()
                      .operation(OperationEnum.PARTITION_RECONFIGURE_PRIORITY)
                      .brokerId(brokerId(0))
                      .partitionId(3)
                      .priority(3)));

      Awaitility.await()
          .untilAsserted(
              () -> ClusterActuatorAssert.assertThat(actuator).hasAppliedChanges(response));

      final var topology = actuator.getTopology();
      assertThat(topology.getPartitionDistribution()).isEqualTo(config);
    }
  }

  @Test
  void shouldRejectPartitionDistributionWithoutZones() {
    try (final var cluster = createCluster(minReplicationFactor())) {
      // given
      cluster.awaitCompleteTopology();
      final var actuator = ClusterActuator.of(cluster.availableGateway());

      // when - then
      final var config =
          new PartitionDistributionConfig().type(TypeEnum.ZONE_AWARE).zones(List.of());
      assertThatCode(() -> actuator.patchPartitionDistribution(config, false))
          .isInstanceOf(FeignException.BadRequest.class);
    }
  }

  @Test
  void shouldRejectRoundRobinConfigOnZoneAwareCluster() {
    try (final var cluster = createCluster(minReplicationFactor())) {
      // given
      cluster.awaitCompleteTopology();
      final var actuator = ClusterActuator.of(cluster.availableGateway());

      // when - then
      final var config =
          new PartitionDistributionConfig().type(PartitionDistributionConfig.TypeEnum.ROUND_ROBIN);
      assertThatCode(() -> actuator.patchPartitionDistribution(config, false))
          .isInstanceOf(FeignException.BadRequest.class);
    }
  }

  @Test
  void shouldRejectClusterPatchWithBareIntegers() {
    try (final var cluster = createCluster(brokerCount())) {
      // given
      cluster.awaitCompleteTopology();
      final var actuator = ClusterActuator.of(cluster.availableGateway());

      // when - attempt patch with bare integer IDs on zone-aware cluster
      final var request =
          new ClusterConfigPatchRequest()
              .brokers(
                  new ClusterConfigPatchRequestBrokers()
                      .add(List.of(BrokerId.of(0), BrokerId.of(1))));
      assertThatCode(() -> actuator.patchCluster(request, false, false))
          .isInstanceOf(FeignException.BadRequest.class)
          .hasMessageContaining("bare node ID");
    }
  }
}
