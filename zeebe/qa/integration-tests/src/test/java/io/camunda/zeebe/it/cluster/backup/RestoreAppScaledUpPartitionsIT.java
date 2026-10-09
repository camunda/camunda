/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.backup;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.atomix.cluster.MemberId;
import io.camunda.configuration.PrimaryStorageBackup.BackupStoreType;
import io.camunda.management.backups.BackupInfo;
import io.camunda.management.backups.StateCode;
import io.camunda.management.backups.TakeBackupRuntimeResponse;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestRestoreApp;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies that the standalone restore application restores a backup taken with fewer partitions
 * than the cluster holds after a scale-up, and leaves the cluster with the backup's partitions.
 *
 * <p>The cluster starts with {@value #BACKUP_PARTITIONS_COUNT} partitions, is backed up, and is
 * then scaled up to {@value #SCALED_PARTITIONS_COUNT}. The restore must bring the topology back to
 * the backup's partitions: holding the scaled-up ones would start partitions that have no data and
 * route requests to them. After the restart, every broker must route new instances over the
 * backup's partitions only, and the jobs of the instances created before the backup must be
 * activatable again.
 */
@ZeebeIntegration
final class RestoreAppScaledUpPartitionsIT {

  private static final int BROKERS_COUNT = 3;
  private static final int BACKUP_PARTITIONS_COUNT = 3;
  private static final int SCALED_PARTITIONS_COUNT = 5;
  private static final long BACKUP_ID = 7;
  private static final String PROCESS_ID = "restore-app-scaled-up-process";
  private static final String JOB_TYPE = "restore-app-scaled-up-job";

  @TempDir private Path backupDir;
  @TempDir private Path dataRoot;

  @Test
  void shouldRestoreOntoEmptyNodesConfiguredForTheScaledUpPartitions() throws IOException {
    // given - nodes that are restored from nothing, still configured for the 5 partitions the
    // cluster was scaled up to
    final var configuredPartitionCount = SCALED_PARTITIONS_COUNT;

    // when / then
    shouldRestoreTheBackupsPartitions(configuredPartitionCount, false);
  }

  @Test
  void shouldRestoreInPlaceWhenTheConfigurationHoldsTheOriginalPartitions() throws IOException {
    // given - nodes that are restored in place, which leaves each node's topology file for the
    // restore to update. The configuration still holds the original 3 partitions, as it does after
    // a dynamic scale-up.
    final var configuredPartitionCount = BACKUP_PARTITIONS_COUNT;

    // when / then
    shouldRestoreTheBackupsPartitions(configuredPartitionCount, true);
  }

  private void shouldRestoreTheBackupsPartitions(
      final int configuredPartitionCount, final boolean inPlace) throws IOException {
    try (final var cluster = buildCluster()) {
      cluster
          .start()
          .awaitCompleteTopology(
              BROKERS_COUNT, BACKUP_PARTITIONS_COUNT, BROKERS_COUNT, Duration.ofSeconds(60));

      // given - a backup with a pending job on each of its partitions
      final List<Long> backedUpInstanceKeys;
      try (final var client = cluster.newClientBuilder().build()) {
        backedUpInstanceKeys =
            InProcessRestoreTestUtil.deployAndCreateInstancesOnEveryPartition(
                client, PROCESS_ID, JOB_TYPE, BACKUP_PARTITIONS_COUNT);
      }
      takeSnapshotOnAllBrokers(cluster);
      takeBackup(BackupActuator.of(cluster.availableGateway()));

      // and - the cluster is scaled up past the backup's partition count
      InProcessRestoreTestUtil.scaleUpPartitions(
          ClusterActuator.of(cluster.availableGateway()), SCALED_PARTITIONS_COUNT, BROKERS_COUNT);

      // when - the stopped cluster is restored on every node, then started again
      cluster.shutdown();
      restoreOnEveryNode(cluster, configuredPartitionCount, inPlace);
      cluster
          .brokers()
          .values()
          .forEach(
              broker ->
                  broker.unifiedConfig().getCluster().setPartitionCount(configuredPartitionCount));
      cluster.start();
      cluster.awaitCompleteTopology(
          BROKERS_COUNT, BACKUP_PARTITIONS_COUNT, BROKERS_COUNT, Duration.ofMinutes(2));

      // then - the cluster holds and routes over the backup's partitions only, on every broker. A
      // restore onto empty nodes generates the topology from the configuration and takes the
      // routing state from the restored data once the cluster is up, so it settles shortly after.
      final var clusterActuator = ClusterActuator.of(cluster.availableGateway());
      Awaitility.await("the cluster routes over the backup's partitions")
          .timeout(Duration.ofSeconds(60))
          .untilAsserted(
              () ->
                  InProcessRestoreTestUtil.assertRoutesOverPartitions(
                      clusterActuator, BACKUP_PARTITIONS_COUNT));
      cluster
          .brokers()
          .values()
          .forEach(
              broker ->
                  InProcessRestoreTestUtil.assertNewInstancesLandOnPartitions(
                      broker, PROCESS_ID, BACKUP_PARTITIONS_COUNT));

      // and - the state from the backup is restored
      try (final var client = cluster.newClientBuilder().build()) {
        InProcessRestoreTestUtil.awaitJobsOfInstancesActivatable(
            client, JOB_TYPE, backedUpInstanceKeys);
      }
    }
  }

  /**
   * Runs the restore application on every node, each against that node's own data directory, either
   * onto an emptied directory or, for {@code inPlace}, replacing the default tenant's data and
   * leaving the node's topology file.
   */
  private void restoreOnEveryNode(
      final TestCluster cluster, final int configuredPartitionCount, final boolean inPlace)
      throws IOException {
    for (final var memberId : cluster.brokers().keySet()) {
      final var dataDirectory = dataDirectoryOf(memberId);
      if (!inPlace) {
        FileUtil.deleteFolderContents(dataDirectory);
      }
      var restoreApp =
          new TestRestoreApp()
              .withUnifiedConfig(
                  config -> {
                    config.getCluster().setNodeId(Integer.parseInt(memberId.id()));
                    config.getCluster().setSize(BROKERS_COUNT);
                    config.getCluster().setReplicationFactor(BROKERS_COUNT);
                    config.getCluster().setPartitionCount(configuredPartitionCount);
                    config.getData().getPrimaryStorage().setDirectory(dataDirectory.toString());
                    configureBackup(config.getData().getPrimaryStorage().getBackup());
                  })
              .withBackupId(BACKUP_ID);
      if (inPlace) {
        // naming the tenant replaces its data in place, leaving the node's topology file
        restoreApp = restoreApp.withPhysicalTenant(DEFAULT_PHYSICAL_TENANT_ID);
      }
      try (final var app = restoreApp) {
        assertThatNoException()
            .describedAs("restore on node %s", memberId.id())
            .isThrownBy(app::start);
      }
    }
  }

  private TestCluster buildCluster() {
    return TestCluster.builder()
        .withBrokersCount(BROKERS_COUNT)
        .withPartitionsCount(BACKUP_PARTITIONS_COUNT)
        .withReplicationFactor(BROKERS_COUNT)
        .withEmbeddedGateway(true)
        .withBrokerConfig(
            (memberId, broker) -> {
              final var cfg = broker.unifiedConfig();
              cfg.getData().getPrimaryStorage().setDirectory(dataDirectoryOf(memberId).toString());
              configureBackup(cfg.getData().getPrimaryStorage().getBackup());
              // Faster gossip so the routing state reaches every broker quickly
              cfg.getCluster().getMembership().setSyncInterval(Duration.ofSeconds(1));
              cfg.getCluster().getMembership().setGossipInterval(Duration.ofMillis(500));
            })
        .build();
  }

  private Path dataDirectoryOf(final MemberId memberId) {
    return dataRoot.resolve("node-" + memberId.id());
  }

  private void configureBackup(final io.camunda.configuration.PrimaryStorageBackup backup) {
    backup.setStore(BackupStoreType.FILESYSTEM);
    backup.getFilesystem().setBasePath(backupDir.toAbsolutePath().toString());
  }

  private void takeSnapshotOnAllBrokers(final TestCluster cluster) {
    cluster
        .brokers()
        .values()
        .forEach(
            broker -> {
              final var partitions = PartitionsActuator.of(broker);
              partitions.takeSnapshot();
              Awaitility.await("snapshot is taken on broker " + broker.nodeId())
                  .atMost(Duration.ofSeconds(60))
                  .untilAsserted(
                      () ->
                          assertThat(partitions.query().values())
                              .allSatisfy(status -> assertThat(status.snapshotId()).isNotNull()));
            });
  }

  private void takeBackup(final BackupActuator actuator) {
    assertThat(actuator.take(BACKUP_ID)).isInstanceOf(TakeBackupRuntimeResponse.class);
    Awaitility.await("until a backup exists with the given ID")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions() // 404 NOT_FOUND throws exception
        .untilAsserted(
            () ->
                assertThat(actuator.status(BACKUP_ID))
                    .extracting(BackupInfo::getBackupId, BackupInfo::getState)
                    .containsExactly(BACKUP_ID, StateCode.COMPLETED));
  }
}
