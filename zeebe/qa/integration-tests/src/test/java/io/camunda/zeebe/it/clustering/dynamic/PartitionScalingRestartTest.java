/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.clustering.dynamic;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequest;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequestPartitions;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.topology.ClusterActuatorAssert;
import java.nio.file.Path;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class PartitionScalingRestartTest {
  @TempDir private Path baseWorkingDir;

  private TestCluster cluster;

  @BeforeEach
  void setup() {
    cluster =
        TestCluster.builder()
            .useRecordingExporter(true)
            .withBrokersCount(1)
            .withPartitionsCount(1)
            .withReplicationFactor(1)
            .withEmbeddedGateway(true)
            .withBrokerConfig(
                (memberId, broker) ->
                    broker.withWorkingDirectory(resolveBrokerDir(baseWorkingDir, memberId)))
            .build();

    cluster.start().awaitCompleteTopology();
  }

  @AfterEach
  void tearDown() {
    if (cluster != null) {
      cluster.shutdown();
    }
  }

  @Test
  void shouldCompleteScalingAfterRestart() {
    // given
    final var actuator = ClusterActuator.of(cluster.anyGateway());

    final var response =
        actuator.patchCluster(
            new ClusterConfigPatchRequest()
                .partitions(
                    new ClusterConfigPatchRequestPartitions().count(2).replicationFactor(1)),
            false,
            false);

    cluster.shutdown();

    cluster.start();

    // wait until scaling is completed
    Awaitility.await()
        .untilAsserted(
            () -> ClusterActuatorAssert.assertThat(actuator).hasAppliedChanges(response));
    cluster.awaitCompleteTopology(1, 2, 1, Duration.ofSeconds(10));
  }

  private Path resolveBrokerDir(final Path base, final MemberId memberId) {
    return base.resolve("broker-" + memberId.id());
  }
}
