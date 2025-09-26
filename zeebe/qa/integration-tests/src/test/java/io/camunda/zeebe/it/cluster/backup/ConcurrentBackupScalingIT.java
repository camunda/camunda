/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import feign.FeignException;
import io.atomix.cluster.MemberId;
import io.camunda.zeebe.broker.system.configuration.backup.BackupStoreCfg.BackupStoreType;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequest;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequestPartitions;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.TestApplication;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.nio.file.Path;
import java.time.Duration;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Integration test to verify that backup requests are rejected when scaling is in progress. */
@Testcontainers
@ZeebeIntegration
final class ConcurrentBackupScalingIT {

  @TempDir Path backupBasePath;

  @TestZeebe(autoStart = false)
  private final TestCluster cluster =
      TestCluster.builder()
          .withBrokersCount(
              3) // Need 3 brokers to ensure we can stop one and still have scaling coordination
          .withGatewaysCount(1)
          .withReplicationFactor(3)
          .withPartitionsCount(1)
          .withEmbeddedGateway(false)
          .withBrokerConfig(
              b ->
                  b.withBrokerConfig(
                      bb -> {
                        bb.getCluster()
                            .getMembership()
                            .setSyncInterval(Duration.ofSeconds(1))
                            .setGossipInterval(Duration.ofMillis(500));
                      }))
          .withNodeConfig(this::configureNode)
          .build();

  @BeforeEach
  void beforeEach() {
    cluster.brokers().values().forEach(this::configureBackup);

    cluster.start().awaitCompleteTopology();
  }

  @Test
  void shouldRejectBackupWhenScalingInProgress() {
    // given
    final var backupActuator = BackupActuator.of(cluster.availableGateway());
    final var clusterActuator = ClusterActuator.of(cluster.availableGateway());

    // Stop broker 2 to stall scaling progress (broker 0 remains as coordinator)
    final var brokerToStop = cluster.brokers().get(MemberId.from("2"));
    assertThat(brokerToStop).isNotNull();
    brokerToStop.stop();

    // Start scaling to trigger scaling in progress state
    final var targetPartitionCount = 3; // Scale from 2 to 3 partitions
    clusterActuator.patchCluster(
        new ClusterConfigPatchRequest()
            .partitions(
                new ClusterConfigPatchRequestPartitions()
                    .count(targetPartitionCount)
                    .replicationFactor(3)),
        false,
        false);

    // Verify scaling is in progress (routing state shows different current vs desired partitions)
    Awaitility.await("until scaling starts")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final var topology = clusterActuator.getTopology();
              assertThat(topology.getPendingChange()).isNotNull();
              assertThat(topology.getPendingChange().getCompleted()).isNotEmpty();
            });

    // when - try to take a backup while scaling is in progress
    final long backupId = 999;

    // then - backup request should be rejected
    assertThatThrownBy(() -> backupActuator.take(backupId))
        .asInstanceOf(InstanceOfAssertFactories.type(FeignException.class))
        .satisfies(
            exception -> {
              assertThat(exception.status()).isEqualTo(409); // Client error status
              assertThat(exception.contentUTF8())
                  .containsIgnoringCase("Cannot take backup while scaling is in progress");
            });
  }

  private void configureBackup(final TestStandaloneBroker broker) {
    broker.withBrokerConfig(
        cfg -> {
          final var backup = cfg.getData().getBackup();
          backup.setStore(BackupStoreType.FILESYSTEM);
          final var fileSystem = backup.getFilesystem();
          fileSystem.setBasePath(backupBasePath.toAbsolutePath().toString());
        });
  }

  private void configureNode(final TestApplication<?> node) {
    node.withProperty("management.endpoints.web.exposure.include", "*");
  }
}
