/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.clustering.dynamic;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.management.cluster.PartitionStateCode;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import io.camunda.zeebe.qa.util.topology.ClusterActuatorAssert;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(3 * 60)
class ScaleResiliencyTest {

  private static Path getDataDirectory(final Path tmpDir, final int brokerId) {
    final var path = tmpDir.resolve(String.valueOf(brokerId));
    try {
      Files.createDirectory(path);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
    return path;
  }

  private static TestCluster createCluster(
      final int initialClusterSize,
      final int partitionsCount,
      final int replicationFactor,
      final Path tmpDir) {
    final var cluster =
        TestCluster.builder()
            .useRecordingExporter(true)
            .withGatewaysCount(1)
            .withGatewayConfig(
                g -> {
                  final var membership = g.unifiedConfig().getCluster().getMembership();
                  // Decrease the timeouts for fast convergence of gateway topology.
                  membership.setSyncInterval(Duration.ofSeconds(1));
                  membership.setFailureTimeout(Duration.ofSeconds(2));
                })
            .withEmbeddedGateway(false)
            .withBrokersCount(initialClusterSize)
            .withPartitionsCount(partitionsCount)
            .withReplicationFactor(replicationFactor)
            .withBrokerConfig(
                b -> {
                  // Decrease the timeouts for fast convergence of gateway topology.
                  final var membership = b.unifiedConfig().getCluster().getMembership();
                  membership.setSyncInterval(Duration.ofSeconds(1));
                  membership.setFailureTimeout(Duration.ofSeconds(2));

                  b.withWorkingDirectory(
                      getDataDirectory(tmpDir, b.unifiedConfig().getCluster().getNodeId()));
                })
            .build();
    cluster.start();
    cluster.awaitCompleteTopology();
    return cluster;
  }

  @Nested
  class ScaleUp {
    private static final int REPLICATION_FACTOR = 2;
    private static final int PARTITIONS_COUNT = 2;
    private static final int INITIAL_CLUSTER_SIZE = 2;
    @TempDir Path tmpDir;
    private final List<TestStandaloneBroker> newBrokers = new ArrayList<>();
    private TestCluster cluster;

    @BeforeEach
    void setup() {
      cluster = createCluster(INITIAL_CLUSTER_SIZE, PARTITIONS_COUNT, REPLICATION_FACTOR, tmpDir);
    }

    @AfterEach
    void shutdownNewBrokers() {
      newBrokers.forEach(TestStandaloneBroker::close);
      newBrokers.clear();
      cluster.shutdown();
    }

    @Test
    void shouldContinueScaleAfterRestart() {
      // given cluster size 2, partitions 2, replicationFactor 2;

      // shutdown broker 1
      cluster.brokers().get(MemberId.from("1")).stop();
      final var newClusterSize = INITIAL_CLUSTER_SIZE + 1;
      final var newBroker =
          createNewBroker(
              newClusterSize,
              newClusterSize - 1,
              REPLICATION_FACTOR,
              getDataDirectory(tmpDir, newClusterSize - 1));

      // scale to 3
      Utils.scale(cluster, newClusterSize);
      // wait until broker 2 is added to the cluster
      Awaitility.await()
          .untilAsserted(
              () -> ClusterActuatorAssert.assertThat(cluster).hasActiveBroker(newClusterSize - 1));
      // applying operations is stuck because there is no leader for partitions, so partitions
      // cannot be moved.

      // when
      // stop broker 2
      newBroker.stop();
      // restart broker 1
      cluster.brokers().get(MemberId.from("1")).start();
      // restart broker 2
      newBroker.start();

      // then
      // scale is completed
      Awaitility.await()
          .timeout(Duration.ofMinutes(2))
          .untilAsserted(
              () -> ClusterActuatorAssert.assertThat(cluster).doesNotHavePendingChanges());
      cluster.awaitCompleteTopology(
          newClusterSize, PARTITIONS_COUNT, REPLICATION_FACTOR, Duration.ofSeconds(10));
    }

    private TestStandaloneBroker createNewBroker(
        final int newClusterSize,
        final int newBrokerId,
        final int replicationFactor,
        final Path dataDirectory) {
      final var newBroker =
          new TestStandaloneBroker()
              .withUnifiedConfig(
                  uc -> {
                    uc.getCluster().setSize(newClusterSize);
                    uc.getCluster().setNodeId(newBrokerId);
                    uc.getCluster().setReplicationFactor(replicationFactor);
                    uc.getCluster()
                        .setInitialContactPoints(
                            List.of(
                                cluster
                                    .brokers()
                                    .get(MemberId.from("0"))
                                    .address(TestZeebePort.CLUSTER)));
                  })
              .withWorkingDirectory(dataDirectory);
      newBrokers.add(newBroker);
      newBroker.start();
      return newBroker;
    }
  }

  @Nested
  class ScaleDown {
    private static final int REPLICATION_FACTOR = 2;
    private static final int PARTITIONS_COUNT = 2;
    private static final int INITIAL_CLUSTER_SIZE = 3;
    @TempDir Path tmpDir;
    private final List<TestStandaloneBroker> newBrokers = new ArrayList<>();
    private TestCluster cluster;

    @BeforeEach
    void setup() {
      cluster = createCluster(INITIAL_CLUSTER_SIZE, PARTITIONS_COUNT, REPLICATION_FACTOR, tmpDir);
    }

    @AfterEach
    void shutdownNewBrokers() {
      newBrokers.forEach(TestStandaloneBroker::close);
      newBrokers.clear();
      cluster.shutdown();
    }

    // Simulate scale down by using individual join/leave operations so that we can control when a
    // broker restarts
    @Test
    void shouldContinueScaleDownAfterRestart() {
      // given  -- partition 1 in broker 0 and 1, and partition 2 in broker 0, 1 and 2
      ClusterActuator.of(cluster.availableGateway()).joinPartition(0, 2, 1);
      // wait until broker 0 has partition 2
      Awaitility.await()
          .untilAsserted(
              () ->
                  ClusterActuatorAssert.assertThat(cluster)
                      .brokerHasPartitionAtState(0, 2, PartitionStateCode.ACTIVE));

      cluster.brokers().get(MemberId.from("1")).stop();

      // when -- restart during leaving

      ClusterActuator.of(cluster.availableGateway()).leavePartition(2, 2);
      // wait until partition 2 is marked as leaving
      Awaitility.await()
          .untilAsserted(
              () ->
                  ClusterActuatorAssert.assertThat(cluster)
                      .brokerHasPartitionAtState(2, 2, PartitionStateCode.LEAVING));
      // Leave cannot complete because broker 1 is not running. So joint consensus cannot commit.
      // Restart at this point.
      cluster.brokers().get(MemberId.from("2")).stop();
      cluster.brokers().get(MemberId.from("2")).start();

      // restart broker 1 so that leave can complete
      cluster.brokers().get(MemberId.from("1")).start();

      // then
      // leave is completed
      Awaitility.await()
          .timeout(Duration.ofMinutes(2))
          .untilAsserted(
              () -> ClusterActuatorAssert.assertThat(cluster).doesNotHavePendingChanges());
    }
  }
}
