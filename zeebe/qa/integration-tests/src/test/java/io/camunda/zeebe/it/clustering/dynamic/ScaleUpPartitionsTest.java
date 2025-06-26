/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.clustering.dynamic;

import static io.camunda.zeebe.it.clustering.dynamic.Utils.DEFAULT_PROCESS_ID;
import static io.camunda.zeebe.it.clustering.dynamic.Utils.createInstanceWithAJobOnAllPartitions;
import static io.camunda.zeebe.it.clustering.dynamic.Utils.deployProcessModel;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.fail;

import io.camunda.application.commons.configuration.BrokerBasedConfiguration.BrokerBasedProperties;
import io.camunda.client.CamundaClient;
import io.camunda.management.backups.StateCode;
import io.camunda.zeebe.broker.system.configuration.backup.BackupStoreCfg.BackupStoreType;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequest;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequestPartitions;
import io.camunda.zeebe.management.cluster.PlannedOperationsResponse;
import io.camunda.zeebe.management.cluster.RequestHandlingAllPartitions;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestRestoreApp;
import io.camunda.zeebe.qa.util.cluster.TestSpringApplication;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.testcontainers.AzuriteContainer;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Objects;
import org.apache.commons.lang3.RandomStringUtils;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ZeebeIntegration
public class ScaleUpPartitionsTest {
  private static final Logger LOG = LoggerFactory.getLogger(ScaleUpPartitionsTest.class);

  private static final int PARTITIONS_COUNT = 3;
  private static final String JOB_TYPE = "job";
  private static final String PROCESS_ID = DEFAULT_PROCESS_ID;
  // must be static so that it's initialized before the cluster
  @TempDir private static Path backupPath;

  @Container private static final AzuriteContainer AZURITE_CONTAINER = new AzuriteContainer();
  @AutoClose CamundaClient camundaClient;
  private ClusterActuator clusterActuator;
  private BackupActuator backupActuator;

  private final String containerName =
      RandomStringUtils.insecure().nextAlphabetic(10).toLowerCase();

  @TestZeebe
  private final TestCluster cluster =
      TestCluster.builder()
          .useRecordingExporter(true)
          .withEmbeddedGateway(true)
          .withBrokersCount(3)
          .withPartitionsCount(PARTITIONS_COUNT)
          .withReplicationFactor(3)
          .withBrokerConfig(
              b ->
                  b.withBrokerConfig(this::configureBackupStore)
                      .withBrokerConfig(
                          bb -> {
                        bb.getExperimental().getFeatures().setEnablePartitionScaling(true);
                        bb.getCluster()
                            .getMembership()
                            .setSyncInterval(Duration.ofSeconds(1))
                            .setGossipInterval(Duration.ofSeconds(1));
                      }))
          .build();

  @BeforeEach
  void createClient() {
    camundaClient = cluster.availableGateway().newClientBuilder().build();
    clusterActuator = ClusterActuator.of(cluster.availableGateway());
    backupActuator = BackupActuator.of(cluster.availableGateway());
  }

  private void configureBackupStore(final BrokerBasedProperties bb) {
    final var backup = bb.getData().getBackup();
    backup.setStore(BackupStoreType.AZURE);
    final var azure = backup.getAzure();

    backup.setStore(BackupStoreType.AZURE);
    azure.setBasePath(containerName);
    azure.setConnectionString(AZURITE_CONTAINER.getConnectString());
  }

