/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.clustering.dynamic;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.topology.ClusterActuatorAssert;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.io.TempDir;

public class ClusterPurgeTest {

  @RegressionTest("https://github.com/camunda/camunda/issues/35650")
  void partitionShouldHaveLeaderAfterPurge(@TempDir final Path dataDirectory) {
    // given
    try (final var cluster =
        TestCluster.builder()
            .withEmbeddedGateway(true)
            .withBrokersCount(1)
            .withPartitionsCount(1)
            .withReplicationFactor(1)
            .withBrokerConfig(
                b ->
                    b.withWorkingDirectory(dataDirectory)
                        .withExporter("purgingexporter", this::configureExporter))
            .build()
            .start()) {

      cluster.awaitCompleteTopology();
      final var actuator = ClusterActuator.of(cluster.availableGateway());

      // Restart broker - increases the raft term of the partition
      cluster
          .brokers()
          .values()
          .forEach(
              broker -> {
                broker.close();
                broker.start();
              });

      cluster.awaitCompleteTopology();

      // when

      // block the purge until the partition is removed from the topology
      PurgingExporter.blockPurge();
      final var secondPurge = actuator.purge(false);

      try (final CamundaClient client = cluster.newClientBuilder().build()) {
        Awaitility.await()
            .timeout(Duration.ofSeconds(30))
            .untilAsserted(
                () ->
                    TopologyAssert.assertThat(client.newTopologyRequest().send().join())
                        .doesNotContainLeaderForPartition(1));
      }

      PurgingExporter.unblockPurge();
      Awaitility.await()
          .timeout(Duration.ofMinutes(2))
          .untilAsserted(
              () -> ClusterActuatorAssert.assertThat(cluster).hasAppliedChanges(secondPurge));

      // then

      // Verify topology is healthy
      try (final CamundaClient client = cluster.newClientBuilder().build()) {
        Awaitility.await("until cluster is healthy")
            .untilAsserted(
                () ->
                    TopologyAssert.assertThat(client.newTopologyRequest().send().join())
                        .isHealthy());
      }
    }
  }

  private static void getSet(final boolean newValue) {
    PurgingExporter.BLOCK_PURGE.set(newValue);
  }

  private void configureExporter(final ExporterCfg exporterCfg) {
    exporterCfg.setClassName(PurgingExporter.class.getName());
  }

  public static class PurgingExporter implements Exporter {
    static final AtomicBoolean BLOCK_PURGE = new AtomicBoolean(true);

    static void blockPurge() {
      BLOCK_PURGE.set(true);
    }

    static void unblockPurge() {
      BLOCK_PURGE.set(false);
    }

    @Override
    public void export(final Record<?> record) {}

    @Override
    public void purge() throws Exception {
      if (BLOCK_PURGE.get()) {
        throw new RuntimeException("Purging is temporarily blocked");
      }
    }
  }
}
