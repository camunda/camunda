/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.backup;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.configuration.Camunda;
import io.camunda.management.backups.BackupInfo;
import io.camunda.management.backups.StateCode;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestRestoreApp;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import io.camunda.zeebe.util.VersionUtil;
import io.zeebe.containers.ZeebeContainer;
import io.zeebe.containers.ZeebeTopologyWaitStrategy;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.RandomStringUtils;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

/**
 * Acceptance tests for cross-version backup compatibility. Verifies that backups created by the
 * previous minor version can be found and restored by the current version.
 *
 * <p>The test flow is:
 *
 * <ol>
 *   <li>Start the previous version broker in a Docker container with backup store configured
 *   <li>Deploy a process with a service task, create a process instance
 *   <li>Take a snapshot and trigger a backup via the actuator
 *   <li>Stop the old container
 *   <li>Restore from the backup using the current version's {@link TestRestoreApp} (in-process)
 *   <li>Start a current-version broker on the restored data
 *   <li>Verify the service task job can be activated and completed
 * </ol>
 */
public interface BackupCompatibilityAcceptance {
  String PROCESS_ID = "compat-test-process";
  String JOB_TYPE = "compat-test-task";

  /**
   * Returns the Docker network shared between the storage emulator and the old broker container.
   */
  Network getNetwork();

  /**
   * Returns environment variables to configure the backup store on the old (previous version)
   * broker container. These use the legacy {@code ZEEBE_BROKER_DATA_BACKUP_*} env var path which is
   * compatible with 8.8 and earlier. Endpoints should use internal (container-to-container)
   * addresses.
   */
  Map<String, String> oldBrokerBackupStoreEnvVars(final String storeBasePath);

  /**
   * Configures the backup store on the current version's {@link TestRestoreApp} and {@link
   * TestStandaloneBroker} via the unified {@link Camunda} config. Endpoints should use external
   * (host-accessible) addresses.
   */
  void configureCurrentBackupStore(Camunda cfg, final String storeBasePath);

  /**
   * Optional hook to further customize the old broker container beyond env vars. Implementations
   * can override this to add bind mounts, dependencies, or other container configuration.
   */
  default void customizeOldBroker(final ZeebeContainer broker) {
    // no-op by default
  }

  @Test
  default void shouldRestoreBackupFromPreviousVersion() {
    // given
    final var backupId = 1L;
    final String storeBasePath = RandomStringUtils.insecure().nextAlphabetic(10).toLowerCase();
    try (final var broker = createOldBroker(storeBasePath)) {
      broker.start();
      createProcessInstanceWithJob(broker);
      takeBackup(broker, backupId);
    }

    // when -- restore the backup using the current version (in-process)
    final Path restoredDataDir;
    try (final var restoreApp =
        new TestRestoreApp()
            .withUnifiedConfig(cfg -> configureCurrentBackupStore(cfg, storeBasePath))
            .withBackupId(backupId)
            .start()) {
      restoredDataDir = restoreApp.getWorkingDirectory();
    }

    assertThat(restoredDataDir)
        .describedAs("Restore app working directory should be set after start()")
        .isNotNull();

    // then -- start a current-version broker on the restored data and verify the job is available
    verifyRestoredJobCanBeCompleted(restoredDataDir, storeBasePath);
  }

  @Test
  default void shouldDeleteBackupFromPreviousVersion() {
    // given
    final var backupId = 1L;
    final String storeBasePath = RandomStringUtils.insecure().nextAlphabetic(10).toLowerCase();
    try (final var broker = createOldBroker(storeBasePath)) {
      broker.start();
      createProcessInstanceWithJob(broker);
      takeBackup(broker, backupId);
    }

    try (final var broker =
        new TestStandaloneBroker()
            .withUnifiedConfig(cfg -> configureCurrentBackupStore(cfg, storeBasePath))) {
      broker.start();
      final var backupActuator = BackupActuator.of(broker);
      Awaitility.await("until backup from previous version is visible")
          .atMost(Duration.ofSeconds(30))
          .ignoreExceptions()
          .untilAsserted(
              () ->
                  assertThat(backupActuator.list())
                      .singleElement()
                      .returns(backupId, BackupInfo::getBackupId));

      // when -- delete backup from previous version
      backupActuator.delete(backupId);

      // then
      Awaitility.await("until backup is deleted")
          .ignoreExceptions()
          .atMost(Duration.ofSeconds(30))
          .untilAsserted(() -> assertThat(backupActuator.list()).isEmpty());
    }
  }

