/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.clustering.dynamic;

import static io.camunda.zeebe.it.cluster.clustering.dynamic.Utils.DEFAULT_PROCESS_ID;
import static io.camunda.zeebe.it.cluster.clustering.dynamic.Utils.createInstanceOnAllPartitions;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.broker.system.configuration.backup.BackupStoreCfg.BackupStoreType;
import io.camunda.zeebe.broker.system.configuration.backup.FilesystemBackupStoreConfig;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequest;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequestPartitions;
import io.camunda.zeebe.management.cluster.RequestHandlingAllPartitions;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Reproduces <a href="https://github.com/camunda/camunda/issues/59881">#59881</a> with a real
 * cluster scale-up: a partition added while a definition is {@code DRAINING} inherits it via the
 * bootstrap snapshot with none of its instances, so the drain must be finalized on bootstrap rather
 * than left {@code DRAINING} forever.
 */
@Testcontainers
@ZeebeIntegration
@Timeout(value = 10, unit = TimeUnit.MINUTES)
class ReconcileDrainingOnScaleUpTest {

  private static final int INITIAL_PARTITIONS = 2;
  private static final int NEW_PARTITION_ID = INITIAL_PARTITIONS + 1;
  private static final String JOB_TYPE = "job";
  private static final String PROCESS_ID = DEFAULT_PROCESS_ID;

  @AutoClose private CamundaClient camundaClient;
  private ClusterActuator clusterActuator;

  @TestZeebe(awaitCompleteTopology = false)
  private final TestCluster cluster;

  ReconcileDrainingOnScaleUpTest(@TempDir final Path backupPath) {
    cluster =
        TestCluster.builder()
            .useRecordingExporter(true)
            .withEmbeddedGateway(true)
            .withBrokersCount(3)
            .withPartitionsCount(INITIAL_PARTITIONS)
            .withReplicationFactor(3)
            .withBrokerConfig(
                b ->
                    b.withBrokerConfig(
                        cfg -> {
                          // Scale-up bootstraps the new partition from a backup store; without it
                          // the bootstrap snapshot cannot be shared and scaling never completes.
                          final var backup = cfg.getData().getBackup();
                          backup.setStore(BackupStoreType.FILESYSTEM);
                          final var fs = new FilesystemBackupStoreConfig();
                          fs.setBasePath(backupPath.toString());
                          backup.setFilesystem(fs);

                          cfg.getCluster()
                              .getMembership()
                              .setSyncInterval(Duration.ofSeconds(1))
                              .setGossipInterval(Duration.ofMillis(500));

                          final var distribution =
                              cfg.getExperimental().getEngine().getDistribution();
                          distribution.setMaxBackoffDuration(Duration.ofSeconds(1));
                          distribution.setRedistributionInterval(Duration.ofMillis(200));
                        }))
            .build();
  }

  @BeforeEach
  void createClient() {
    camundaClient = cluster.availableGateway().newClientBuilder().build();
    clusterActuator = ClusterActuator.of(cluster.availableGateway());
  }

  @Test
  void shouldFinalizeDrainingDefinitionOnNewlyBootstrappedPartition() {
    // given - a healthy cluster with a process that parks its instances on a pending job
    cluster.awaitHealthyTopology();

    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
            .endEvent()
            .done();
    final var deployResponse =
        camundaClient
            .newDeployResourceCommand()
            .addProcessModel(process, "process.bpmn")
            .send()
            .join();
    final long processDefinitionKey =
        deployResponse.getProcesses().getFirst().getProcessDefinitionKey();

    // Instances on every existing partition keep a pending job, so the definition stays DRAINING
    // (not immediately deleted) on each partition once the deletion is distributed. Crucially, the
    // bootstrap source (partition 1) must have a local active instance, otherwise it deletes the
    // definition immediately and the snapshot the new partition inherits carries no DRAINING state.
    createInstanceOnAllPartitions(camundaClient, INITIAL_PARTITIONS, PROCESS_ID, Map::of);

    // when - the definition is deleted while instances are still active and new partition is added
    camundaClient.newDeleteResourceCommand(processDefinitionKey).send().join();

    // the deletion has to land as DRAINING on the bootstrap source partition before we scale
    Awaitility.await("definition is DRAINING on the bootstrap source partition")
        .atMost(Duration.ofMinutes(1))
        .untilAsserted(
            () ->
                assertThat(
                        RecordingExporter.processRecords()
                            .withIntent(ProcessIntent.DRAINING)
                            .withProcessDefinitionKey(processDefinitionKey)
                            .withPartitionId(1)
                            .findFirst())
                    .describedAs("definition is DRAINING on the bootstrap source partition")
                    .isPresent());

    scaleToPartitions(NEW_PARTITION_ID);
    awaitScaleUpCompletion(NEW_PARTITION_ID);

    // then - the newly bootstrapped partition deletes the inherited draining definition locally
    assertThat(
            RecordingExporter.processRecords()
                .withProcessDefinitionKey(processDefinitionKey)
                .withPartitionId(NEW_PARTITION_ID)
                .withIntent(ProcessIntent.DELETED)
                .exists())
        .describedAs("draining definition is deleted on the newly bootstrapped partition")
        .isTrue();
  }

  private void scaleToPartitions(
      @SuppressWarnings("SameParameterValue") final int desiredPartitionCount) {
    clusterActuator.patchCluster(
        new ClusterConfigPatchRequest()
            .partitions(
                new ClusterConfigPatchRequestPartitions()
                    .count(desiredPartitionCount)
                    .replicationFactor(3)),
        false,
        false);
  }

  private void awaitScaleUpCompletion(
      @SuppressWarnings("SameParameterValue") final int desiredPartitionCount) {
    Awaitility.await("until scaling is done")
        .atMost(Duration.ofMinutes(2))
        .catchUncaughtExceptions()
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(
            () -> {
              final var topology = clusterActuator.getTopology();
              assertThat(topology.getRouting()).isNotNull();
              final var requestHandling = topology.getRouting().getRequestHandling();
              assertThat(requestHandling).isInstanceOf(RequestHandlingAllPartitions.class);
              final var allPartitions = (RequestHandlingAllPartitions) requestHandling;
              assertThat(allPartitions.getPartitionCount()).isEqualTo(desiredPartitionCount);
            });
  }
}
