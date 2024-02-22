/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.backup;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.backup.s3.S3BackupConfig.Builder;
import io.camunda.zeebe.backup.s3.S3BackupStore;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.backup.BackupStoreCfg.BackupStoreType;
import io.camunda.zeebe.gateway.admin.backup.BackupRequestHandler;
import io.camunda.zeebe.gateway.admin.backup.BackupStatus;
import io.camunda.zeebe.gateway.admin.backup.State;
import io.camunda.zeebe.it.clustering.ClusteringRuleExtension;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.qa.util.testcontainers.MinioContainer;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
class BackupReplicatedPartitionTest {
  @Container private static final MinioContainer S3 = new MinioContainer();
  private static final String JOB_TYPE = "test";
  private String bucketName = null;
  private GrpcClientRule client;
  private BackupRequestHandler backupRequestHandler;

  @RegisterExtension
  private final ClusteringRuleExtension clusteringRule =
      new ClusteringRuleExtension(1, 3, 3, this::configureBackupStore);

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

  @BeforeEach
  void setup() {
    client = new GrpcClientRule(clusteringRule.getClient());
    backupRequestHandler = new BackupRequestHandler(clusteringRule.getGateway().getBrokerClient());
    // Create bucket before for storing backups
    final var s3ClientConfig =
        new Builder()
            .withBucketName(bucketName)
            .withEndpoint(S3.externalEndpoint())
            .withRegion(S3.region())
            .withCredentials(S3.accessKey(), S3.secretKey())
            .withApiCallTimeout(Duration.ofSeconds(15))
            .forcePathStyleAccess(true)
            .build();
    try (final var s3Client = S3BackupStore.buildClient(s3ClientConfig)) {
      s3Client.createBucket(builder -> builder.bucket(bucketName).build()).join();
    }
  }

  @AfterEach
  void close() {
    // reset so that each test can use a different bucket name
    bucketName = null;
  }

  @Test
  @Timeout(value = 120)
  void shouldQueryStatusOfBackupAfterLeaderChange()
      throws ExecutionException, InterruptedException, TimeoutException {
    // given
    client.createSingleJob(JOB_TYPE);
    final long backupId = 1;
    backup(backupId);
    waitUntilBackupIsCompleted(backupId);

    // when
    final var anyFollower =
        clusteringRule
            .getOtherBrokerObjects(clusteringRule.getLeaderForPartition(1).getNodeId())
            .stream()
            .findAny()
            .map(b -> b.getConfig().getCluster().getNodeId())
            .orElseThrow();
    clusteringRule.forceNewLeaderForPartition(anyFollower, 1);

    // then
    assertThat(getBackupStatus(backupId).status()).isEqualTo(State.COMPLETED);
  }

  @Test
  @Timeout(value = 120)
  void shouldTakeNewBackupAfterLeaderChange() {
    // given
    client.createSingleJob(JOB_TYPE);
    final long backupId = 1;
    backup(backupId);
    waitUntilBackupIsCompleted(backupId);

    // when
    final var anyFollower =
        clusteringRule
            .getOtherBrokerObjects(clusteringRule.getLeaderForPartition(1).getNodeId())
            .stream()
            .findAny()
            .map(b -> b.getConfig().getCluster().getNodeId())
            .orElseThrow();
    clusteringRule.forceNewLeaderForPartition(anyFollower, 1);
    client.createSingleJob(JOB_TYPE);

    // then
    final var newBackupId = 2;
    backup(newBackupId);
    waitUntilBackupIsCompleted(newBackupId);
  }

  private void backup(final long backupId) {
    assertThat(backupRequestHandler.takeBackup(backupId).toCompletableFuture())
        .succeedsWithin(Duration.ofSeconds(30));
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
}
