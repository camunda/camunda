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

import com.google.cloud.storage.BucketInfo;
import feign.FeignException;
import io.camunda.zeebe.backup.gcs.GcsBackupConfig;
import io.camunda.zeebe.backup.gcs.GcsBackupStore;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.testcontainers.ContainerLogsDumper;
import io.camunda.zeebe.qa.util.testcontainers.GcsContainer;
import io.camunda.zeebe.qa.util.testcontainers.ZeebeTestContainerDefaults;
import io.camunda.zeebe.shared.management.openapi.models.BackupInfo;
import io.camunda.zeebe.shared.management.openapi.models.PartitionBackupInfo;
import io.camunda.zeebe.shared.management.openapi.models.StateCode;
import io.camunda.zeebe.shared.management.openapi.models.TakeBackupResponse;
import io.zeebe.containers.ZeebeBrokerNode;
import io.zeebe.containers.ZeebeNode;
import io.zeebe.containers.ZeebePort;
import io.zeebe.containers.cluster.ZeebeCluster;
import io.zeebe.containers.engine.ContainerEngine;
import java.time.Duration;
import org.agrona.CloseHelper;
import org.apache.commons.lang3.RandomStringUtils;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.groups.Tuple;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.Network;
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
final class GcsBackupAcceptanceIT {
  private static final Network NETWORK = Network.newNetwork();

  private static final String BUCKET_NAME = RandomStringUtils.randomAlphabetic(10).toLowerCase();

  @Container private static final GcsContainer GCS = new GcsContainer(NETWORK, "gcs.local");

  private final String basePath = RandomStringUtils.randomAlphabetic(10).toLowerCase();
  private final ZeebeCluster cluster =
      ZeebeCluster.builder()
          .withImage(ZeebeTestContainerDefaults.defaultTestImage())
          .withNetwork(NETWORK)
          .withBrokersCount(2)
          .withGatewaysCount(1)
          .withReplicationFactor(1)
          .withPartitionsCount(2)
          .withEmbeddedGateway(false)
          .withBrokerConfig(this::configureBroker)
          .withNodeConfig(this::configureNode)
          .build();

  @RegisterExtension
  @SuppressWarnings("unused")
  final ContainerLogsDumper logsWatcher = new ContainerLogsDumper(cluster::getNodes);

  @Container
  private final ContainerEngine engine = ContainerEngine.builder().withCluster(cluster).build();

  @AfterAll
  static void afterAll() {
    CloseHelper.quietCloseAll(NETWORK);
  }

  @BeforeAll
  static void beforeAll() throws Exception {
    final var config =
        new GcsBackupConfig.Builder()
            .withoutAuthentication()
            .withHost(GCS.externalEndpoint())
            .withBucketName(BUCKET_NAME)
            .build();

    try (final var client = GcsBackupStore.buildClient(config)) {
      client.create(BucketInfo.of(BUCKET_NAME));
    }
  }

  @Test
  void shouldTakeBackup() {
    // given
    final var actuator = BackupActuator.of(cluster.getAvailableGateway());
    try (final var client = engine.createClient()) {
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
    final var actuator = BackupActuator.of(cluster.getAvailableGateway());
    try (final var client = engine.createClient()) {
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
    final var actuator = BackupActuator.of(cluster.getAvailableGateway());
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

  private void configureBroker(final ZeebeBrokerNode<?> broker) {
    broker
        .withEnv("ZEEBE_BROKER_DATA_BACKUP_STORE", "GCS")
        .withEnv("ZEEBE_BROKER_DATA_BACKUP_GCS_BUCKETNAME", BUCKET_NAME)
        .withEnv("ZEEBE_BROKER_DATA_BACKUP_GCS_BASEPATH", basePath)
        .withEnv("ZEEBE_BROKER_DATA_BACKUP_GCS_AUTH", "none")
        .withEnv("ZEEBE_BROKER_DATA_BACKUP_GCS_HOST", GCS.internalEndpoint());
  }

  private void configureNode(final ZeebeNode<?> node) {
    node.withEnv("MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE", "*").dependsOn(GCS);
    node.addExposedPort(ZeebePort.MONITORING.getPort());
  }
}
