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

import io.camunda.application.commons.rdbms.RdbmsDataSources;
import io.camunda.configuration.PrimaryStorageBackup.BackupStoreType;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.db.rdbms.write.RdbmsMapperBundle;
import io.camunda.management.backups.StateCode;
import io.camunda.zeebe.management.cluster.BrokerState;
import io.camunda.zeebe.management.cluster.PartitionState;
import io.camunda.zeebe.management.cluster.PartitionStateCode;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.actuator.ExportersActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.topology.ClusterActuatorAssert;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Restores from a time range of backups in-process, i.e. triggered over a running broker's REST
 * endpoint while it is in {@code RECOVERING} mode. See {@link RdbmsRangeRestoreTestBase} for the
 * shared fixture and test cases, and {@link RdbmsRangeRestoreIT} for the standalone counterpart.
 */
final class InProcessRdbmsRangeRestoreIT extends RdbmsRangeRestoreTestBase {

  private static final int BACKUP_PARTITIONS_COUNT = 3;
  private static final int SCALED_PARTITIONS_COUNT = 5;
  private static final String SCALED_PROCESS_ID = "rdbms-scaled-restore-process";
  private static final String SCALED_JOB_TYPE = "rdbms-scaled-restore-job";

  private static @TempDir Path backupDir;

  @Test
  void shouldInvokeLiquibaseDuringPrimaryRestore() throws Exception {
    // given
    final Interval interval;
    try (final var client = broker.newClientBuilder().build()) {
      final var processKey = deployTestProcess(client);
      interval = createProcessInstancesAndTakeBackups(client, processKey);
    }
    takeAndAwaitBackup();
    ExportersActuator.of(broker).disableExporter(RDBMS_EXPORTER_NAME);
    awaitBackupCoversExportedPositionsOnEveryPartition();
    final var clusterActuator = enterRecovering();
    final var dataSource =
        broker.bean(RdbmsDataSources.class).dataSourceFor(DEFAULT_PHYSICAL_TENANT_ID);

    try (final var connection = dataSource.getConnection();
        final var statement = connection.createStatement()) {
      assertThat(deployedResourceTableExists(connection)).isTrue();
      statement.execute("DROP TABLE DEPLOYED_RESOURCE");
      // Liquibase only reapplies the table's changeset when its execution record is absent.
      assertThat(
              statement.executeUpdate(
                  "DELETE FROM DATABASECHANGELOG WHERE ID = 'create_deployed_resource_table'"
                      + " AND AUTHOR = 'camunda'"
                      + " AND FILENAME = 'db/changelog/rdbms-exporter/changesets/8.10.0.xml'"))
          .isOne();
      assertThat(statement.executeUpdate("DELETE FROM DATABASECHANGELOGLOCK")).isOne();
      connection.commit();
      assertThat(deployedResourceTableExists(connection)).isFalse();
    }

    // when
    try (final var client = broker.newClientBuilder().build()) {
      final var changeId =
          InProcessRestoreTestUtil.triggerRestore(
              client, Map.of("from", interval.start().toString(), "to", interval.end().toString()));
      awaitChangeCompletesAndBrokerActive(clusterActuator, changeId);
    }

    // then
    try (final var connection = dataSource.getConnection()) {
      assertThat(deployedResourceTableExists(connection)).isTrue();
    }
  }

