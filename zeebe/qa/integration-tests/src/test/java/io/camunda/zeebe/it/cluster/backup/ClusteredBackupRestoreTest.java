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

import com.google.cloud.storage.BucketInfo;
import io.camunda.configuration.Backup;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.Gcs;
import io.camunda.management.backups.BackupInfo;
import io.camunda.management.backups.StateCode;
import io.camunda.management.backups.TakeBackupRuntimeResponse;
import io.camunda.zeebe.backup.gcs.GcsBackupConfig;
import io.camunda.zeebe.backup.gcs.GcsBackupStore;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.backup.BackupStoreCfg.BackupStoreType;
import io.camunda.zeebe.broker.system.configuration.backup.GcsBackupStoreConfig;
import io.camunda.zeebe.broker.system.configuration.backup.GcsBackupStoreConfig.GcsBackupStoreAuth;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.cluster.TestClusterBuilder;
import io.camunda.zeebe.qa.util.cluster.TestRestoreApp;
import io.camunda.zeebe.test.testcontainers.GcsContainer;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import java.time.Duration;
import java.util.stream.IntStream;
import org.apache.commons.lang3.RandomStringUtils;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class ClusteredBackupRestoreTest {
  private static final String BUCKET_NAME = RandomStringUtils.randomAlphabetic(10).toLowerCase();

  @Container private static final GcsContainer GCS = new GcsContainer();

  @BeforeAll
  static void setupBucket() throws Exception {
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

  @RegressionTest("https://github.com/camunda/camunda/issues/14496")
  void shouldRestoreWithFewerBrokers() {
    // given
    final var backupId = 22;

    // when -- take a backup with 3 brokers, each with one partition
    try (final var cluster =
        new TestClusterBuilder()
            .withBrokersCount(3)
            .withPartitionsCount(3)
            .withReplicationFactor(1)
            .withBrokerConfig(broker -> configureBackupStore(broker.brokerConfig()))
            .build()
            .start()
            .awaitCompleteTopology()) {
      final var actuator = BackupActuator.of(cluster.availableGateway());

      try (final var client = cluster.newClientBuilder().build()) {
        IntStream.range(0, 30)
            .forEach(
                (i) ->
                    client
                        .newPublishMessageCommand()
                        .messageName("name")
                        .correlationKey(Integer.toString(i))
                        .send()
                        .join());
      }

      assertThat(actuator.take(backupId)).isInstanceOf(TakeBackupRuntimeResponse.class);

      Awaitility.await("until a backup exists with the given ID")
          .atMost(Duration.ofSeconds(60))
          .ignoreExceptions() // 404 NOT_FOUND throws exception
          .untilAsserted(
              () -> {
                final var status = actuator.status(backupId);
                assertThat(status)
                    .extracting(BackupInfo::getBackupId, BackupInfo::getState)
                    .containsExactly((long) backupId, StateCode.COMPLETED);
              });
    }

    // then -- restoring with one broker is successful
    try (final var restoreApp =
        new TestRestoreApp()
            .withConfig(
                config -> {
                  configureBackupStore(config);
                  config.getCluster().setSize(1);
                  config.getCluster().setPartitionCount(3);
                  config.getCluster().setReplicationFactor(1);
                })
            .withBackupId(backupId)) {

      assertThatNoException().isThrownBy(() -> restoreApp.start());
    }
  }

  private static void configureBackupStore(final BrokerCfg brokerCfg) {
    final var backup = brokerCfg.getData().getBackup();

    final var storeConfig = new GcsBackupStoreConfig();
    storeConfig.setAuth(GcsBackupStoreAuth.NONE);
    storeConfig.setBucketName(BUCKET_NAME);
    storeConfig.setHost(GCS.externalEndpoint());

    backup.setStore(BackupStoreType.GCS);
    backup.setGcs(storeConfig);
  }

  private static void configureBackupStore(final Camunda config) {
    final var backup = config.getData().getBackup();

    final var storeConfig = new Gcs();
    storeConfig.setAuth(Gcs.GcsBackupStoreAuth.NONE);
    storeConfig.setBucketName(BUCKET_NAME);
    storeConfig.setHost(GCS.externalEndpoint());

    backup.setStore(Backup.BackupStoreType.GCS);
    backup.setGcs(storeConfig);
  }
}
