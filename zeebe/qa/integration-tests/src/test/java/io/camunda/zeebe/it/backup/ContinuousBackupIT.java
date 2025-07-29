/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.google.cloud.storage.BucketInfo;
import io.camunda.client.CamundaClient;
import io.camunda.management.backups.StateCode;
import io.camunda.zeebe.backup.gcs.GcsBackupConfig;
import io.camunda.zeebe.backup.gcs.GcsBackupStore;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.backup.BackupStoreCfg.BackupStoreType;
import io.camunda.zeebe.broker.system.configuration.backup.GcsBackupStoreConfig;
import io.camunda.zeebe.broker.system.configuration.backup.GcsBackupStoreConfig.GcsBackupStoreAuth;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestRestoreApp;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotId;
import io.camunda.zeebe.test.testcontainers.GcsContainer;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@ZeebeIntegration
@Testcontainers
final class ContinuousBackupIT {
  private static final String BUCKET_NAME = RandomStringUtils.randomAlphabetic(10).toLowerCase();
  @Container private static final GcsContainer GCS = new GcsContainer();

  private final String basePath = RandomStringUtils.randomAlphabetic(10).toLowerCase();

  @TestZeebe
  private final TestStandaloneBroker broker =
      new TestStandaloneBroker().withBrokerConfig(this::configureBroker);

  private BackupActuator backupActuator;
  private PartitionsActuator partitionsActuator;

  @BeforeEach
  void setUp() {
    backupActuator = BackupActuator.of(broker);
    partitionsActuator = PartitionsActuator.of(broker);
    // Some tests stop GCS, make sure it's always started again
    GCS.start();
  }

  @BeforeAll
  static void setupBucket() throws Exception {
    final var config =
        new GcsBackupConfig.Builder()
            .withoutAuthentication()
            .withHost(GCS.externalEndpoint())
            .withBucketName(BUCKET_NAME)
            .build();

    try (final var client = GcsBackupStore.buildClient(config)) {
      client.create(BucketInfo.of(BUCKET_NAME));
    }
  }

  @Test
  void missingBackupPreventsSnapshotProgress() {
    // given - some initial processing
    processSomeData();

    // when - taking a snapshot without backup
    partitionsActuator.takeSnapshot();

    // then - snapshot is for the initial index (no progress due to missing backup)
    await("snapshot is taken but doesn't progress beyond initial position")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(() -> assertThat(getSnapshotIndex()).isEqualTo(0));
  }

  @Test
  void successfulBackupEnablesSnapshotProgress() {
    // given - some initial processing
    processSomeData();

    // when - taking a successful backup, then processing a bit more and taking a snapshot
    takeAndAwaitBackup(1L);

    // then - even after a new snapshot is taken, it's index is still the backup index
    partitionsActuator.takeSnapshot();
    await("snapshot is taken with backup position")
        .untilAsserted(() -> assertThat(getSnapshotIndex()).isGreaterThan(0));
  }

  @Test
  void canRestoreFromMultipleBackups() throws IOException {
    // given - three backups with some initial data
    try (final var client = broker.newClientBuilder().build()) {
      final var process = deployTestProcess(client);
      client.newCreateInstanceCommand().processDefinitionKey(process).send().join();
      takeAndAwaitBackup(1L);
      client.newCreateInstanceCommand().processDefinitionKey(process).send().join();
      takeAndAwaitBackup(2L);
      client.newCreateInstanceCommand().processDefinitionKey(process).send().join();
      takeAndAwaitBackup(3L);
      client.newCreateInstanceCommand().processDefinitionKey(process).send().join();
    }

    // when - restoring from all three backups
    broker.stop();
    final var dataDirectory = Path.of(broker.brokerConfig().getData().getDirectory()).getParent();
    FileUtil.deleteFolder(dataDirectory);
    FileUtil.ensureDirectoryExists(dataDirectory);
    final var restore =
        new TestRestoreApp()
            .withBrokerConfig(this::configureBroker)
            .withWorkingDirectory(dataDirectory)
            .withBackupId(1, 2, 3)
            .start();
    restore.close();

    // then - result has the expected data: the first three process instances but not the fourth
    broker.start();
    try (final var client = broker.newClientBuilder().build()) {
      final var jobs =
          client.newActivateJobsCommand().jobType("task").maxJobsToActivate(3).send().join();
      assertThat(jobs.getJobs()).hasSize(3);
      for (final var job : jobs.getJobs()) {
        client.newCompleteCommand(job.getKey()).send().join();
      }
    }
  }

