/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.clustering;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.configuration.Partitioning;
import io.camunda.configuration.Region;
import io.camunda.configuration.ZoneAware;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator.PartitionStatus;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verifies zone-aware partition distribution across two physically separate zone clusters that
 * discover each other via cross-zone initial contact points.
 *
 * <p>Zone A: 2 brokers, priority 1000. Zone B: 1 broker, priority 500. Partitions: 2, RF: 3.
 */
final class ZoneAwarePartitionDistributionIT {

  private static final ZoneAware ZONE_AWARE_CFG =
      new ZoneAware(List.of(new Region("zoneA", 2, 2, 1000), new Region("zoneB", 1, 1, 500)));

  // Zone A: 2 brokers, us-east1, priority 1000. Gateway enabled for topology checks.
  @AutoClose
  private static final TestCluster ZONE_A =
      TestCluster.builder()
          .withBrokersCount(2)
          .withEmbeddedGateway(true)
          .withPartitionsCount(2)
          .withReplicationFactor(3)
          .withoutNodeId()
          .multiZone()
          .withBrokerConfig(
              (id, broker) -> {
                final int localId = Integer.parseInt(id.id());
                broker.withUnifiedConfig(
                    uc -> {
                      uc.getCluster().setSize(3);
                      uc.getCluster().setZone("zoneA");
                      uc.getCluster().setNodeId(localId);
                      applyRegionAwarePartitioning(uc.getCluster().getPartitioning());
                    });
              })
          .build();

  // Zone B: 1 broker, us-west1, priority 500.
  @AutoClose
  private static final TestCluster ZONE_B =
      TestCluster.builder()
          .withBrokersCount(1)
          .withPartitionsCount(2)
          .withReplicationFactor(3)
          .withoutNodeId()
          .multiZone()
          .withBrokerConfig(
              (id, broker) ->
                  broker.withUnifiedConfig(
                      uc -> {
                        uc.getCluster().setSize(3);
                        uc.getCluster().setZone("zoneB");
                        uc.getCluster().setNodeId(0);
                        applyRegionAwarePartitioning(uc.getCluster().getPartitioning());
                      }))
          .build();

  @BeforeAll
  static void startClusters() {
    final List<String> allContactPoints =
        Stream.concat(ZONE_A.brokers().values().stream(), ZONE_B.brokers().values().stream())
            .map(b -> b.address(TestZeebePort.CLUSTER))
            .toList();

    Stream.concat(ZONE_A.brokers().values().stream(), ZONE_B.brokers().values().stream())
        .forEach(
            b ->
                b.withUnifiedConfig(
                    uc -> uc.getCluster().setInitialContactPoints(allContactPoints)));

    assertThat(
            CompletableFuture.allOf(
                CompletableFuture.runAsync(ZONE_A::start),
                CompletableFuture.runAsync(ZONE_B::start)))
        .succeedsWithin(Duration.ofSeconds(60));

    // await topology from zone A's embedded gateway; total cluster size = 3 (2 + 1)
    ZONE_A.awaitCompleteTopology(3, 2, 3, Duration.ofMinutes(3));
  }

  @Test
  void shouldDistributePartitionsAcrossZones() {
    // RF=3 with 3 total brokers: every broker holds all partitions
    assertThat(partitionsOf(ZONE_A, 0)).containsOnlyKeys(1, 2);
    assertThat(partitionsOf(ZONE_A, 1)).containsOnlyKeys(1, 2);
    assertThat(partitionsOf(ZONE_B, 0)).containsOnlyKeys(1, 2);
  }

  @Test
  void shouldElectLeadersInHighestPriorityZone() {
    // zoneA(priority 1000) > zoneB(priority 500): leaders must be in zoneA
    for (final int partitionId : List.of(1, 2)) {
      final boolean eastBroker0IsLeader = isLeader(partitionsOf(ZONE_A, 0).get(partitionId));
      final boolean eastBroker1IsLeader = isLeader(partitionsOf(ZONE_A, 1).get(partitionId));
      final boolean westBrokerIsLeader = isLeader(partitionsOf(ZONE_B, 0).get(partitionId));

      assertThat(eastBroker0IsLeader || eastBroker1IsLeader)
          .as("partition %d leader should be in zoneA(higher priority)", partitionId)
          .isTrue();
      assertThat(westBrokerIsLeader)
          .as("zoneB broker should not be leader for partition %d", partitionId)
          .isFalse();
    }
  }

  private static Map<Integer, PartitionStatus> partitionsOf(
      final TestCluster cluster, final int idx) {
    // local cluster memberId without zone
    final var broker = cluster.brokers().get(MemberId.from(idx));
    return PartitionsActuator.of(broker).query();
  }

  private static boolean isLeader(final PartitionStatus status) {
    return status != null && "Leader".equalsIgnoreCase(status.role());
  }

  private static void applyRegionAwarePartitioning(final Partitioning partitioning) {
    partitioning.setScheme(Partitioning.Scheme.REGION_AWARE);
    partitioning.setZoneAware(ZONE_AWARE_CFG);
  }
}
