/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.clustering.dynamic;

import static io.camunda.zeebe.dynamic.config.gossip.ClusterConfigurationGossiperConfig.DEFAULT_SYNC_REQUEST_TIMEOUT;
import static io.camunda.zeebe.it.clustering.dynamic.Utils.assertChangeIsPlanned;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.system.configuration.ClusterCfg;
import io.camunda.zeebe.broker.system.configuration.ConfigManagerCfg;
import io.camunda.zeebe.dynamic.config.gossip.ClusterConfigurationGossiperConfig;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator.PartitionStatus;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.qa.util.topology.ClusterActuatorAssert;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotId;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.Objects;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
final class SnapshotAfterScalingTest {
  @TestZeebe
  TestCluster cluster =
      TestCluster.builder()
          .useRecordingExporter(true)
          .withBrokersCount(2)
          .withReplicationFactor(1)
          .withPartitionsCount(2)
          .withGatewaysCount(1)
          .withBrokerConfig(
              broker -> {
                final ConfigManagerCfg configManagerCfg =
                    new ConfigManagerCfg(
                        new ClusterConfigurationGossiperConfig(
                            Duration.ofSeconds(1), DEFAULT_SYNC_REQUEST_TIMEOUT, 3));
                final ClusterCfg clusterCfg = broker.brokerConfig().getCluster();
                clusterCfg.setConfigManager(configManagerCfg);
              })
          .build()
          .start()
          .awaitCompleteTopology();

  @Test
  void shouldTakeSnapshotOnAllReplicasAfterScaling() {
    // Instead of doing a full scaling operation, we just add a new replica to partition 1. This is
    // done to simplify the test setup. This test fails without the fix
    // https://github.com/camunda/camunda/pull/15277

    // given
    final var actuator = ClusterActuator.of(cluster.availableGateway());
    final var response = actuator.joinPartition(1, 1, 1);
    assertChangeIsPlanned(response);
    Awaitility.await("Requested change is completed in time")
        .untilAsserted(
            () -> ClusterActuatorAssert.assertThat(cluster).hasCompletedChanges(response));
    cluster.awaitHealthyTopology();

    // when -- add some data to be processed and exported
    try (final var client = cluster.availableGateway().newClientBuilder().build()) {
      // the message will be published to partition 1
      client.newPublishMessageCommand().messageName("msg").correlationKey("item-0").send().join();
    }

    Awaitility.await()
        .until(
            () ->
                RecordingExporter.messageRecords(MessageIntent.PUBLISHED).withPartitionId(1).count()
                    == 1);

    // then
    Awaitility.await("Both replicas has updated exported position")
        .timeout(Duration.ofSeconds(30))
        .untilAsserted(() -> hasExportedInFollower(cluster));

    Awaitility.await("Both replicas has taken snapshot")
        .timeout(Duration.ofSeconds(30))
        .untilAsserted(() -> hasSnapshotOnAllBrokersForPartitionOne(cluster));
  }

  private void hasExportedInFollower(final TestCluster cluster) {
    final var statuses =
        cluster.brokers().values().stream()
            .map(PartitionsActuator::of)
            .map(PartitionsActuator::query)
            .map(s -> s.get(1))
            .toList();

    assertThat(
            statuses.stream()
                .map(PartitionStatus::exportedPosition)
                .filter(position -> position > 0)
                .count())
        .describedAs(
            "Expected both replicas to have same exported position but received %s", statuses)
        .isEqualTo(2);
  }

  private void hasSnapshotOnAllBrokersForPartitionOne(final TestCluster cluster) {
    cluster.brokers().values().stream()
        .map(PartitionsActuator::of)
        .forEach(PartitionsActuator::takeSnapshot);

    final var statuses =
        cluster.brokers().values().stream()
            .map(PartitionsActuator::of)
            .map(PartitionsActuator::query)
            .toList();
    assertThat(
            statuses.stream()
                .map(status -> status.get(1).snapshotId())
                .filter(Objects::nonNull)
                .count())
        .describedAs("Expected both replicas to have taken snapshot. Received status %s", statuses)
        .isEqualTo(2);

    assertThat(
            statuses.stream()
                .map(
                    status ->
                        FileBasedSnapshotId.ofFileName(status.get(1).snapshotId()).get().getIndex())
                .distinct()
                .count())
        .describedAs(
            "Expected both replicas to have snapshot at same index. Received status %s", statuses)
        .isOne();
  }
}