  @Test
  void shouldRestoreTheBackupsPartitionsAfterAScaleUp() throws Exception {
    // given - a broker with 3 partitions, of its own since the shared fixture has 2, with a backup
    // covering the exported positions of all 3
    final var h2Url =
        "jdbc:h2:mem:rdbms-scaled-restore-"
            + UUID.randomUUID()
            + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL";
    try (final var scaledBroker =
        new TestStandaloneBroker()
            .withRecordingExporter(true)
            .withSecondaryStorageType(SecondaryStorageType.rdbms)
            .withUnifiedConfig(
                cfg -> {
                  cfg.getCluster().setPartitionCount(BACKUP_PARTITIONS_COUNT);
                  final var rdbms = cfg.getData().getSecondaryStorage().getRdbms();
                  rdbms.setUrl(h2Url);
                  rdbms.setUsername("sa");
                  rdbms.setPassword("");
                  cfg.getData().getSecondaryStorage().setAutoconfigureCamundaExporter(false);
                  final var backup = cfg.getData().getPrimaryStorage().getBackup();
                  backup.setStore(BackupStoreType.FILESYSTEM);
                  backup
                      .getFilesystem()
                      .setBasePath(backupDir.resolve(UUID.randomUUID().toString()).toString());
                  backup.setContinuous(true);
                })
            .start()
            .awaitCompleteTopology(1, BACKUP_PARTITIONS_COUNT, 1, Duration.ofSeconds(60))) {
      final List<Long> backedUpInstanceKeys;
      try (final var client = scaledBroker.newClientBuilder().build()) {
        backedUpInstanceKeys =
            InProcessRestoreTestUtil.deployAndCreateInstancesOnEveryPartition(
                client, SCALED_PROCESS_ID, SCALED_JOB_TYPE, BACKUP_PARTITIONS_COUNT);
      }
      ExportersActuator.of(scaledBroker).disableExporter(RDBMS_EXPORTER_NAME);
      awaitBackupCoveringExportedPositions(scaledBroker);

      // and - the cluster is scaled up to 5 partitions
      final var clusterActuator = ClusterActuator.of(scaledBroker);
      InProcessRestoreTestUtil.scaleUpPartitions(clusterActuator, SCALED_PARTITIONS_COUNT, 1);

      try (final var client = scaledBroker.newClientBuilder().build()) {
        final var toRecovering = InProcessRestoreTestUtil.changeMode(client, "RECOVERING", false);
        awaitScaledChangeCompletes(clusterActuator, toRecovering);

        // and - the RDBMS is restored to the backup's point, so it only holds exported positions
        // for the backup's 3 partitions, not for the 2 added by the scale-up
        final var dataSource =
            scaledBroker.bean(RdbmsDataSources.class).dataSourceFor(DEFAULT_PHYSICAL_TENANT_ID);
        try (final var connection = dataSource.getConnection();
            final var statement = connection.createStatement()) {
          statement.executeUpdate(
              "DELETE FROM EXPORTER_POSITION WHERE PARTITION_ID > " + BACKUP_PARTITIONS_COUNT);
          connection.commit();
        }

        // when - restoring the latest common checkpoint of the partitions the RDBMS holds
        final var restore = InProcessRestoreTestUtil.triggerRestore(client, Map.of());
        awaitScaledChangeCompletes(clusterActuator, restore);

        // then - the group routes over the backup's partitions again
        InProcessRestoreTestUtil.assertRoutesOverPartitions(
            clusterActuator, BACKUP_PARTITIONS_COUNT);
        InProcessRestoreTestUtil.assertNewInstancesLandOnPartitions(
            scaledBroker, SCALED_PROCESS_ID, BACKUP_PARTITIONS_COUNT);

        // and - the backup's partitions are restored and processing
        InProcessRestoreTestUtil.awaitJobsOfInstancesActivatable(
            client, SCALED_JOB_TYPE, backedUpInstanceKeys);
      }
    }
  }

