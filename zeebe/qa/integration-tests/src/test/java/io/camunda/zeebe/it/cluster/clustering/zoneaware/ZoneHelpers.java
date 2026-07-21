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
import io.camunda.zeebe.qa.util.topology.ClusterActuatorAssert;
import java.time.Duration;
import java.util.List;
import org.agrona.CloseHelper;
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
   * Starts a standalone broker in {@code zone} (static node id, joining the running cluster), adds
   * it to the persisted topology, and waits for the change to be applied. The broker's blocking
   * {@link TestStandaloneBroker#start()} runs on a virtual thread because it only returns once the
   * broker has joined the topology (i.e. after {@code addBroker}).
   */
  public static AutoCloseable addBrokerInZone(
      final TestCluster cluster,
      final ClusterActuator actuator,
      final String clusterName,
      final String zone,
      final int nodeId,
      final int newTotalSize,
      final List<Zone> targetZones) {
    final var contactPoint =
        cluster.brokers().values().iterator().next().address(TestZeebePort.CLUSTER);
    final var broker =
        new TestStandaloneBroker()
            .withUnauthenticatedAccess()
            .withUnifiedConfig(
                cfg -> {
                  final var clusterCfg = cfg.getCluster();
                  clusterCfg.setName(clusterName);
                  clusterCfg.setInitialContactPoints(List.of(contactPoint));
                  clusterCfg.setZone(zone);
                  clusterCfg.setNodeId(nodeId);
                  clusterCfg.setSize(newTotalSize);
                  clusterCfg.getPartitioning().setScheme(Scheme.ZONE_AWARE);
                  clusterCfg.getPartitioning().setZoneAware(new ZoneAware(targetZones));
                  cfg.getData().getSecondaryStorage().setAutoconfigureCamundaExporter(false);
                });

    final var startThread =
        Thread.ofVirtual().name("start-" + zone + "-" + nodeId).start(broker::start);

    final var added = actuator.addBroker(MemberId.from(zone, nodeId).toString());
    final AutoCloseable closeable =
        () -> {
          // Close the broker first to unblock the (potentially still blocked) start() call, then
          // join the
          // virtual thread so it does not leak beyond the test.
          broker.close();
          startThread.interrupt();
          startThread.join(Duration.ofSeconds(30));
        };
    try {
      Awaitility.await()
          .untilAsserted(() -> ClusterActuatorAssert.assertThat(actuator).hasAppliedChanges(added));
    } catch (final Exception e) {
      CloseHelper.close(closeable);
    }

    return closeable;
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