  /**
   * Verifies that checkpoint state written by the previous version can be deserialized after
   * restore. This is a regression test for <a
   * href="https://github.com/camunda/camunda/issues/49812">a bug</a> where the {@code timestamp}
   * property was added to {@code CheckpointInfo} without a default value, causing deserialization
   * to fail when reading old-format data that lacks the property.
   *
   * <p>The existing {@link #shouldRestoreBackupFromPreviousVersion()} test does not catch this
   * because its snapshot predates the checkpoint record — the checkpoint info is only ever written
   * by the current version during log replay. This test forces the scenario where the snapshot
   * already contains checkpoint state from the previous version.
   */
  @RegressionTest("https://github.com/camunda/camunda/issues/49812")
  default void shouldRestoreBackupWhenSnapshotContainsCheckpointFromPreviousVersion() {
    // given -- two backups: the first writes checkpoint info to RocksDB, and the
    // second's snapshot captures that old-format checkpoint state
    final var firstBackupId = 1L;
    final var secondBackupId = 2L;
    final String storeBasePath = RandomStringUtils.insecure().nextAlphabetic(10).toLowerCase();
    try (final var broker = createOldBroker(storeBasePath)) {
      broker.start();
      createProcessInstanceWithJob(broker);
      takeBackup(broker, firstBackupId);

      // Checkpoint info from backup #1 is now persisted in RocksDB.
      // Take a second backup whose snapshot includes that state.
      takeBackupWithFreshSnapshot(broker, secondBackupId);
    }

    // when -- restore the second backup using the current version
    final Path restoredDataDir;
    try (final var restoreApp =
        new TestRestoreApp()
            .withUnifiedConfig(cfg -> configureCurrentBackupStore(cfg, storeBasePath))
            .withBackupId(secondBackupId)
            .start()) {
      restoredDataDir = restoreApp.getWorkingDirectory();
    }

    // then -- the current-version broker must deserialize old checkpoint state from the
    // snapshot without errors and replay the remaining log successfully
    verifyRestoredJobCanBeCompleted(restoredDataDir, storeBasePath);
  }

