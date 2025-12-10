/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.backup;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.backup.s3.S3BackupConfig.Builder;
import io.camunda.zeebe.backup.s3.S3BackupStore;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.backup.BackupCfg.BackupStoreType;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.it.cluster.clustering.ClusteringRuleExtension;
import io.camunda.zeebe.shared.management.BackupEndpoint;
import io.camunda.zeebe.test.testcontainers.MinioContainer;
import java.time.Duration;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class BackupErrorResponseTest {

  @Nested
  final class DuplicateBackupIdTest {

    @Container private static final MinioContainer S3 = new MinioContainer();
    private BackupEndpoint backupEndpoint;

    private String bucketName;

    @RegisterExtension
    private final ClusteringRuleExtension clusteringRule =
        new ClusteringRuleExtension(1, 1, 1, this::configureBackupStore);

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

    void createBucket() {
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

    @BeforeEach
    void setup() {
      backupEndpoint =
          new BackupEndpoint(
              clusteringRule.getGateway().getBrokerClient(),
              clusteringRule.getBrokerCfg(0).getData().getBackup());
      createBucket();
    }

    @AfterEach
    void close() {
      // reset so that each test can use a different bucket name
      bucketName = null;
    }

    @ParameterizedTest
    @CsvSource({"1,1", "2,1"})
    @Timeout(value = 60)
    void shouldFailTakeBackupIfCheckpointAlreadyExists(
        final long existingCheckpoint, final long backupIdToTake) {
      // given
      backupEndpoint.take(existingCheckpoint);

      // when - then
      assertThat(backupEndpoint.take(backupIdToTake).getStatus()).isEqualTo(409);
    }
  }

  @Nested
  final class BackupNotConfiguredTest {
    @RegisterExtension
    private final ClusteringRuleExtension clusteringRule =
        new ClusteringRuleExtension(1, 1, 1, cfg -> {});

    private BackupEndpoint backupEndpoint;

    @BeforeEach
    void setup() {
      backupEndpoint =
          new BackupEndpoint(
              clusteringRule.getGateway().getBrokerClient(),
              clusteringRule.getBrokerCfg(0).getData().getBackup());
    }

    @Timeout(value = 60)
    @Test
    void shouldReturn400() {
      // when - then
      assertThat(backupEndpoint.query(new String[] {"1"}).getStatus()).isEqualTo(400);
    }
  }

  @Nested
  final class RequestTimeoutTest {
    @RegisterExtension
    private final ClusteringRuleExtension clusteringRule =
        new ClusteringRuleExtension(1, 1, 1, (cfg) -> {}, this::lowerTimeout);

    private BackupEndpoint backupEndpoint;

    private void lowerTimeout(final GatewayCfg config) {
      config.getCluster().setRequestTimeout(Duration.ofMillis(1));
    }

    @BeforeEach
    void setup() {
      backupEndpoint =
          new BackupEndpoint(
              clusteringRule.getGateway().getBrokerClient(),
              clusteringRule.getBrokerCfg(0).getData().getBackup());
    }

    @Timeout(value = 60)
    @Test
    void shouldReturn504() {
      // given
      clusteringRule.disconnect(clusteringRule.getBroker(0));

      // when - then
      assertThat(backupEndpoint.query(new String[] {"1"}).getStatus()).isEqualTo(504);
    }
  }

  @Nested
  final class ContinuousBackupsEnabled {
    @RegisterExtension
    private final ClusteringRuleExtension clusteringRule =
        new ClusteringRuleExtension(1, 1, 1, cfg -> cfg.getData().getBackup().setContinuous(true));

    private BackupEndpoint backupEndpoint;

    @BeforeEach
    void setup() {
      backupEndpoint =
          new BackupEndpoint(
              clusteringRule.getGateway().getBrokerClient(),
              clusteringRule.getBrokerCfg(0).getData().getBackup());
    }

    @Timeout(value = 60)
    @Test
    void shouldReturn400() {
      // when - then
      assertThat(backupEndpoint.take(1L).getStatus()).isEqualTo(400);
    }
  }
}
