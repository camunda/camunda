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
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequestPartitions;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import java.util.List;
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
    // Replace replicationFactor with per-zone replicationFactors so the cluster-patch path works
    // on zone-aware clusters. zoneA: 2 replicas, zoneB: 1 replica (total RF = minRF + 1 = 3).
    final var zoneAwareRequest =
        new ClusterConfigPatchRequest()
            .brokers(request.getBrokers())
            .partitions(
                new ClusterConfigPatchRequestPartitions()
                    .count(partitionCount())
                    .putNewReplicationFactorsItem(ZONES[0], 2)
                    .putNewReplicationFactorsItem(ZONES[1], 1));
    final var response = actuator.patchCluster(zoneAwareRequest, true, false);
    assertThat(response.getExpectedTopology())
        .describedAs("Cluster has " + brokerCount() + " brokers")
        .hasSize(brokerCount());
    assertThat(response.getPlannedChanges()).isNotEmpty();
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
  void shouldScaleZoneViaCount() {
    try (final var cluster = createCluster(minReplicationFactor())) {
      // given
      cluster.awaitCompleteTopology();
      final var actuator = ClusterActuator.of(cluster.availableGateway());

      // when -- scale zone() (zoneA) from its current broker count to one more
      final var request =
          new ClusterConfigPatchRequest()
              .brokers(new ClusterConfigPatchRequestBrokers().count(brokerCount()).zone(zone()));
      final var response = actuator.patchCluster(request, true, false);

      final var topology = actuator.getTopology();

      // then -- cluster grew by one (zoneA has an additional broker)
      assertThat(response.getExpectedTopology()).hasSize(brokerCount() + 1);
      assertThat(response.getPlannedChanges()).isNotEmpty();
      // then -- topology reports zone-aware distributor config
      assertThat(topology.getPartitionDistribution()).isNotNull();
      assertThat(topology.getPartitionDistribution().getType())
          .isEqualTo(
              io.camunda.zeebe.management.cluster.PartitionDistributionConfig.TypeEnum.ZONE_AWARE);
      assertThat(topology.getPartitionDistribution().getZones())
          .satisfiesExactlyInAnyOrder(
              z -> {
                assertThat(z.getName()).isEqualTo(ZONES[0]); // zoneA: 1 replica
                assertThat(z.getNumberOfReplicas()).isEqualTo(1);
                assertThat(z.getPriority()).isEqualTo(100);
              },
              z -> {
                assertThat(z.getName()).isEqualTo(ZONES[1]); // zoneB: 1 replica
                assertThat(z.getNumberOfReplicas()).isEqualTo(1);
                assertThat(z.getPriority()).isEqualTo(10);
              });
    }
  }

  @Test
  void shouldRejectCountWithoutZoneOnZoneAware() {
    try (final var cluster = createCluster(minReplicationFactor())) {
      // given
      cluster.awaitCompleteTopology();
      final var actuator = ClusterActuator.of(cluster.availableGateway());

      // when
      final var request =
          new ClusterConfigPatchRequest()
              .brokers(new ClusterConfigPatchRequestBrokers().count(brokerCount() + 1));

      // then -- rejected: zone field is required for count-based scaling on zone-aware clusters
      assertThatCode(() -> actuator.patchCluster(request, true, false))
          .isInstanceOf(FeignException.BadRequest.class)
          .hasMessageContaining("zone");
    }
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
