/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import feign.FeignException;
import io.camunda.zeebe.backup.s3.S3BackupConfig.Builder;
import io.camunda.zeebe.backup.s3.S3BackupStore;
import io.camunda.zeebe.broker.system.configuration.backup.BackupStoreCfg.BackupStoreType;
import io.camunda.zeebe.management.backups.BackupInfo;
import io.camunda.zeebe.management.backups.PartitionBackupInfo;
import io.camunda.zeebe.management.backups.StateCode;
import io.camunda.zeebe.management.backups.TakeBackupResponse;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.cluster.TestApplication;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.qa.util.testcontainers.MinioContainer;
import io.camunda.zeebe.test.util.testcontainers.ContainerLogsDumper;
import java.time.Duration;
import java.util.Map;
import org.agrona.CloseHelper;
import org.apache.commons.lang3.RandomStringUtils;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.groups.Tuple;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Acceptance tests for the backup management API. Tests here should interact with the backups
 * primarily via the management API, and occasionally assert results on the configured backup store.
 *
 * <p>The tests run against a cluster of 2 brokers and 1 gateway, no embedded gateways, two
 * partitions and replication factor of 1. This allows us to test that requests are correctly fanned
 * out across the gateway, since each broker is guaranteed to be leader of a partition.
 *
 * <p>NOTE: this does not test the consistency of backups, nor that partition leaders correctly
 * maintain consistency via checkpoint records. Other test suites should be set up for this.
 */
@Testcontainers
@ZeebeIntegration
final class S3BackupAcceptanceIT {
  private final String bucketName = RandomStringUtils.randomAlphabetic(10).toLowerCase();

  @Container
  private final MinioContainer minio = new MinioContainer().withDomain("minio.local", bucketName);

  @RegisterExtension
  @SuppressWarnings("unused")
  final ContainerLogsDumper logsWatcher = new ContainerLogsDumper(() -> Map.of("minio", minio));

  // cannot auto start, as we need minio to be started before we can configure the brokers
  @TestZeebe(autoStart = false)
  private final TestCluster cluster =
      TestCluster.builder()
          .withBrokersCount(2)
          .withGatewaysCount(1)
          .withReplicationFactor(1)
          .withPartitionsCount(2)
          .withEmbeddedGateway(false)
          .withNodeConfig(this::configureNode)
          .build();

  private S3BackupStore store;

  @BeforeEach
  void beforeEach() {
    final var config =
        new Builder()
            .withBucketName(bucketName)
            .withEndpoint(minio.externalEndpoint())
            .withRegion(minio.region())
            .withCredentials(minio.accessKey(), minio.secretKey())
            .withApiCallTimeout(Duration.ofSeconds(25))
            .forcePathStyleAccess(true)
            .build();
    store = new S3BackupStore(config);

    try (final var client = S3BackupStore.buildClient(config)) {
      client.createBucket(builder -> builder.bucket(config.bucketName()).build()).join();
    }

    // we have to configure the cluster here, after minio is started, as otherwise we won't have
    // access to the exposed port
    cluster.brokers().values().forEach(this::configureBroker);
    cluster.start().awaitCompleteTopology();
  }

  @AfterEach
  void afterEach() {
    CloseHelper.quietCloseAll(() -> store.closeAsync().join());
  }

  @Test
  void shouldTakeBackup() {
    // given
    final var actuator = BackupActuator.ofAddress(cluster.availableGateway().monitoringAddress());
    try (final var client = cluster.newClientBuilder().build()) {
      client.newPublishMessageCommand().messageName("name").correlationKey("key").send().join();
    }

    // when
    final var response = actuator.take(1);

    // then
    assertThat(response).isInstanceOf(TakeBackupResponse.class);
    waitUntilBackupIsCompleted(actuator, 1L);
  }

  private static void waitUntilBackupIsCompleted(
      final BackupActuator actuator, final long backupId) {
    Awaitility.await("until a backup exists with the id %d".formatted(backupId))
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions() // 404 NOT_FOUND throws exception
        .untilAsserted(
            () -> {
              final var status = actuator.status(backupId);
              assertThat(status)
                  .extracting(BackupInfo::getBackupId, BackupInfo::getState)
                  .containsExactly(backupId, StateCode.COMPLETED);
              assertThat(status.getDetails())
                  .flatExtracting(PartitionBackupInfo::getPartitionId)
                  .containsExactlyInAnyOrder(1, 2);
            });
  }

  @Test
  void shouldListBackups() {
    // given
    final var actuator = BackupActuator.ofAddress(cluster.availableGateway().monitoringAddress());
    try (final var client = cluster.newClientBuilder().build()) {
      client.newPublishMessageCommand().messageName("name").correlationKey("key").send().join();
    }

    // when
    actuator.take(1);
    actuator.take(2);

    waitUntilBackupIsCompleted(actuator, 1L);
    waitUntilBackupIsCompleted(actuator, 2L);

    // then
    final var status = actuator.list();
    assertThat(status)
        .hasSize(2)
        .extracting(BackupInfo::getBackupId, BackupInfo::getState)
        .containsExactly(
            Tuple.tuple(1L, StateCode.COMPLETED), Tuple.tuple(2L, StateCode.COMPLETED));
  }

  @Test
  void shouldDeleteBackup() {
    // given
    final var actuator = BackupActuator.ofAddress(cluster.availableGateway().monitoringAddress());
    final long backupId = 1;
    actuator.take(backupId);
    waitUntilBackupIsCompleted(actuator, backupId);

    // when
    actuator.delete(backupId);

    // then
    Awaitility.await("Backup is deleted")
        .timeout(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThatThrownBy(() -> actuator.status(backupId))
                    .asInstanceOf(InstanceOfAssertFactories.type(FeignException.class))
                    .extracting(FeignException::status)
                    .isEqualTo(404));
  }

  private void configureBroker(final TestStandaloneBroker broker) {
    broker.withBrokerConfig(
        cfg -> {
          final var backup = cfg.getData().getBackup();
          final var s3 = backup.getS3();

          backup.setStore(BackupStoreType.S3);
          s3.setBucketName(bucketName);
          s3.setEndpoint(minio.externalEndpoint());
          s3.setRegion(minio.region());
          s3.setAccessKey(minio.accessKey());
          s3.setSecretKey(minio.secretKey());
          s3.setForcePathStyleAccess(true);
        });
  }

  private void configureNode(final TestApplication<?> node) {
    node.withProperty("management.endpoints.web.exposure.include", "*");
  }
}
