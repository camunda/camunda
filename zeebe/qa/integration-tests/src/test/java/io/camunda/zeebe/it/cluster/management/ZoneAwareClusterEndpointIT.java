/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.management;

import static org.assertj.core.api.Assertions.assertThatCode;

import feign.FeignException;
import io.atomix.cluster.BrokerMemberId;
import io.atomix.cluster.MemberId;
import io.camunda.configuration.Partitioning.Scheme;
import io.camunda.configuration.Zone;
import io.camunda.configuration.ZoneAware;
import io.camunda.zeebe.management.cluster.BrokerId;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequest;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequestBrokers;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ZoneAwareClusterEndpointIT extends ClusterEndpointIT {

  private static final String[] ZONES = {"zoneA", "zoneB"};

  @Override
  @SuppressWarnings("resource")
  protected TestCluster createCluster(final int replicationFactor) {
    final var replicasZoneB = replicationFactor / 2;
    final var replicasZoneA = replicationFactor - replicasZoneB;
    return TestCluster.builder()
        .withEmbeddedGateway(true)
        .withBrokersCount(BROKER_COUNT)
        .withPartitionsCount(PARTITION_COUNT)
        .withReplicationFactor(replicationFactor)
        .withoutNodeId()
        .multiZone()
        .withBrokerConfig(
            (id, broker) -> {
              final int localId = Integer.parseInt(id.id());
              final String zone = ZONES[localId % ZONES.length];
              broker.withUnifiedConfig(
                  uc -> {
                    uc.getCluster().setZone(zone);
                    uc.getCluster().setNodeId(localId / 2); // 2 zones: 0->0, 1-> 0, 2 -> 1, 3 -> 1
                    uc.getCluster().setSize(BROKER_COUNT);
                    final var half = BROKER_COUNT / 2;
                    final var zoneAware =
                        new ZoneAware(
                            List.of(
                                new Zone(ZONES[0], BROKER_COUNT - half, replicasZoneA, 100),
                                new Zone(ZONES[1], half, replicasZoneB, 10)));
                    uc.getCluster().getPartitioning().setScheme(Scheme.ZONE_AWARE);
                    uc.getCluster().getPartitioning().setZoneAware(zoneAware);
                  });
            })
        .build()
        .start();
  }

  @Override
  protected String zone() {
    return ZONES[0];
  }

  @Override
  protected BrokerId brokerId(final int nodeIdx) {
    return new BrokerId.String(BrokerMemberId.from(zone(), nodeIdx).toString());
  }

  @Override
  protected MemberId memberIdForBroker(final int nodeIdx) {
    // In this zone-aware test, brokerId(nodeIdx) maps to zoneA brokers.
    // zoneA brokers have localIds 0, 2, 4, ... (even indices in a 2-zone cluster).
    return MemberId.from(java.lang.String.valueOf(nodeIdx * ZONES.length));
  }

  @Test
  void shouldRejectBareIntegersWhenScaling() {
    try (final var cluster = createCluster(BROKER_COUNT)) {
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
    try (final var cluster = createCluster(BROKER_COUNT)) {
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
    try (final var cluster = createCluster(BROKER_COUNT)) {
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
    try (final var cluster = createCluster(BROKER_COUNT)) {
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
    try (final var cluster = createCluster(BROKER_COUNT)) {
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