  /**
   * Starts a current-version broker on the restored data directory and verifies that the service
   * task job created before the backup can be activated and completed.
   */
  private void verifyRestoredJobCanBeCompleted(
      final Path restoredDataDir, final String storeBasePath) {
    try (final var broker = createCurrentBroker(restoredDataDir, storeBasePath)) {
      broker.start();
      try (final var client = broker.newClientBuilder().build()) {
        // The broker needs time to replay the log after starting on restored data,
        // so we poll until the job becomes activatable.
        final var jobs =
            Awaitility.await("Job from restored process instance becomes activatable")
                .atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofMillis(500))
                .ignoreExceptions()
                .until(
                    () ->
                        client
                            .newActivateJobsCommand()
                            .jobType(JOB_TYPE)
                            .maxJobsToActivate(1)
                            .timeout(Duration.ofSeconds(30))
                            .send()
                            .join()
                            .getJobs(),
                    j -> !j.isEmpty());
        assertThat(jobs)
            .describedAs("Job from process instance created on version %s", previousVersion())
            .hasSize(1);

        client.newCompleteCommand(jobs.getFirst()).send().join();
      }
    }
  }

  /** Deploys a process with a service task and creates a single instance on the given broker. */
  private void createProcessInstanceWithJob(final ZeebeContainer broker) {
    try (final var client =
        CamundaClient.newClientBuilder().restAddress(broker.getRestAddress()).build()) {
      client
          .newDeployResourceCommand()
          .addProcessModel(
              Bpmn.createExecutableProcess(PROCESS_ID)
                  .startEvent()
                  .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
                  .endEvent()
                  .done(),
              "compat-test.bpmn")
          .send()
          .join();

      client.newCreateInstanceCommand().bpmnProcessId(PROCESS_ID).latestVersion().send().join();
    }
  }

  /** Takes a snapshot and backup on the given broker, waiting for both to complete. */
  private void takeBackup(final ZeebeContainer broker, final long backupId) {
    final var partitionsActuator = PartitionsActuator.of(broker);
    partitionsActuator.takeSnapshot();
    Awaitility.await("Snapshot is taken")
        .atMost(Duration.ofSeconds(60))
        .until(() -> partitionsActuator.query().get(1).snapshotId(), Objects::nonNull);

    // Initiate additional processing to progress logstream so that backup doesn't fail
    createProcessInstanceWithJob(broker);

    final var backupActuator = BackupActuator.of(broker);
    backupActuator.take(backupId);

    Awaitility.await("until backup is completed")
        .atMost(Duration.ofSeconds(120))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var status = backupActuator.status(backupId);
              assertThat(status)
                  .describedAs("Backup status (failureReason=%s)", status.getFailureReason())
                  .returns(StateCode.COMPLETED, BackupInfo::getState);
            });
  }

  /**
   * Takes a backup after forcing a fresh snapshot, ensuring the snapshot captures the current
   * RocksDB state (including any checkpoint info written by prior backups). Unlike {@link
   * #takeBackup}, this waits for the snapshot ID to actually change before proceeding.
   */
  private void takeBackupWithFreshSnapshot(final ZeebeContainer broker, final long backupId) {
    final var partitionsActuator = PartitionsActuator.of(broker);
    final var previousSnapshotId = partitionsActuator.query().get(1).snapshotId();

    partitionsActuator.takeSnapshot();
    Awaitility.await("Fresh snapshot capturing checkpoint state")
        .atMost(Duration.ofSeconds(60))
        .until(
            () -> partitionsActuator.query().get(1).snapshotId(),
            id -> id != null && !id.equals(previousSnapshotId));

    // Progress the logstream past the snapshot position
    createProcessInstanceWithJob(broker);

    final var backupActuator = BackupActuator.of(broker);
    backupActuator.take(backupId);

    Awaitility.await("until backup is completed")
        .atMost(Duration.ofSeconds(120))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var status = backupActuator.status(backupId);
              assertThat(status)
                  .describedAs("Backup status (failureReason=%s)", status.getFailureReason())
                  .returns(StateCode.COMPLETED, BackupInfo::getState);
            });
  }

  private ZeebeContainer createOldBroker(final String storeBasePath) {
    final var broker =
        new ZeebeContainer(
                DockerImageName.parse("camunda/zeebe").withTag(VersionUtil.getPreviousVersion()))
            .withNetwork(getNetwork())
            .withTopologyCheck(new ZeebeTopologyWaitStrategy(1, 1, 1))
            .withEnv("CAMUNDA_DATABASE_TYPE", "NONE")
            .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_TYPE", "NONE")
            .withEnv("CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI", "true");

    // Apply backup store specific env vars
    oldBrokerBackupStoreEnvVars(storeBasePath).forEach(broker::withEnv);

    // Allow implementations to add bind mounts, dependencies, etc.
    customizeOldBroker(broker);

    return broker;
  }

  @SuppressWarnings("resource")
  private TestStandaloneBroker createCurrentBroker(final Path dataDir, final String storeBasePath) {
    return new TestStandaloneBroker()
        .withUnifiedConfig(cfg -> configureCurrentBackupStore(cfg, storeBasePath))
        .withUnifiedConfig(cfg -> cfg.getSystem().getUpgrade().setEnableVersionCheck(false))
        .withWorkingDirectory(dataDir);
  }

  private static String previousVersion() {
    return VersionUtil.getPreviousVersion();
  }
}
