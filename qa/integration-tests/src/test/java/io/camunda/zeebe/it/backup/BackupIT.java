/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.backup;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.backup.s3.S3BackupStore;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.backup.BackupStoreCfg.BackupStoreType;
import io.camunda.zeebe.gateway.admin.backup.BackupRequestHandler;
import io.camunda.zeebe.gateway.admin.backup.BackupStatus;
import io.camunda.zeebe.it.clustering.ClusteringRuleExtension;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.protocol.management.BackupStatusCode;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.commons.lang3.RandomStringUtils;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class BackupIT {
  @Container
  private static final LocalStackContainer S3 =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:0.14.5"))
          .withServices(Service.S3);

  private GrpcClientRule client;

  @RegisterExtension
  private final ClusteringRuleExtension clusteringRule =
      new ClusteringRuleExtension(1, 1, 1, this::configureBackupStore);

  private BackupRequestHandler backupRequestHandler;

  private void configureBackupStore(final BrokerCfg config) {
    config.getExperimental().getFeatures().setEnableBackup(true);

    final var backupConfig = config.getData().getBackup();
    backupConfig.setStore(BackupStoreType.S3);

    final var s3Config = backupConfig.getS3();
    final String bucketName = RandomStringUtils.randomAlphabetic(10).toLowerCase();
    s3Config.setBucketName(bucketName);
    s3Config.setEndpoint(S3.getEndpointOverride(Service.S3).toString());
    s3Config.setRegion(S3.getRegion());
    s3Config.setAccessKey(S3.getAccessKey());
    s3Config.setSecretKey(S3.getSecretKey());

    // Create bucket before for storing backups
    final var s3ClientConfig =
        io.camunda.zeebe.backup.s3.S3BackupConfig.from(
            bucketName,
            s3Config.getEndpoint(),
            S3.getRegion(),
            S3.getAccessKey(),
            S3.getSecretKey());
    try (final var s3Client = S3BackupStore.buildClient(s3ClientConfig)) {
      s3Client.createBucket(builder -> builder.bucket(bucketName).build()).join();
    }
  }

  @BeforeEach
  void setup() {
    client = new GrpcClientRule(clusteringRule.getClient());
    backupRequestHandler = new BackupRequestHandler(clusteringRule.getGateway().getBrokerClient());
  }

  @Test
  void shouldBackup() {
    // given
    client.createSingleJob("test");

    final long backupId = 2;

    // when
    backup(backupId);

    // then
    Awaitility.await("Backup must be completed.")
        .timeout(Duration.ofMinutes(2))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var status = getBackupStatus(backupId);
              assertThat(status.status()).isEqualTo(BackupStatusCode.COMPLETED);
              assertThat(status.backupId()).isEqualTo(backupId);
              assertThat(status.partitions()).hasSize(clusteringRule.getPartitionCount());
            });
  }

  private BackupStatus getBackupStatus(final long backupId)
      throws InterruptedException, ExecutionException, TimeoutException {
    // TODO: This should be replaced by the Gateway rest api when it is available
    return backupRequestHandler.getStatus(backupId).toCompletableFuture().get(30, TimeUnit.SECONDS);
  }

  private void backup(final long backupId) {
    // TODO: This should be replaced by the Gateway rest api when it is available
    assertThat(backupRequestHandler.takeBackup(backupId).toCompletableFuture())
        .succeedsWithin(Duration.ofSeconds(30));
  }
}
