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

import com.fasterxml.jackson.databind.ObjectMapper;
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
import io.camunda.zeebe.management.cluster.BrokerState;
import io.camunda.zeebe.management.cluster.PartitionState;
import io.camunda.zeebe.management.cluster.PartitionStateCode;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.actuator.ExportersActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.qa.util.topology.ClusterActuatorAssert;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.unit.DataSize;

/**
 * Acceptance test for restoring from an RDBMS time range ({@code from}/{@code to} instead of
 * explicit {@code backupIds}) in-process, i.e. while a running broker is in {@code RECOVERING}
 * mode, as opposed to {@link RdbmsRangeRestoreIT} which restores via a separate, standalone restore
 * application.
 *
 * <p>The RDBMS-aware resolution of the restore point from a time range (via {@code
 * RestorePointResolver}/{@code RestoreValidator}) is exercised identically to {@link
 * RdbmsRangeRestoreIT}; only the execution of the resolved restore plan differs here (in-process
 * instead of {@code TestRestoreApp}).
 */
@ZeebeIntegration
final class InProcessRdbmsRangeRestoreIT implements ClockSupport {

  private static final Logger LOG = LoggerFactory.getLogger(InProcessRdbmsRangeRestoreIT.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  // H2 in-memory DB URL. DB_CLOSE_DELAY=-1 keeps the DB alive after last connection closes.
  // MODE=PostgreSQL for SQL compatibility with the RDBMS module.
  private static final String H2_URL =
      "jdbc:h2:mem:rdbms-restore-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL";

  private static @TempDir Path backupDir;

  @TestZeebe
  private final TestStandaloneBroker broker =
      new TestStandaloneBroker()
          .withSecondaryStorageType(SecondaryStorageType.rdbms)
          .withUnifiedConfig(this::configureBroker);

  private BackupActuator backupActuator;
  private ExporterPositionMapper exporterPositionMapper;

  @BeforeEach
  void setUp() {
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
  void tearDown() throws SQLException {
    final var dataSources = broker.bean(RdbmsDataSources.class);
    if (dataSources != null) {
      final var ds = dataSources.dataSourceFor(DEFAULT_PHYSICAL_TENANT_ID);
      try (final var connection = ds.getConnection()) {
        connection.createStatement().execute("DROP ALL OBJECTS");
      }
    }
  }

  @Test
  void shouldRestoreFromATimeRangeInProcess() throws Exception {
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

    // when - the broker is put into RECOVERING mode
    final var clusterActuator = ClusterActuator.of(broker);
    final var toRecovering = clusterActuator.updateMode("RECOVERING", false);
    awaitChangeCompletes(
        clusterActuator, toRecovering.getChangeId(), "broker transitions to RECOVERING");

    // and - a range restore is triggered over the broker's REST endpoint while recovering
    final long changeId;
    try (final var client = broker.newClientBuilder().build()) {
      changeId = triggerRangeRestore(client, interval);
    }

    // then - the restore change plan completes
    awaitChangeCompletes(clusterActuator, changeId, "restore change plan completes");

    // and - the broker reports ACTIVE again
    Awaitility.await("broker reports ACTIVE again")
        .timeout(Duration.ofSeconds(60))
        .untilAsserted(
            () -> {
              final var topology = clusterActuator.getTopology();
              assertThat(topology.getBrokers())
                  .flatExtracting(BrokerState::getPartitions)
                  .extracting(PartitionState::getState)
                  .allMatch(state -> state == PartitionStateCode.ACTIVE);
            });

    // and - jobs from process instances created before the restore window's end are still there,
    // proving the RDBMS-resolved restore point was applied correctly
    completeJobs(4);
  }

  /**
   * Awaits a cluster configuration change plan completing. The broker's system clock is pinned (see
   * {@link #configureBroker}), so the {@code ClusterConfigurationManager}'s internal retry backoff
   * (used e.g. while polling for a partition to settle into its new role after a mode change) will
   * not elapse on its own; this progresses the clock on every poll so any such scheduled retry gets
   * a chance to fire.
   */
  private void awaitChangeCompletes(
      final ClusterActuator clusterActuator, final long changeId, final String alias) {
    Awaitility.await(alias)
        .timeout(Duration.ofSeconds(90))
        .pollInterval(Duration.ofSeconds(2))
        .untilAsserted(
            () -> {
              progressClock(broker, 3000);
              ClusterActuatorAssert.assertThat(clusterActuator)
                  .hasCompletedChanges(changeId)
                  .doesNotHavePendingChanges();
            });
  }

  private long triggerRangeRestore(final CamundaClient client, final Interval interval) {
    try {
      final var uri =
          URI.create(
              "%sv2/restore?dryRun=false".formatted(client.getConfiguration().getRestAddress()));
      final var body =
          OBJECT_MAPPER.writeValueAsString(
              Map.of("from", interval.start().toString(), "to", interval.end().toString()));
      final var request =
          HttpRequest.newBuilder(uri)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .build();
      final var response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode())
          .describedAs("restore REST response: %s".formatted(response.body()))
          .isEqualTo(202);
      return OBJECT_MAPPER.readTree(response.body()).get("changeId").asLong();
    } catch (final IOException | InterruptedException e) {
      throw new RuntimeException("Failed to trigger restore via REST endpoint", e);
    }
  }

  private void completeJobs(final int expectedJobCount) {
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

  private static void configureRdbms(final Camunda cfg) {
    cfg.getData().getSecondaryStorage().setType(SecondaryStorageType.rdbms);
    final var rdbms = cfg.getData().getSecondaryStorage().getRdbms();
    rdbms.setUrl(H2_URL);
    rdbms.setUsername("sa");
    rdbms.setPassword("");
  }

  private record Interval(Instant start, Instant end) {}
}