  @Test
  void restoreFailsOnGapsBetweenBackups() throws IOException {
    // given - three backups spanning over multiple segments with snapshots in between
    try (final var client = broker.newClientBuilder().build()) {
      final var process = deployTestProcess(client);

      createManyInstances(client, process);
      takeAndAwaitBackup(1L);

      partitionsActuator.takeSnapshot();
      createManyInstances(client, process);
      takeAndAwaitBackup(2L);

      partitionsActuator.takeSnapshot();
      createManyInstances(client, process);
      takeAndAwaitBackup(3L);
    }

    // when/then - restoring from backup 1 and 3, but skipping backup 2
    broker.stop();
    final var dataDirectory = Path.of(broker.brokerConfig().getData().getDirectory()).getParent();
    FileUtil.deleteFolder(dataDirectory);
    FileUtil.ensureDirectoryExists(dataDirectory);
    final var restore =
        new TestRestoreApp()
            .withBrokerConfig(this::configureBroker)
            .withWorkingDirectory(dataDirectory)
            .withBackupId(1, 3);

    // then restore will fail
    assertThatThrownBy(restore::start)
        .rootCause()
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Cannot find a record at checkpoint position");

    restore.close();
  }

  private static long deployTestProcess(final CamundaClient client) {
    return client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("task"))
                .endEvent()
                .done(),
            "process.bpmn")
        .send()
        .join()
        .getProcesses()
        .getFirst()
        .getProcessDefinitionKey();
  }

  private static void createManyInstances(final CamundaClient client, final long process) {
    for (int i = 0; i < 100; i++) {
      client
          .newCreateInstanceCommand()
          .processDefinitionKey(process)
          .variables(Map.of("test", RandomStringUtils.insecure().nextAlphabetic(100_000)))
          .send()
          .join();
    }
  }

  void takeAndAwaitBackup(final long backupId) {
    backupActuator.take(backupId);
    await("backup is completed")
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(backupActuator.status(backupId).getState())
                    .isEqualTo(StateCode.COMPLETED));
  }

  private long getSnapshotIndex() {
    return FileBasedSnapshotId.ofFileName(partitionsActuator.query().get(1).snapshotId())
        .getOrThrow()
        .getIndex();
  }

  private void processSomeData() {
    try (final var client = broker.newClientBuilder().build()) {
      for (int i = 5; i < 10; i++) {
        client
            .newPublishMessageCommand()
            .messageName("test-message")
            .correlationKey("key-" + i)
            .send()
            .join();
      }
    }
  }

  private void configureBroker(final BrokerCfg cfg) {
    cfg.getExperimental().setContinuousBackups(true);

    final var gcsConfig = new GcsBackupStoreConfig();
    gcsConfig.setAuth(GcsBackupStoreAuth.NONE);
    gcsConfig.setBasePath(basePath);
    gcsConfig.setBucketName(BUCKET_NAME);
    gcsConfig.setHost(GCS.externalEndpoint());
    cfg.getData().getBackup().setGcs(gcsConfig);
    cfg.getData().getBackup().setStore(BackupStoreType.GCS);
    cfg.getData().setLogSegmentSize(DataSize.ofMegabytes(1));
    cfg.getNetwork().setMaxMessageSize(DataSize.ofKilobytes(500));
  }
}
