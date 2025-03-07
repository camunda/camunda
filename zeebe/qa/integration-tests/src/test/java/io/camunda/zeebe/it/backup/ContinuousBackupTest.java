/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.backup;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.management.backups.BackupInfo;
import io.camunda.management.backups.StateCode;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.backup.BackupStoreCfg.BackupStoreType;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestRestoreApp;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotId;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import joptsimple.internal.Strings;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.unit.DataSize;

final class ContinuousBackupTest {

  private static final Logger LOG = LoggerFactory.getLogger(ContinuousBackupTest.class);
  @TestZeebe private TestCluster cluster;
  private Path backupDir;

  @BeforeEach
  void setup() throws IOException {
    backupDir = Path.of("/tmp/zeebe-backup");
    FileUtil.deleteFolderIfExists(backupDir);
    cluster =
        TestCluster.builder()
            .withBrokersCount(3)
            .withGatewaysCount(1)
            .withReplicationFactor(3)
            .withPartitionsCount(3)
            .withEmbeddedGateway(false)
            .withBrokerConfig(
                broker -> {
                  broker.withRecordingExporter(false);
                  broker.withRdbmsExporter();
                  broker
                      .withBrokerConfig(this::configureBroker)
                      .withWorkingDirectory(backupDir.resolve(broker.nodeId().id()));
                })
            .build()
            .start()
            .awaitCompleteTopology();
    Files.createDirectories(backupDir.resolve("manifests/"));
  }

  private void configureBroker(final BrokerCfg brokerCfg) {
    brokerCfg.getData().setLogSegmentSize(DataSize.ofMegabytes(4));
    final var backupCfg = brokerCfg.getData().getBackup();
    backupCfg.setStore(BackupStoreType.FILESYSTEM);
    backupCfg.getFilesystem().setBasePath(backupDir.toString());
  }

  @Test
  void shouldRestoreFromContinuousBackup() throws IOException, InterruptedException {
    // given -- a three member cluster
    final var initialLeader = cluster.leaderForPartition(1);
    final long firstProcessInstanceKey;
    try (final var client = cluster.newClientBuilder().build()) {
      client
          .newDeployResourceCommand()
          .addProcessModel(
              Bpmn.createExecutableProcess("process")
                  .startEvent()
                  .userTask("task")
                  .zeebeUserTask()
                  .endEvent()
                  .done(),
              "process.bpmn")
          .send()
          .join();

      firstProcessInstanceKey =
          client
              .newCreateInstanceCommand()
              .bpmnProcessId("process")
              .latestVersion()
              .send()
              .join()
              .getProcessInstanceKey();
    }

    LOG.info("First process instance on {}", firstProcessInstanceKey);

    PartitionsActuator.of(initialLeader).takeSnapshot();
    Awaitility.await("Snapshot is taken")
        .atMost(Duration.ofSeconds(60))
        .until(
            () ->
                Optional.ofNullable(
                        PartitionsActuator.of(initialLeader).query().get(1).snapshotId())
                    .flatMap(FileBasedSnapshotId::ofFileName),
            Optional::isPresent)
        .orElseThrow();

    // Fill at least one segment
    try (final var client = cluster.newClientBuilder().build()) {
      for (int i = 0; i < 4000; i++) {
        client
            .newPublishMessageCommand()
            .messageName("msg-" + i)
            .correlationKey("msg-" + i)
            .variable("var", "\"" + Strings.repeat('x', 3000) + "\"")
            .send()
            .join();
      }
    }

    // when -- create a backup from one member, then one from the other member
    BackupActuator.of(cluster.availableGateway()).take(1);
    Awaitility.await("until a backup exists with the given ID")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions() // 404 NOT_FOUND throws exception
        .untilAsserted(
            () -> {
              final var status = BackupActuator.of(cluster.availableGateway()).status(1);
              assertThat(status)
                  .extracting(BackupInfo::getBackupId, BackupInfo::getState)
                  .containsExactly((long) 1, StateCode.COMPLETED);
            });

    initialLeader.stop();
    final var newGateway =
        Awaitility.await("Any gateway available")
            .ignoreExceptions()
            .until(cluster::availableGateway, Objects::nonNull);

    // Fill at least one segment
    try (final var client = cluster.newClientBuilder().build()) {
      for (int i = 0; i < 4000; i++) {
        client
            .newPublishMessageCommand()
            .messageName("msg-" + i)
            .correlationKey("msg-" + i)
            .variable("var", "\"" + Strings.repeat('x', 3000) + "\"")
            .send()
            .join();
      }
    }

    final long secondInstanceKey;
    try (final var client = cluster.newClientBuilder().build()) {
      secondInstanceKey =
          Awaitility.await("A process instance is created")
              .ignoreExceptions()
              .until(
                  () ->
                      client
                          .newCreateInstanceCommand()
                          .bpmnProcessId("process")
                          .latestVersion()
                          .send()
                          .join()
                          .getProcessInstanceKey(),
                  (key) -> true);
    }

    final var actuator = BackupActuator.of(newGateway);
    actuator.take(2);

    final var otherPartition = Protocol.decodePartitionId(secondInstanceKey);
    final var otherBroker = cluster.leaderForPartition(otherPartition);
    LOG.info("Second process instance on {}", otherPartition);
    PartitionsActuator.of(otherBroker).takeSnapshot();
    Awaitility.await("Snapshot is taken")
        .atMost(Duration.ofSeconds(60))
        .until(
            () ->
                Optional.ofNullable(
                        PartitionsActuator.of(otherBroker).query().get(otherPartition).snapshotId())
                    .flatMap(FileBasedSnapshotId::ofFileName),
            Optional::isPresent)
        .orElseThrow();

    Awaitility.await("until a backup exists with the given ID")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions() // 404 NOT_FOUND throws exception
        .untilAsserted(
            () -> {
              final var status = actuator.status(2);
              assertThat(status)
                  .extracting(BackupInfo::getBackupId, BackupInfo::getState)
                  .containsExactly((long) 2, StateCode.COMPLETED);
            });

    // then -- restore the combined backup
    cluster.close();

    for (final var broker : cluster.brokers().values()) {
      FileUtil.deleteFolder(broker.brokerConfig().getData().getDirectory());
      broker.property("camunda.database.type", String.class, "");
      final var restoreApp = new TestRestoreApp(broker.brokerConfig());
      restoreApp.withProperty(
          "camunda.database.type", broker.property("camunda.database.type", String.class, ""));
      restoreApp.withProperty(
          "camunda.database.url", broker.property("camunda.database.url", String.class, ""));
      restoreApp.withProperty(
          "camunda.database.username",
          broker.property("camunda.database.username", String.class, ""));
      restoreApp.withProperty(
          "camunda.database.password",
          broker.property("camunda.database.password", String.class, ""));
      restoreApp.withBackupId(2).start().close();
    }

    // then -- the cluster can start again
    cluster.start().awaitCompleteTopology();

    try (final var client = cluster.newClientBuilder().build()) {
      client.newCancelInstanceCommand(firstProcessInstanceKey).send().join();
      client.newCancelInstanceCommand(secondInstanceKey).send().join();
    }
  }
}
