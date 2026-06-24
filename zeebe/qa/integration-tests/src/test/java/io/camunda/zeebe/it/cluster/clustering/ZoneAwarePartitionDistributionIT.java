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
import io.camunda.configuration.Zone;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator.PartitionStatus;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Verifies zone-aware partition distribution for a cluster with two zones.
 *
 * <p>Zone A: 2 brokers, priority 1000. Zone B: 1 broker, priority 500. Partitions: 2, RF: 3.
 */
@ZeebeIntegration
final class ZoneAwarePartitionDistributionIT {

  private static final String ZONE_A = "zoneA";
  private static final String ZONE_B = "zoneB";
  private static final List<Zone> ZONE_CONFIGS =
      List.of(new Zone(ZONE_A, 2, 2, 1000), new Zone(ZONE_B, 1, 1, 500));

  @TestZeebe(purgeAfterEach = false)
  private static final TestCluster CLUSTER =
      TestCluster.builder()
          .withBrokersCount(3)
          .withEmbeddedGateway(true)
          .withPartitionsCount(2)
          .withReplicationFactor(3)
          .multiZone(ZONE_CONFIGS)
          .build();

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
      final String zone, final int localNodeId) {
    final var broker = CLUSTER.brokers().get(MemberId.from(zone, localNodeId));
    return PartitionsActuator.of(broker).query();
  }

  private static boolean isLeader(final PartitionStatus status) {
    return status != null && "Leader".equalsIgnoreCase(status.role());
  }
}
