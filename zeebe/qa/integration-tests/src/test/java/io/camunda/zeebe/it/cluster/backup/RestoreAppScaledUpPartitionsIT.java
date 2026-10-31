/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.atomix.cluster.MemberId;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.configuration.PrimaryStorageBackup;
import io.camunda.configuration.PrimaryStorageBackup.BackupStoreType;
import io.camunda.management.backups.BackupInfo;
import io.camunda.management.backups.StateCode;
import io.camunda.management.backups.TakeBackupRuntimeResponse;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequest;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequestPartitions;
import io.camunda.zeebe.management.cluster.RequestHandlingAllPartitions;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestGateway;
import io.camunda.zeebe.qa.util.cluster.TestRestoreApp;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.topology.ClusterActuatorAssert;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies that the restore application restores a backup taken with fewer partitions than the
 * cluster holds after a scale-up, and leaves the cluster with the backup's partitions.
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
    try (final var cluster = buildCluster()) {
      cluster
          .start()
          .awaitCompleteTopology(
              BROKERS_COUNT, BACKUP_PARTITIONS_COUNT, BROKERS_COUNT, Duration.ofSeconds(60));

      // given - a backup with a pending job on each of its partitions
      final List<Long> backedUpInstanceKeys;
      try (final var client = cluster.newClientBuilder().build()) {
        backedUpInstanceKeys = deployAndCreateInstancesOnEveryPartition(client);
      }
      takeSnapshotOnAllBrokers(cluster);
      takeBackup(BackupActuator.of(cluster.availableGateway()));

      // and - the cluster is scaled up past the backup's partition count
      scaleUpPartitions(ClusterActuator.of(cluster.availableGateway()));

      // when - the stopped cluster is restored onto empty nodes, still configured for the 5
      // partitions the cluster was scaled up to, then started again
      cluster.shutdown();
      restoreOnEveryNode(cluster);
      cluster
          .brokers()
          .values()
          .forEach(
              broker ->
                  broker.unifiedConfig().getCluster().setPartitionCount(SCALED_PARTITIONS_COUNT));
      cluster.start();
      cluster.awaitCompleteTopology(
          BROKERS_COUNT, BACKUP_PARTITIONS_COUNT, BROKERS_COUNT, Duration.ofMinutes(2));

      // then - the cluster holds and routes over the backup's partitions only, on every broker. The
      // topology is generated for the backup's partitions and takes the routing state from the
      // restored data once the cluster is up, so it settles shortly after.
      final var clusterActuator = ClusterActuator.of(cluster.availableGateway());
      Awaitility.await("the cluster routes over the backup's partitions")
          .timeout(Duration.ofSeconds(60))
          .untilAsserted(
              () ->
                  assertThat(clusterActuator.getTopology().getRouting().getRequestHandling())
                      .isEqualTo(requestHandlingOver(BACKUP_PARTITIONS_COUNT)));
      cluster
          .brokers()
          .values()
          .forEach(broker -> assertNewInstancesLandOnTheBackupsPartitions(broker));

      // and - the state from the backup is restored
      try (final var client = cluster.newClientBuilder().build()) {
        awaitJobsOfInstancesActivatable(client, backedUpInstanceKeys);
      }
    }
  }

  /** Runs the restore application on every node against that node's emptied data directory. */
  private void restoreOnEveryNode(final TestCluster cluster) throws IOException {
    for (final var memberId : cluster.brokers().keySet()) {
      final var dataDirectory = dataDirectoryOf(memberId);
      FileUtil.deleteFolderContents(dataDirectory);
      try (final var app =
          new TestRestoreApp()
              .withUnifiedConfig(
                  config -> {
                    config.getCluster().setNodeId(Integer.parseInt(memberId.id()));
                    config.getCluster().setSize(BROKERS_COUNT);
                    config.getCluster().setReplicationFactor(BROKERS_COUNT);
                    config.getCluster().setPartitionCount(SCALED_PARTITIONS_COUNT);
                    config.getData().getPrimaryStorage().setDirectory(dataDirectory.toString());
                    configureBackup(config.getData().getPrimaryStorage().getBackup());
                  })
              .withBackupId(BACKUP_ID)) {
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

  private void configureBackup(final PrimaryStorageBackup backup) {
    backup.setStore(BackupStoreType.FILESYSTEM);
    backup.getFilesystem().setBasePath(backupDir.toAbsolutePath().toString());
  }

  private static List<Long> deployAndCreateInstancesOnEveryPartition(final CamundaClient client) {
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
            .endEvent()
            .done();
    final var deploymentKey =
        client
            .newDeployResourceCommand()
            .addProcessModel(process, "process.bpmn")
            .send()
            .join()
            .getKey();
    new ZeebeResourcesHelper(client).waitUntilDeploymentIsDone(deploymentKey);

    final List<Long> processInstanceKeys = new ArrayList<>();
    Awaitility.await("every partition has at least one process instance with a pending job")
        .timeout(Duration.ofSeconds(60))
        // might throw an exception when a partition has not yet received deployment distribution
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var result =
                  client
                      .newCreateInstanceCommand()
                      .bpmnProcessId(PROCESS_ID)
                      .latestVersion()
                      .send()
                      .join();
              processInstanceKeys.add(result.getProcessInstanceKey());

              assertThat(
                      processInstanceKeys.stream()
                          .map(Protocol::decodePartitionId)
                          .collect(Collectors.toSet()))
                  .containsExactlyInAnyOrderElementsOf(
                      IntStream.rangeClosed(1, BACKUP_PARTITIONS_COUNT).boxed().toList());
            });
    return processInstanceKeys;
  }

  private static void scaleUpPartitions(final ClusterActuator clusterActuator) {
    final var scaleUp =
        clusterActuator.patchCluster(
            new ClusterConfigPatchRequest()
                .partitions(
                    new ClusterConfigPatchRequestPartitions()
                        .count(SCALED_PARTITIONS_COUNT)
                        .replicationFactor(BROKERS_COUNT)),
            false,
            false);
    Awaitility.await("cluster is scaled up to %d partitions".formatted(SCALED_PARTITIONS_COUNT))
        .timeout(Duration.ofMinutes(3))
        .untilAsserted(
            () -> {
              ClusterActuatorAssert.assertThat(clusterActuator)
                  .hasCompletedChanges(scaleUp)
                  .doesNotHavePendingChanges();
              assertThat(clusterActuator.getTopology().getRouting().getRequestHandling())
                  .isEqualTo(requestHandlingOver(SCALED_PARTITIONS_COUNT));
            });
  }

  private static void assertNewInstancesLandOnTheBackupsPartitions(final TestGateway<?> gateway) {
    try (final var client = gateway.newClientBuilder().build()) {
      Awaitility.await("new instances land on the backup's partitions")
          .timeout(Duration.ofSeconds(60))
          .ignoreExceptions()
          .untilAsserted(
              () -> {
                final var partitions =
                    IntStream.range(0, BACKUP_PARTITIONS_COUNT * 5)
                        .mapToLong(
                            ignored ->
                                client
                                    .newCreateInstanceCommand()
                                    .bpmnProcessId(PROCESS_ID)
                                    .latestVersion()
                                    .send()
                                    .join()
                                    .getProcessInstanceKey())
                        .mapToInt(Protocol::decodePartitionId)
                        .boxed()
                        .toList();
                assertThat(partitions)
                    .describedAs("partitions of the instances created through %s", gateway)
                    .containsOnly(
                        IntStream.rangeClosed(1, BACKUP_PARTITIONS_COUNT)
                            .boxed()
                            .toArray(Integer[]::new));
              });
    }
  }

  private static void awaitJobsOfInstancesActivatable(
      final CamundaClient client, final List<Long> processInstanceKeys) {
    Awaitility.await("the jobs of the restored instances can be activated")
        .timeout(Duration.ofSeconds(60))
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        client
                            .newActivateJobsCommand()
                            .jobType(JOB_TYPE)
                            .maxJobsToActivate(1000)
                            .send()
                            .join()
                            .getJobs())
                    .extracting(ActivatedJob::getProcessInstanceKey)
                    .containsAll(processInstanceKeys));
  }

  private static RequestHandlingAllPartitions requestHandlingOver(final int partitionCount) {
    return new RequestHandlingAllPartitions(partitionCount).strategy("AllPartitions");
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
