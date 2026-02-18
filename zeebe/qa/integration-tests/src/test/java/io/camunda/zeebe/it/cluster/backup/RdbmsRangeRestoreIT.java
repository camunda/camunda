/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.backup;

import static io.camunda.configuration.beanoverrides.BrokerBasedPropertiesOverride.RDBMS_EXPORTER_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.Filesystem;
import io.camunda.configuration.PrimaryStorageBackup.BackupStoreType;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.db.rdbms.sql.ExporterPositionMapper;
import io.camunda.management.backups.BackupInfo;
import io.camunda.management.backups.PartitionBackupInfo;
import io.camunda.management.backups.StateCode;
import io.camunda.zeebe.backup.api.Interval;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.actuator.ExportersActuator;
import io.camunda.zeebe.qa.util.cluster.TestRestoreApp;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.util.FileUtil;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.unit.DataSize;

/**
 * Integration test that verifies restoring from a time range of backups when RDBMS is configured as
 * the secondary storage. This exercises the RDBMS-aware restore path in {@code RestoreManager}
 * where {@code ExporterPositionMapper} is used by {@code BackupRangeResolver} to determine safe
 * restore points per partition.
 *
 * <p>Uses a filesystem backup store (no containers needed) and H2 in-memory database for RDBMS.
 */
