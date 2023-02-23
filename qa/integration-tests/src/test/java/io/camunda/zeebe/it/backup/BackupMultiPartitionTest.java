/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.backup;

import static java.util.function.Predicate.isEqual;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.backup.s3.S3BackupConfig;
import io.camunda.zeebe.backup.s3.S3BackupConfig.Builder;
import io.camunda.zeebe.backup.s3.S3BackupStore;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.backup.BackupStoreCfg.BackupStoreType;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.gateway.admin.backup.BackupRequestHandler;
import io.camunda.zeebe.gateway.admin.backup.BackupStatus;
import io.camunda.zeebe.gateway.admin.backup.BackupStatusRequest;
import io.camunda.zeebe.gateway.admin.backup.BrokerBackupRequest;
import io.camunda.zeebe.gateway.admin.backup.State;
import io.camunda.zeebe.gateway.impl.broker.BrokerClient;
import io.camunda.zeebe.it.clustering.ClusteringRuleExtension;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.it.util.RecordingJobHandler;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.management.BackupStatusCode;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.qa.util.testcontainers.MinioContainer;
import io.camunda.zeebe.restore.RestoreManager;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.apache.commons.lang3.RandomStringUtils;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class BackupMultiPartitionTest {
  @Container private static final MinioContainer S3 = new MinioContainer();
  private static final String JOB_TYPE = "test";
  private static final BpmnModelInstance SIMPLE_PROCESS =
      Bpmn.createExecutableProcess("process").startEvent("start").endEvent("end").done();
  private static final String CORRELATION_KEY = "key";
  private static final String MESSAGE_NAME = "message";
  private static final BpmnModelInstance PROCESS_WITH_MESSAGE_EVENT =
      Bpmn.createExecutableProcess("message-process")
          .startEvent()
          .intermediateCatchEvent("receive-message")
          .message(m -> m.name(MESSAGE_NAME).zeebeCorrelationKeyExpression(CORRELATION_KEY))
          .endEvent()
          .done();
  private static final String CORRELATION_KEY_VALUE_FOR_PARTITION_2 = "item-1";
  private S3BackupStore s3BackupStore;
  private S3BackupConfig s3ClientConfig;
  private String bucketName = null;
  private GrpcClientRule client;
  private BackupRequestHandler backupRequestHandler;

  @RegisterExtension
  private final ClusteringRuleExtension clusteringRule =
      new ClusteringRuleExtension(2, 1, 2, this::configureBackupStore);

  private void configureBackupStore(final BrokerCfg config) {

    final var backupConfig = config.getData().getBackup();
    backupConfig.setStore(BackupStoreType.S3);

    final var s3Config = backupConfig.getS3();

    generateBucketName();

    s3Config.setBucketName(bucketName);
    s3Config.setEndpoint(S3.externalEndpoint());
    s3Config.setRegion(S3.region());
    s3Config.setAccessKey(S3.accessKey());
    s3Config.setSecretKey(S3.secretKey());
    s3Config.setForcePathStyleAccess(true);
  }

  private void generateBucketName() {
    // Generate only once per test
    if (bucketName == null) {
      bucketName = RandomStringUtils.randomAlphabetic(10).toLowerCase();
    }
  }

  void createBackupStoreForTest() {
    // Create bucket before for storing backups
    s3ClientConfig =
        new Builder()
            .withBucketName(bucketName)
            .withEndpoint(S3.externalEndpoint())
            .withRegion(S3.region())
            .withCredentials(S3.accessKey(), S3.secretKey())
            .withApiCallTimeout(Duration.ofSeconds(15))
            .forcePathStyleAccess(true)
            .build();
    s3BackupStore = new S3BackupStore(s3ClientConfig);
    try (final var s3Client = S3BackupStore.buildClient(s3ClientConfig)) {
      s3Client.createBucket(builder -> builder.bucket(bucketName).build()).join();
    }
  }

  @BeforeEach
  void setup() {
    client = new GrpcClientRule(clusteringRule.getClient());
    backupRequestHandler = new BackupRequestHandler(clusteringRule.getGateway().getBrokerClient());
    createBackupStoreForTest();
  }

  @AfterEach
  void close() {
    // Create bucket before for storing backups
    s3BackupStore.closeAsync();
    // reset so that each test can use a different bucket name
    bucketName = null;
  }

  @Test
  @Timeout(value = 120)
  void shouldTriggerBackupViaInterPartitionMessageDeploymentDistribute() {
    // given
    final long backupId = 1;
    // trigger backup only on partition 1
    takeBackupOnPartition(backupId, 1);

    // when
    client.deployProcess(SIMPLE_PROCESS);

    // then
    waitUntilBackupIsCompleted(backupId);
  }

  @Test
  @Timeout(value = 120)
  void shouldTriggerBackupViaInterPartitionMessageSubscriptionCommands() {
    // given
    final long processKey = client.deployProcess(PROCESS_WITH_MESSAGE_EVENT);

    final long backupId = 2;
    // trigger backup only on partition 1
    takeBackupOnPartition(backupId, 1);

    // when
    createProcessInstanceOnPartitionOne(processKey);

    // then
    waitUntilBackupIsCompleted(backupId);
  }

  @Test
  @Timeout(value = 120)
  void shouldTriggerBackupViaInterPartitionMessageCorrelationCommands() {
    // given
    final long processKey = client.deployProcess(PROCESS_WITH_MESSAGE_EVENT);
    createProcessInstanceOnPartitionOne(processKey);

    final long backupId = 3;
    // trigger backup only on partition 2
    takeBackupOnPartition(backupId, 2);

    // when
    publishMessageAndWaitUntilCorrelated();

    // then
    waitUntilBackupIsCompleted(backupId);
  }

  @Test
  @Timeout(value = 180)
  void shouldRestoreOnAllPartitions() {
    // given
    final var jobsCreated = createJobsOnAllPartitions();

    final var backupId = 4;
    backup(backupId);
    waitUntilBackupIsCompleted(backupId);

    final var brokerIds =
        clusteringRule.getBrokers().stream()
            .map(broker -> broker.getConfig().getCluster().getNodeId())
            .toList();

    // when
    brokerIds.forEach(this::stopBrokerAndDeleteData);
    brokerIds.forEach(broker -> restoreBroker(backupId, broker));
    brokerIds.forEach(b -> clusteringRule.getBroker(b).start());

    clusteringRule.waitForTopology(
        topology -> topology.hasLeaderForEachPartition(clusteringRule.getPartitionCount()));

    // then
    final var jobHandler = new RecordingJobHandler();
    try (final var ignored =
        client.getClient().newWorker().jobType(JOB_TYPE).handler(jobHandler).open()) {
      Awaitility.await("All jobs created before restoring the cluster are activated")
          .timeout(Duration.ofSeconds(30))
          .untilAsserted(
              () ->
                  assertThat(
                          jobHandler.getHandledJobs().stream()
                              .map(ActivatedJob::getKey)
                              .collect(Collectors.toSet()))
                      .containsExactlyInAnyOrderElementsOf(jobsCreated));
    }
  }

  @Test
  @Timeout(value = 180)
  void shouldDeleteBackup() {
    // given
    final var backupId = 4;
    backup(backupId);
    waitUntilBackupIsCompleted(backupId);

    // when
    delete(backupId);

    // then
    Awaitility.await("Backup must be deleted.")
        .timeout(Duration.ofSeconds(30))
        .ignoreExceptions()
        .until(() -> getBackupStatus(backupId).status(), isEqual(State.DOES_NOT_EXIST));
  }

  private Set<Long> createJobsOnAllPartitions() {
    final Set<Integer> partitions = new HashSet<>();
    final Set<Long> jobKeys = new HashSet<>();
    while (partitions.size() < clusteringRule.getPartitionCount()) {
      final long jobKey = client.createSingleJob(JOB_TYPE);
      jobKeys.add(jobKey);
      partitions.addAll(
          RecordingExporter.jobRecords(JobIntent.CREATED)
              .withType(JOB_TYPE)
              .filter(r -> r.getKey() == jobKey)
              .limit(1)
              .map(Record::getPartitionId)
              .collect(Collectors.toSet()));
    }
    return jobKeys;
  }

  private void stopBrokerAndDeleteData(final int brokerId) {
    final var broker = clusteringRule.getBroker(brokerId);
    final var dataDirectory = broker.getConfig().getData().getDirectory();

    clusteringRule.stopBroker(brokerId);
    try {
      FileUtil.deleteFolderIfExists(Path.of(dataDirectory));
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void backup(final long backupId) {
    assertThat(backupRequestHandler.takeBackup(backupId).toCompletableFuture())
        .succeedsWithin(Duration.ofSeconds(30));
  }

  private void delete(final long backupId) {
    assertThat(backupRequestHandler.deleteBackup(backupId).toCompletableFuture())
        .succeedsWithin(Duration.ofSeconds(30));
  }

  private void restoreBroker(final long backupId, final int brokerId) {
    try {
      new RestoreManager(clusteringRule.getBrokerCfg(brokerId), s3BackupStore)
          .restore(backupId)
          .get(120, TimeUnit.SECONDS);
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void waitUntilBackupIsCompleted(final long backupId) {
    Awaitility.await("Backup must be completed.")
        .timeout(Duration.ofMinutes(1))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var status = getBackupStatus(backupId);
              assertThat(status.status()).isEqualTo(State.COMPLETED);
              assertThat(status.backupId()).isEqualTo(backupId);
              assertThat(status.partitions()).hasSize(clusteringRule.getPartitionCount());
            });
  }

  private BackupStatus getBackupStatus(final long backupId)
      throws InterruptedException, ExecutionException, TimeoutException {
    return backupRequestHandler.getStatus(backupId).toCompletableFuture().get(30, TimeUnit.SECONDS);
  }

  private void takeBackupOnPartition(final long backupId, final int partitionId) {
    final BrokerBackupRequest backupRequest = new BrokerBackupRequest();
    backupRequest.setBackupId(backupId);
    backupRequest.setPartitionId(partitionId);
    final BrokerClient brokerClient = clusteringRule.getGateway().getBrokerClient();
    brokerClient.sendRequest(backupRequest).orTimeout(30, TimeUnit.SECONDS).join();

    waitUntilBackupCompletedOnPartition(backupId, partitionId);
  }

  private void waitUntilBackupCompletedOnPartition(final long backupId, final int partitionId) {
    final BackupStatusRequest backupStatusRequest = new BackupStatusRequest();
    backupStatusRequest.setPartitionId(partitionId);
    backupStatusRequest.setBackupId(backupId);
    final BrokerClient brokerClient = clusteringRule.getGateway().getBrokerClient();
    Awaitility.await()
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(brokerClient.sendRequest(backupStatusRequest).join())
                    .matches(
                        response ->
                            response.getResponse().getStatus() == BackupStatusCode.COMPLETED));
  }

  private void publishMessageAndWaitUntilCorrelated() {
    client
        .getClient()
        .newPublishMessageCommand()
        .messageName(MESSAGE_NAME)
        .correlationKey(CORRELATION_KEY_VALUE_FOR_PARTITION_2)
        .send()
        .join();

    assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.CORRELATED)
                .withPartitionId(1)
                .limit(1)
                .findFirst())
        .isPresent();
  }

  private void createProcessInstanceOnPartitionOne(final long processKey) {
    Awaitility.await()
        .until(
            () -> {
              client
                  .getClient()
                  .newCreateInstanceCommand()
                  .processDefinitionKey(processKey)
                  .variables(Map.of(CORRELATION_KEY, CORRELATION_KEY_VALUE_FOR_PARTITION_2))
                  .send()
                  .join();
              // Ensure process instance is created on partition 1
              return RecordingExporter.processInstanceCreationRecords()
                  .withPartitionId(1)
                  .findFirst()
                  .isPresent();
            });
    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
                .withPartitionId(2)
                .limit(1)
                .findFirst())
        .isPresent();
  }
}
