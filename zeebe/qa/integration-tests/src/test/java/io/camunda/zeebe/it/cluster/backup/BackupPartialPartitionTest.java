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

import io.camunda.client.CamundaClient;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.PrimaryStorageBackup.BackupStoreType;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.client.api.BackupRequestHandler;
import io.camunda.zeebe.backup.client.api.BackupStatusRequest;
import io.camunda.zeebe.backup.client.api.BrokerBackupRequest;
import io.camunda.zeebe.backup.s3.S3BackupConfig;
import io.camunda.zeebe.backup.s3.S3BackupConfig.Builder;
import io.camunda.zeebe.backup.s3.S3BackupStore;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.encoding.CheckpointStateResponse;
import io.camunda.zeebe.protocol.impl.encoding.CheckpointStateResponse.PartitionCheckpointState;
import io.camunda.zeebe.protocol.management.BackupStatusCode;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.testcontainers.MinioContainer;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.commons.lang3.RandomStringUtils;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ZeebeIntegration
class BackupPartialPartitionTest {
  @Container private static final MinioContainer S3 = new MinioContainer();
  private static final String CORRELATION_KEY = "key";
  private static final String MESSAGE_NAME = "message";
  private static final String CORRELATION_KEY_VALUE_FOR_PARTITION_2 = "item-1";
  private static final BpmnModelInstance PROCESS_WITH_MESSAGE_EVENT =
      Bpmn.createExecutableProcess("message-process")
          .startEvent()
          .intermediateCatchEvent("receive-message")
          .message(m -> m.name(MESSAGE_NAME).zeebeCorrelationKeyExpression(CORRELATION_KEY))
          .endEvent()
          .done();
  private BackupStore backupStore;
  private S3BackupConfig clientConfig;
  private String bucketName;
  @AutoClose private CamundaClient client;
  private ZeebeResourcesHelper resourcesHelper;
  private BackupRequestHandler backupRequestHandler;
  private int lastSeenDistributionRecordCount = -1;

  @TestZeebe
  private final TestCluster cluster =
      TestCluster.builder()
          .withBrokersCount(2)
          .withPartitionsCount(2)
          .withReplicationFactor(1)
          .withBrokerConfig(
              broker ->
                  broker.withUnifiedConfig(
                      config -> {
                        configureBackupStore(config);
                        config
                            .getProcessing()
                            .getEngine()
                            .getDistribution()
                            .setPauseCommandDistribution(true);
                      }))
          .build();

  @BeforeEach
  void setup() {
    client = cluster.newClientBuilder().build();
    resourcesHelper = new ZeebeResourcesHelper(client);
    backupRequestHandler = new BackupRequestHandler(cluster.anyGateway().bean(BrokerClient.class));
    createBackupStore();
    awaitCommandDistributionsDrained();
  }

  @AfterEach
  void close() {
    if (backupStore != null) {
      backupStore.closeAsync().join();
    }
    bucketName = null;
  }