@ZeebeIntegration
final class RdbmsRangeRestoreIT implements ClockSupport {

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsRangeRestoreIT.class);
  // H2 in-memory DB URL shared across broker and restore app JVM lifecycles.
  // DB_CLOSE_DELAY=-1 keeps the DB alive after last connection closes.
  // This is necessary so that the broker can restart without losing the exported data
  // MODE=PostgreSQL for SQL compatibility with the RDBMS module.
  private static final String H2_URL =
      "jdbc:h2:mem:rdbms-restore-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL";

  private static @TempDir Path backupDir;
  private static final Map<String, Object> H2_PROPERTIES =
      Map.of(
          "camunda.data.secondary-storage.type",
          "rdbms",
          "spring.datasource.url",
          H2_URL,
          "spring.datasource.driver-class-name",
          "org.h2.Driver",
          "spring.datasource.username",
          "sa",
          "spring.datasource.password",
          "");
  private Path workingDirectory;

  @TestZeebe
  private final TestStandaloneBroker broker =
      new TestStandaloneBroker()
          .withSecondaryStorageType(SecondaryStorageType.rdbms)
          .withAdditionalProperties(H2_PROPERTIES)
          .withUnifiedConfig(this::configureBroker);

  private BackupActuator backupActuator;
  private ExporterPositionMapper exporterPositionMapper;

  @BeforeEach
  void setUp() {
    backupActuator = BackupActuator.of(broker);
    workingDirectory = broker.getWorkingDirectory();
    exporterPositionMapper = broker.bean(ExporterPositionMapper.class);
    pinClock(broker);
  }

  @AfterEach
  void tearDown() throws SQLException {
    final var ds = broker.bean(DataSource.class);
    if (ds != null) {
      try (final var connection = ds.getConnection()) {
        connection.createStatement().execute("DROP ALL OBJECTS");
      }
    }
  }

  @Test
  void shouldNotRestoreIfMissingBackupAfterRestorePosition() throws Exception {
    // given - deploy a process and create instances, then take continuous backups.
    final long processKey;

    final Interval<Instant> interval;
    try (final var client = broker.newClientBuilder().build()) {
      processKey = deployTestProcess(client);

      // Create some process instances to have data to verify after restore
      interval = createProcessInstancesAndTakeBackups(client, processKey);

      takeAndAwaitBackup();
      progressClock(broker, 2000);
      client.newCreateInstanceCommand().processDefinitionKey(processKey).send().join();
      client.newCreateInstanceCommand().processDefinitionKey(processKey).send().join();
    }

    // when - stop broker, delete data, restore from time range with RDBMS
    broker.stop();
    FileUtil.deleteFolder(workingDirectory);
    FileUtil.ensureDirectoryExists(workingDirectory);

    // then
    try (final var restoreApp = testRestoreApp(interval)) {
      assertThatThrownBy(restoreApp::start)
          .rootCause()
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("is less than exporter position");
    }
  }

  @Test
  void shouldRestoreFromATimeRange() throws Exception {
    // given - some process instances with backups between them
    final long processKey;

    final Interval<Instant> interval;
    try (final var client = broker.newClientBuilder().build()) {
      processKey = deployTestProcess(client);
      interval = createProcessInstancesAndTakeBackups(client, processKey);
    }
    takeAndAwaitBackup();

    final var exporterActuator = ExportersActuator.of(broker);
    exporterActuator.disableExporter(RDBMS_EXPORTER_NAME);

    Awaitility.await("Until backup is greater or equal to exported position")
        .pollDelay(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(300))
        .untilAsserted(
            () -> {
              final var backup = takeAndAwaitBackup();
              final var position = exporterPositionMapper.findOne(1).lastExportedPosition();
              final var details = backup.getDetails().getFirst();
              assertThat(details)
                  .returns(1, PartitionBackupInfo::getPartitionId)
                  .returns(StateCode.COMPLETED, PartitionBackupInfo::getState);
              assertThat(details.getCheckpointPosition()).isGreaterThanOrEqualTo(position);
            });

    // when - stop broker, delete data, restore from time range with RDBMS
    broker.stop();

    FileUtil.deleteFolder(workingDirectory);
    FileUtil.ensureDirectoryExists(workingDirectory);
    try (final var restore = testRestoreApp(interval)) {
      restore.start();
    }

    broker.start();

    try (final var client = broker.newClientBuilder().build()) {
      final var jobActivationResult =
          client.newActivateJobsCommand().jobType("task").maxJobsToActivate(10).send().join();

      assertThat(jobActivationResult.getJobs()).hasSize(4);

      for (final var job : jobActivationResult.getJobs()) {
        client.newCompleteCommand(job.getKey()).send().join();
      }
    }
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

  private Interval<Instant> createProcessInstancesAndTakeBackups(
      final CamundaClient client, final long processKey) {

    // Create some process instances to have data to verify after restore
    client.newCreateInstanceCommand().processDefinitionKey(processKey).send().join();
    client.newCreateInstanceCommand().processDefinitionKey(processKey).send().join();

    takeAndAwaitBackup();
    // Record "from" before the first backup, with a small sleep to create clear separation

    final var from = currentTime(broker);
    LOG.info("Time range from = {}", from);

    client.newCreateInstanceCommand().processDefinitionKey(processKey).send().join();
    // Take first backup
    takeAndAwaitBackup();

    // Create another instance and take second backup
    client.newCreateInstanceCommand().processDefinitionKey(processKey).send().join();
    takeAndAwaitBackup();

    // Record "to" after second backup
    final Instant to = currentTime(broker);
    LOG.info("Time range to = {}", to);
    return Interval.closed(from, to);
  }

  private BackupInfo takeAndAwaitBackup() {
    progressClock(broker, 2000);
    final var res = backupActuator.take();
    final long backupId = Optional.ofNullable(res.getBackupId()).orElseThrow();

    await("backup is completed")
        .timeout(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(backupActuator.status(backupId).getState())
                    .isEqualTo(StateCode.COMPLETED));

    return backupActuator.status(backupId);
  }

  private void configureBroker(final Camunda cfg) {
    cfg.getSystem().setClockControlled(true);
    // Filesystem backup store
    final var fsConfig = new Filesystem();
    fsConfig.setBasePath(backupDir.toAbsolutePath().toString());
    cfg.getData().getPrimaryStorage().getBackup().setFilesystem(fsConfig);
    cfg.getData().getPrimaryStorage().getBackup().setStore(BackupStoreType.FILESYSTEM);

    // Enable continuous backups
    cfg.getData().getPrimaryStorage().getBackup().setContinuous(true);

    // Use smaller log segments for faster checkpoint creation.
    // Max message size must be <= log segment size (enforced by RaftPartitionFactory).
    cfg.getData().getPrimaryStorage().getLogStream().setLogSegmentSize(DataSize.ofMegabytes(1));
    cfg.getCluster().getNetwork().setMaxMessageSize(DataSize.ofKilobytes(500));

    // Disable auto-configure camunda exporter to avoid conflicts
    cfg.getData().getSecondaryStorage().setAutoconfigureCamundaExporter(false);
  }

  private void configureRestoreApp(final Camunda cfg) {
    // Filesystem backup store (same as broker)
    cfg.getData().getSecondaryStorage().setType(SecondaryStorageType.rdbms);
    final var fsConfig = new Filesystem();
    fsConfig.setBasePath(backupDir.toAbsolutePath().toString());
    cfg.getData().getPrimaryStorage().getBackup().setFilesystem(fsConfig);
    cfg.getData().getPrimaryStorage().getBackup().setStore(BackupStoreType.FILESYSTEM);
  }

  @SuppressWarnings("resource")
  private TestRestoreApp testRestoreApp(final Interval<Instant> interval) {
    return new TestRestoreApp()
        .withUnifiedConfig(this::configureRestoreApp)
        .withAdditionalProperties(H2_PROPERTIES)
        .withWorkingDirectory(workingDirectory)
        .withTimeRange(interval.start(), interval.end());
  }

  @SuppressWarnings("resource")
  private TestRestoreApp testRestoreApp() {
    return new TestRestoreApp()
        .withUnifiedConfig(this::configureRestoreApp)
        .withAdditionalProperties(H2_PROPERTIES)
        .withWorkingDirectory(workingDirectory);
  }
}
