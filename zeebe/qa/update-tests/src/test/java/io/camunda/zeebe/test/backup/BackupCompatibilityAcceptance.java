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
import io.camunda.zeebe.util.VersionUtil;
import io.zeebe.containers.ZeebeContainer;
import io.zeebe.containers.ZeebeTopologyWaitStrategy;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
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
  Map<String, String> backupStoreEnvVars();

  /**
   * Configures the backup store on the {@link TestRestoreApp} via the unified {@link Camunda}
   * config. Endpoints should use external (host-accessible) addresses.
   */
  void configureBackupStore(Camunda cfg);

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
    try (final var broker = createOldBroker()) {
      broker.start();
      createProcessInstanceWithJob(broker);
      takeBackup(broker, backupId);
    }

    // when -- restore the backup using the current version (in-process)
    final Path restoredDataDir;
    try (final var restoreApp =
        new TestRestoreApp()
            .withUnifiedConfig(this::configureBackupStore)
            .withBackupId(backupId)
            .start()) {
      restoredDataDir = restoreApp.getWorkingDirectory();
    }

    assertThat(restoredDataDir)
        .describedAs("Restore app working directory should be set after start()")
        .isNotNull();

    // then -- start a current-version broker on the restored data and verify the job is available
    verifyRestoredJobCanBeCompleted(restoredDataDir);
  }

  /**
   * Starts a current-version broker on the restored data directory and verifies that the service
   * task job created before the backup can be activated and completed.
   */
  private void verifyRestoredJobCanBeCompleted(final Path restoredDataDir) {
    try (final var broker = createCurrentBroker(restoredDataDir)) {
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
    PartitionsActuator.of(broker).takeSnapshot();
    Awaitility.await("Snapshot is taken")
        .atMost(Duration.ofSeconds(60))
        .until(() -> PartitionsActuator.of(broker).query().get(1).snapshotId(), Objects::nonNull);

    // Use the 8.8 actuator path (/actuator/backups) rather than the current
    // /actuator/backupRuntime
    final var backupActuator =
        BackupActuator.of(
            String.format("http://%s/actuator/backups", broker.getExternalMonitoringAddress()));
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

  private ZeebeContainer createOldBroker() {
    final var broker =
        new ZeebeContainer(
                DockerImageName.parse("camunda/zeebe").withTag(VersionUtil.getPreviousVersion()))
            .withNetwork(getNetwork())
            .withTopologyCheck(new ZeebeTopologyWaitStrategy(1, 1, 1))
            .withEnv("CAMUNDA_DATABASE_TYPE", "NONE")
            .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_TYPE", "NONE")
            .withEnv("CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI", "true");

    // Apply backup store specific env vars
    backupStoreEnvVars().forEach(broker::withEnv);

    // Allow implementations to add bind mounts, dependencies, etc.
    customizeOldBroker(broker);

    return broker;
  }

  @SuppressWarnings("resource")
  private TestStandaloneBroker createCurrentBroker(final Path dataDir) {
    return new TestStandaloneBroker()
        .withUnifiedConfig(this::configureBackupStore)
        .withUnifiedConfig(cfg -> cfg.getSystem().getUpgrade().setEnableVersionCheck(false))
        .withWorkingDirectory(dataDir);
  }

  private static String previousVersion() {
    return VersionUtil.getPreviousVersion();
  }
}