  @Test
  void shouldDeployProcessesToNewPartitionsAndStartNewInstances() {
    final var desiredPartitionCount = PARTITIONS_COUNT + 1;
    cluster.awaitHealthyTopology();
    // when
    scaleToPartitions(desiredPartitionCount);

    awaitScaleUpCompletion(desiredPartitionCount);
    createInstanceWithAJobOnAllPartitions(camundaClient, JOB_TYPE, desiredPartitionCount);
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 3})
  public void shouldStartProcessInstancesDeployedBeforeScaleUp(final int partitionsToAdd) {
    // given
    final var desiredPartitionCount = PARTITIONS_COUNT + partitionsToAdd;
    cluster.awaitHealthyTopology();

    // when
    deployProcessModel(camundaClient, JOB_TYPE, PROCESS_ID);

    scaleToPartitions(desiredPartitionCount);
    awaitScaleUpCompletion(desiredPartitionCount);

    for (int i = 0; i < 20; i++) {
      createInstanceWithAJobOnAllPartitions(
          camundaClient, JOB_TYPE, desiredPartitionCount, false, PROCESS_ID);
    }

    cluster.awaitHealthyTopology();
  }

  @Test
  public void shouldStartProcessInstancesDeployedWhenScalingUp() {
    // given
    final var desiredPartitionCount = PARTITIONS_COUNT + 3;
    cluster.awaitHealthyTopology();

    deployProcessModel(camundaClient, JOB_TYPE, "baseProcess");
    // when
    scaleToPartitions(desiredPartitionCount);

    final var processIds = new ArrayList<String>();
    for (int i = 0; i < 10; i++) {
      final var id = "processId-" + i;
      processIds.add(id);
      // do not wait for the deployment to be distributed to all partitions
      final var deploymentKey = deployProcessModel(camundaClient, JOB_TYPE, id, false);
      LOG.debug("Deployed process model with id: {}, key: {}", id, deploymentKey);
    }

    awaitScaleUpCompletion(desiredPartitionCount);

    // then
    for (final var processId : processIds) {
      createInstanceWithAJobOnAllPartitions(
          camundaClient, JOB_TYPE, desiredPartitionCount, false, processId);
    }

    cluster.awaitHealthyTopology();
  }

  @Test
  public void shouldScaleUpMultipleTimes() {
    // given
    final var firstScaleUp = PARTITIONS_COUNT + 1;
    final var secondScaleUp = firstScaleUp + 1;

    cluster.awaitHealthyTopology();

    // Scale up to first partition count
    scaleToPartitions(firstScaleUp);
    awaitScaleUpCompletion(firstScaleUp);

    createInstanceWithAJobOnAllPartitions(camundaClient, JOB_TYPE, firstScaleUp, true, PROCESS_ID);

    // when
    // Scale up to second partition count
    scaleToPartitions(secondScaleUp);
    awaitScaleUpCompletion(secondScaleUp);

    // then
    createInstanceWithAJobOnAllPartitions(
        camundaClient, JOB_TYPE, secondScaleUp, false, PROCESS_ID);
    cluster.awaitHealthyTopology();
  }

  @Test
  public void shouldDeleteBootstrapSnapshotWhenScalingIsDone() {
    cluster.awaitHealthyTopology();
    deployProcessModel(camundaClient, JOB_TYPE, PROCESS_ID);
    final var targetPartitionCount = PARTITIONS_COUNT + 1;

    final var partition1Leader = cluster.leaderForPartition(1);

    final var directory = Path.of(partition1Leader.brokerConfig().getData().getDirectory());
    final var bootstrapSnapshotDirectory =
        directory.resolve("raft-partition/partitions/1/bootstrap-snapshots/1-1-0-0-0");
    scaleToPartitions(targetPartitionCount);
    Awaitility.await("until snapshot is created")
        // to limit flakyness, the folder is checked every millisecond
        .pollInterval(Duration.ofMillis(1))
        .untilAsserted(
            () -> {
              assertThat(bootstrapSnapshotDirectory).exists();
            });
    awaitScaleUpCompletion(targetPartitionCount);

    Awaitility.await("until snapshot is created")
        // to limit flakyness, the folder is checked every millisecond
        .pollInterval(Duration.ofMillis(1))
        .untilAsserted(
            () -> {
              assertThat(bootstrapSnapshotDirectory).doesNotExist();
            });
  }

  @Test
  public void shouldBePossibleToRestoreAsAfterScalingUp() throws IOException {
    // given
    final var desiredPartitionCount = PARTITIONS_COUNT + 1;
    cluster.awaitHealthyTopology();
    final var backupId = 1L;

    // when
    scaleToPartitions(desiredPartitionCount);
    awaitScaleUpCompletion(desiredPartitionCount);

    final var routingStateAfterScaling = clusterActuator.getTopology().getRouting();

    backupActuator.take(backupId);

    Awaitility.await("until backup is ready")
        .timeout(Duration.ofMinutes(1))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(
            () -> {
              try {
                final var backupInfo = backupActuator.status(backupId);
                assertThat(backupInfo.getState()).isEqualTo(StateCode.COMPLETED);
              } catch (final Exception e) {
                LOG.error("Failed to get backup status for backupId: {}", backupId, e);
                fail(e);
              }
            });
    cluster.brokers().values().forEach(TestSpringApplication::stop);

    for (final var broker : cluster.brokers().values()) {
      LOG.debug("Restoring broker: {}", broker.nodeId());
      final var dataFolder = Path.of(broker.brokerConfig().getData().getDirectory());
      FileUtil.deleteFolderIfExists(dataFolder);

      Files.createDirectories(dataFolder);
      try (final var restoreApp =
          new TestRestoreApp(broker.brokerConfig()).withBackupId(backupId)) {
        assertThatNoException().isThrownBy(restoreApp::start);
      }
    }

    // then
    // the topology is restored to the desired partition count and message correlation
    cluster.brokers().values().parallelStream().forEach(TestSpringApplication::start);
    Awaitility.await("until topology is restored correctly")
        .untilAsserted(
            () -> {
              final var topology = clusterActuator.getTopology();
              assertThat(topology.getRouting().getRequestHandling())
                  .isEqualTo(routingStateAfterScaling.getRequestHandling());
              assertThat(topology.getRouting().getMessageCorrelation())
                  .isEqualTo(routingStateAfterScaling.getMessageCorrelation());
            });
  }

  private void awaitScaleUpCompletion(final int desiredPartitionCount) {
    Awaitility.await("until scaling is done")
        .timeout(Duration.ofMinutes(5))
        .untilAsserted(
            () -> {
              final var topology = clusterActuator.getTopology();
              if (Objects.requireNonNull(topology.getRouting().getRequestHandling())
                  instanceof final RequestHandlingAllPartitions allPartitions) {
                assertThat(allPartitions.getPartitionCount()).isEqualTo(desiredPartitionCount);
              } else {
                throw new AssertionError(
                    "Unexpected request handling mode: "
                        + topology.getRouting().getRequestHandling());
              }
            });
  }

  private PlannedOperationsResponse scaleToPartitions(final int desiredPartitionCount) {
    return clusterActuator.patchCluster(
        new ClusterConfigPatchRequest()
            .partitions(
                new ClusterConfigPatchRequestPartitions()
                    .count(desiredPartitionCount)
                    .replicationFactor(3)),
        false,
        false);
  }
}