  private static void awaitBackupCoveringExportedPositions(final TestStandaloneBroker broker) {
    final Map<String, RdbmsMapperBundle> bundles = broker.bean("rdbmsMapperBundles");
    final var exporterPositions = bundles.get(DEFAULT_PHYSICAL_TENANT_ID).exporterPositionMapper();
    final var backupActuator = BackupActuator.of(broker);
    Awaitility.await("a backup covers the exported position on every partition")
        .atMost(Duration.ofMinutes(2))
        .pollInterval(Duration.ofSeconds(2))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final long backupId = backupActuator.take().getBackupId();
              Awaitility.await("backup %d completes".formatted(backupId))
                  .atMost(Duration.ofSeconds(30))
                  .ignoreExceptions()
                  .untilAsserted(
                      () ->
                          assertThat(backupActuator.status(backupId).getState())
                              .isEqualTo(StateCode.COMPLETED));
              assertThat(backupActuator.status(backupId).getDetails())
                  .hasSize(BACKUP_PARTITIONS_COUNT)
                  .allSatisfy(
                      details ->
                          assertThat(details.getCheckpointPosition())
                              .isGreaterThanOrEqualTo(
                                  exporterPositions
                                      .findOne(details.getPartitionId())
                                      .lastExportedPosition()));
            });
  }

  private static void awaitScaledChangeCompletes(
      final ClusterActuator clusterActuator, final long changeId) {
    Awaitility.await("cluster configuration change %d completes".formatted(changeId))
        .timeout(Duration.ofMinutes(2))
        .untilAsserted(
            () ->
                ClusterActuatorAssert.assertThat(clusterActuator)
                    .hasCompletedChanges(changeId)
                    .doesNotHavePendingChanges());
  }

  private static boolean deployedResourceTableExists(final Connection connection)
      throws SQLException {
    try (final var tables =
        connection
            .getMetaData()
            .getTables(null, "PUBLIC", "DEPLOYED_RESOURCE", new String[] {"TABLE"})) {
      return tables.next();
    }
  }

  @Override
  void restoreFromTimeRange(final Interval interval) throws Exception {
    final var clusterActuator = enterRecovering();
    final long changeId;
    try (final var client = broker.newClientBuilder().build()) {
      changeId =
          InProcessRestoreTestUtil.triggerRestore(
              client, Map.of("from", interval.start().toString(), "to", interval.end().toString()));
    }
    awaitChangeCompletesAndBrokerActive(clusterActuator, changeId);
  }

  @Override
  void restoreWithoutArguments() throws Exception {
    final var clusterActuator = enterRecovering();
    final long changeId;
    try (final var client = broker.newClientBuilder().build()) {
      changeId = InProcessRestoreTestUtil.triggerRestore(client, Map.of());
    }
    awaitChangeCompletesAndBrokerActive(clusterActuator, changeId);
  }

  @Override
  void assertRestoreFailsForMissingBackup(final Interval interval) throws Exception {
    enterRecovering();
    try (final var client = broker.newClientBuilder().build()) {
      final var response =
          InProcessRestoreTestUtil.sendRestoreRequest(
              client, Map.of("from", interval.start().toString(), "to", interval.end().toString()));
      assertThat(response.statusCode())
          .describedAs("restore REST response: %s".formatted(response.body()))
          .isEqualTo(409);
      assertThat(response.body()).contains("No usable range found");
    }
  }

  @Override
  protected Path backupDir() {
    return backupDir;
  }

  private ClusterActuator enterRecovering() {
    final var clusterActuator = ClusterActuator.of(broker);
    final long toRecovering;
    try (final var client = broker.newClientBuilder().build()) {
      toRecovering = InProcessRestoreTestUtil.changeMode(client, "RECOVERING", false);
    }
    awaitChangeCompletes(clusterActuator, toRecovering, "broker transitions to RECOVERING");
    return clusterActuator;
  }

  private void awaitChangeCompletesAndBrokerActive(
      final ClusterActuator clusterActuator, final long changeId) {
    awaitChangeCompletes(clusterActuator, changeId, "restore change plan completes");

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
  }

  /**
   * Awaits a cluster configuration change plan completing. The broker's system clock is pinned (see
   * {@code RdbmsRangeRestoreTestBase#configureBroker}), so the {@code
   * ClusterConfigurationManager}'s internal retry backoff (used e.g. while polling for a partition
   * to settle into its new role after a mode change) will not elapse on its own; this progresses
   * the clock on every poll so any such scheduled retry gets a chance to fire.
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
}
