/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.clustering.zoneaware;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.configuration.Partitioning.Scheme;
import io.camunda.configuration.Zone;
import io.camunda.configuration.ZoneAware;
import io.camunda.zeebe.management.cluster.BrokerId;
import io.camunda.zeebe.management.cluster.PartitionState;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import java.util.List;
import org.awaitility.Awaitility;

public class ZoneHelpers {

  public static TestCluster createCluster(
      final String name,
      final List<Zone> zones,
      final int partitionCount,
      final int replicationFactor) {
    return TestCluster.builder()
        .withName(name)
        .withEmbeddedGateway(true)
        .withBrokersCount(zones.stream().mapToInt(Zone::numberOfBrokers).sum())
        .withPartitionsCount(partitionCount)
        .withReplicationFactor(replicationFactor)
        .multiZone(zones)
        .build()
        .start();
  }

  /**
   * Starts a broker with id in a zone without adding it to the topology. The {@link
   * TestStandaloneBroker#start} is run in a virtual thread as it returns only after it is able to
   * join the topology
   */
  public static TestStandaloneBroker startBrokerInZone(
      final TestCluster cluster,
      final String zone,
      final int nodeId,
      final int clusterSize,
      final List<Zone> newZones) {
    final var contactPoint =
        cluster.brokers().values().iterator().next().address(TestZeebePort.CLUSTER);
    final var broker =
        new TestStandaloneBroker()
            .withUnauthenticatedAccess()
            .withUnifiedConfig(
                cfg -> {
                  final var clusterCfg = cfg.getCluster();
                  clusterCfg.setName(cluster.name());
                  clusterCfg.setInitialContactPoints(List.of(contactPoint));
                  clusterCfg.setZone(zone);
                  clusterCfg.setSize(clusterSize);
                  clusterCfg.setNodeId(nodeId);
                  clusterCfg.getPartitioning().setScheme(Scheme.ZONE_AWARE);
                  clusterCfg.getPartitioning().setZoneAware(new ZoneAware(newZones));
                  cfg.getData().getSecondaryStorage().setAutoconfigureCamundaExporter(false);
                });

    Thread.ofVirtual().name("start-" + zone + "-" + nodeId).start(broker::start);

    return broker;
  }

  public static void assertZoneHostsPartitions(
      final ClusterActuator actuator, final String zone, final int nodeId) {
    final var brokerId = new BrokerId.String(MemberId.from(zone, nodeId).toString());
    Awaitility.await()
        .untilAsserted(
            () -> {
              final var broker =
                  actuator.getTopology().getBrokers().stream()
                      .filter(b -> brokerId.equals(b.getId()))
                      .findFirst()
                      .orElseThrow();
              assertThat(broker.getPartitions())
                  .as("the added zone's broker hosts all partitions")
                  .extracting(PartitionState::getId)
                  .containsExactlyInAnyOrder(1, 2);
            });
  }

  /**
   * start a cluster over {@code initialZones}, add a broker in {@code newZone}, then update the
   * partition distribution to {@code targetZones}.
   */
  public record AddZoneScenario(
      String clusterName, List<Zone> initialZones, List<Zone> targetZones, String newZone) {
    @Override
    public String toString() {
      return clusterName;
    }
  }
}