  @Test
  @Timeout(value = 120)
  void canRetrieveCheckpointStateFromPartialPartitions() {
    final long processKey = deployProcess();
    createProcessInstanceOnPartitionOne(processKey);

    final long backupId = 3;
    takeBackupOnPartition(backupId, 2);
    waitUntilBackupCompletedOnPartition(backupId, 2);

    Awaitility.await("partial checkpoint state must be visible")
        .timeout(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var state = getCheckpointState();
              assertThat(state.getCheckpointStates()).hasSize(1);
              assertThat(state.getBackupStates()).hasSize(1);
              assertThat(state.getCheckpointStates())
                  .first()
                  .returns(2, PartitionCheckpointState::partitionId)
                  .returns(backupId, PartitionCheckpointState::checkpointId);
              assertThat(state.getBackupStates())
                  .first()
                  .returns(2, PartitionCheckpointState::partitionId)
                  .returns(backupId, PartitionCheckpointState::checkpointId);
            });
  }

  private void configureBackupStore(final Camunda config) {
    final var backupConfig = config.getData().getPrimaryStorage().getBackup();
    backupConfig.setStore(BackupStoreType.S3);
    final var s3Config = backupConfig.getS3();
    if (bucketName == null) {
      bucketName = RandomStringUtils.randomAlphabetic(10).toLowerCase();
    }
    s3Config.setBucketName(bucketName);
    s3Config.setEndpoint(S3.externalEndpoint());
    s3Config.setRegion(S3.region());
    s3Config.setAccessKey(S3.accessKey());
    s3Config.setSecretKey(S3.secretKey());
    s3Config.setForcePathStyleAccess(true);
  }

  private void createBackupStore() {
    clientConfig =
        new Builder()
            .withBucketName(bucketName)
            .withEndpoint(S3.externalEndpoint())
            .withRegion(S3.region())
            .withCredentials(S3.accessKey(), S3.secretKey())
            .withApiCallTimeout(Duration.ofSeconds(15))
            .forcePathStyleAccess(true)
            .build();
    backupStore = S3BackupStore.of(clientConfig);
    try (final var s3Client = S3BackupStore.buildClient(clientConfig)) {
      s3Client.createBucket(builder -> builder.bucket(bucketName).build()).join();
    }
  }

  private long deployProcess() {
    final var deployment =
        client
            .newDeployResourceCommand()
            .addProcessModel(PROCESS_WITH_MESSAGE_EVENT, "process.bpmn")
            .send()
            .join();
    resourcesHelper.waitUntilDeploymentIsDone(deployment.getKey());
    return deployment.getProcesses().getFirst().getProcessDefinitionKey();
  }

  private void createProcessInstanceOnPartitionOne(final long processKey) {
    Awaitility.await()
        .until(
            () -> {
              final var result =
                  client
                      .newCreateInstanceCommand()
                      .processDefinitionKey(processKey)
                      .variables(Map.of(CORRELATION_KEY, CORRELATION_KEY_VALUE_FOR_PARTITION_2))
                      .send()
                      .join();
              return 1 == Protocol.decodePartitionId(result.getProcessInstanceKey());
            });
    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
                .withPartitionId(2)
                .limit(1)
                .findFirst())
        .isPresent();
  }

  private void takeBackupOnPartition(final long backupId, final int partitionId) {
    final var request = new BrokerBackupRequest();
    request.setBackupId(backupId);
    request.setPartitionId(partitionId);
    request.setCheckpointType(CheckpointType.MANUAL_BACKUP);
    request.setPartitionGroup(DEFAULT_PHYSICAL_TENANT_ID);
    cluster.anyGateway().bean(BrokerClient.class).sendRequest(request).join();
  }

  private void waitUntilBackupCompletedOnPartition(final long backupId, final int partitionId) {
    final var request = new BackupStatusRequest();
    request.setPartitionId(partitionId);
    request.setBackupId(backupId);
    request.setPartitionGroup(DEFAULT_PHYSICAL_TENANT_ID);
    final var brokerClient = cluster.anyGateway().bean(BrokerClient.class);
    Awaitility.await()
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(brokerClient.sendRequest(request).join())
                    .matches(
                        response ->
                            response.getResponse().getStatus() == BackupStatusCode.COMPLETED));
  }

  private CheckpointStateResponse getCheckpointState()
      throws InterruptedException, ExecutionException, TimeoutException {
    return backupRequestHandler
        .getCheckpointState(DEFAULT_PHYSICAL_TENANT_ID)
        .toCompletableFuture()
        .get(30, TimeUnit.SECONDS);
  }

  private void awaitCommandDistributionsDrained() {
    Awaitility.await("initial command distributions must finish")
        .atMost(Duration.ofSeconds(60))
        .pollInterval(Duration.ofMillis(100))
        .during(Duration.ofSeconds(5))
        .until(
            () -> {
              final var records =
                  RecordingExporter.getRecords().stream()
                      .filter(record -> record.getValueType() == ValueType.COMMAND_DISTRIBUTION)
                      .toList();
              final var started = new HashSet<Long>();
              final var finished = new HashSet<Long>();
              records.forEach(
                  record -> {
                    if (record.getIntent() == CommandDistributionIntent.STARTED) {
                      started.add(record.getKey());
                    } else if (record.getIntent() == CommandDistributionIntent.FINISHED) {
                      finished.add(record.getKey());
                    }
                  });
              started.removeAll(finished);
              final var unchanged = records.size() == lastSeenDistributionRecordCount;
              lastSeenDistributionRecordCount = records.size();
              return unchanged && started.isEmpty();
            });
  }
}
