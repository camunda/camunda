/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.backup;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static io.camunda.configuration.beanoverrides.BrokerBasedPropertiesOverride.RDBMS_EXPORTER_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.application.commons.rdbms.RdbmsDataSources;
import io.camunda.client.CamundaClient;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.Filesystem;
import io.camunda.configuration.PrimaryStorageBackup.BackupStoreType;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.db.rdbms.sql.ExporterPositionMapper;
import io.camunda.db.rdbms.write.RdbmsMapperBundle;
import io.camunda.management.backups.BackupInfo;
import io.camunda.management.backups.PartitionBackupInfo;
import io.camunda.management.backups.StateCode;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.actuator.ExportersActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.unit.DataSize;

/**
 * Shared fixture and test cases for restoring from a time range of backups when RDBMS is configured
 * as the secondary storage. This exercises the RDBMS-aware restore path where {@code
 * ExporterPositionMapper} is used by {@code RestorePointResolver} to determine safe restore points
 * per partition.
 *
 * <p>Every test case here is exercised by two concrete subclasses, one per restore execution path:
 * {@link RdbmsRangeRestoreIT} (a separate, standalone restore application) and {@link
 * InProcessRdbmsRangeRestoreIT} (triggered over a running broker's REST endpoint while it is in
 * {@code RECOVERING} mode). The RDBMS-aware resolution of the restore point from a time range is
 * identical either way; only the execution of the resolved restore plan differs, so subclasses only
 * implement the three restore-execution hook methods below.
 *
 * <p>Uses a filesystem backup store (no containers needed) and H2 in-memory database for RDBMS.
 */
@ZeebeIntegration
abstract class RdbmsRangeRestoreTestBase implements ClockSupport {

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsRangeRestoreTestBase.class);

  // H2 in-memory DB URL. DB_CLOSE_DELAY=-1 keeps the DB alive across a broker's stop/restart
  // cycle within a single test. MODE=PostgreSQL for SQL compatibility with the RDBMS module.
  protected final String h2Url =
      "jdbc:h2:mem:rdbms-restore-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL";

  @TestZeebe
  protected final TestStandaloneBroker broker =
      new TestStandaloneBroker()
          .withSecondaryStorageType(SecondaryStorageType.rdbms)
          .withUnifiedConfig(this::configureBroker);

  private BackupActuator backupActuator;
  private ExporterPositionMapper exporterPositionMapper;

  @BeforeEach
  void setUpBase() {
    backupActuator = BackupActuator.of(broker);
    final Map<String, RdbmsMapperBundle> bundles = broker.bean("rdbmsMapperBundles");
    final var defaultBundle = bundles.get(DEFAULT_PHYSICAL_TENANT_ID);
    if (defaultBundle == null) {
      throw new IllegalStateException(
          "Missing default physical tenant '%s' in rdbmsMapperBundles; known tenants: %s"
              .formatted(DEFAULT_PHYSICAL_TENANT_ID, bundles.keySet()));
    }
    exporterPositionMapper = defaultBundle.exporterPositionMapper();
    pinClock(broker);
  }

  @AfterEach
  void tearDownBase() throws SQLException {
    final var dataSources = broker.bean(RdbmsDataSources.class);
    if (dataSources != null) {
      final var ds = dataSources.dataSourceFor(DEFAULT_PHYSICAL_TENANT_ID);
      try (final var connection = ds.getConnection()) {
        connection.createStatement().execute("DROP ALL OBJECTS");
      }
    }
  }

  @Test
  void shouldNotRestoreIfMissingBackupAfterRestorePosition() throws Exception {
    // given - deploy a process and create instances, then take continuous backups.
    final Interval interval;
    try (final var client = broker.newClientBuilder().build()) {
      final var processKey = deployTestProcess(client);

      // Create some process instances to have data to verify after restore
      interval = createProcessInstancesAndTakeBackups(client, processKey);

      takeAndAwaitBackup();
      progressClock(broker, 2000);
      client.newCreateInstanceCommand().processDefinitionKey(processKey).send().join();
      client.newCreateInstanceCommand().processDefinitionKey(processKey).send().join();
    }

    // when/then - restoring from a range that ends before the backup covering it exists must fail
    assertRestoreFailsForMissingBackup(interval);
  }

  @Test
  void shouldRestoreFromATimeRange() throws Exception {
    // given - some process instances with backups between them
    final long processKey;
    final Interval interval;
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

    // when - restore from the recorded time range
    restoreFromTimeRange(interval);

    // then - jobs from process instances created before the restore window's end are still there,
    // proving the RDBMS-resolved restore point was applied correctly
    completeJobs(4);
  }

  @Test
  void shouldRestoreWithoutArguments() throws Exception {
    // given - deploy a process and create instances, then take continuous backups.
    try (final var client = broker.newClientBuilder().build()) {
      final var processKey = deployTestProcess(client);
      createProcessInstancesAndTakeBackups(client, processKey);
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

    // when - restore without specifying a time range (resolves the latest common checkpoint)
    restoreWithoutArguments();

    // then
    completeJobs(4);
  }

  /**
   * Restores from the given time range and leaves the broker running and ready for verification
   * (e.g. by restarting it, for the standalone path, or by completing an in-process restore change
   * plan and waiting for the broker to return to {@code ACTIVE}).
   */
  abstract void restoreFromTimeRange(Interval interval) throws Exception;

  /** Same as {@link #restoreFromTimeRange(Interval)}, but without an explicit time range. */
  abstract void restoreWithoutArguments() throws Exception;

  /**
   * Attempts a restore for a time range that ends before any backup exists to cover it, and asserts
   * that the attempt fails with a message indicating no usable range was found.
   */
  abstract void assertRestoreFailsForMissingBackup(Interval interval) throws Exception;

  /**
   * The backup store's base directory. Declared as a {@code static @TempDir} field on each concrete
   * subclass (not here) so JUnit injects it before that subclass's {@code broker} field initializer
   * runs {@link #configureBroker}, and so the two subclasses never share one physical temp
   * directory.
   */
  protected abstract Path backupDir();

  protected void completeJobs(final int expectedJobCount) {
    try (final var client = broker.newClientBuilder().build()) {
      int tries = 10;
      int remaining = expectedJobCount;
      while (remaining > 0 && tries-- > 0) {
        final var jobActivationResult =
            client
                .newActivateJobsCommand()
                .jobType("task")
                .maxJobsToActivate(remaining)
                .send()
                .join();
        remaining -= jobActivationResult.getJobs().size();
        for (final var job : jobActivationResult.getJobs()) {
          client.newCompleteCommand(job.getKey()).send().join();
        }
      }
      assertThat(remaining).describedAs("all expected jobs were activated and completed").isZero();
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

  private Interval createProcessInstancesAndTakeBackups(
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
    return new Interval(from, to);
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
    configureRdbms(cfg);
    // Filesystem backup store
    final var fsConfig = new Filesystem();
    fsConfig.setBasePath(backupDir().toAbsolutePath().toString());
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

  protected void configureRdbms(final Camunda cfg) {
    cfg.getData().getSecondaryStorage().setType(SecondaryStorageType.rdbms);
    final var rdbms = cfg.getData().getSecondaryStorage().getRdbms();
    rdbms.setUrl(h2Url);
    rdbms.setUsername("sa");
    rdbms.setPassword("");
  }

  protected record Interval(Instant start, Instant end) {}
}
