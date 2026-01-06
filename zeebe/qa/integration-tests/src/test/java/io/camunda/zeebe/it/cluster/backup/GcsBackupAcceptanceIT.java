/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.backup;

import com.google.cloud.storage.BucketInfo;
import io.camunda.client.CamundaClient;
import io.camunda.configuration.Gcs;
import io.camunda.configuration.PrimaryStorageBackup;
import io.camunda.zeebe.backup.gcs.GcsBackupConfig;
import io.camunda.zeebe.backup.gcs.GcsBackupStore;
import io.camunda.zeebe.qa.util.cluster.TestApplication;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.testcontainers.GcsContainer;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
final class GcsBackupAcceptanceIT implements BackupAcceptance {

  private static final String BUCKET_NAME = RandomStringUtils.randomAlphabetic(10).toLowerCase();

  @Container private static final GcsContainer GCS = new GcsContainer();

  private final String basePath = RandomStringUtils.randomAlphabetic(10).toLowerCase();

  @TestZeebe
  private final TestCluster cluster =
      TestCluster.builder()
          .withBrokersCount(2)
          .withGatewaysCount(1)
          .withReplicationFactor(1)
          .withPartitionsCount(2)
          .withEmbeddedGateway(false)
          .withBrokerConfig(this::configureBroker)
          .withNodeConfig(this::configureNode)
          .build();

  @AutoClose private CamundaClient client;

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

  @BeforeEach
  void beforeEach() {
    client = cluster.newClientBuilder().build();
  }

  @Override
  public TestCluster getTestCluster() {
    return cluster;
  }

  private void configureBroker(final TestStandaloneBroker broker) {
    broker.withUnifiedConfig(
        cfg -> {
          cfg.getData()
              .getPrimaryStorage()
              .getBackup()
              .setStore(PrimaryStorageBackup.BackupStoreType.GCS);

          final var config = new Gcs();
          config.setAuth(Gcs.GcsBackupStoreAuth.NONE);
          config.setBasePath(basePath);
          config.setBucketName(BUCKET_NAME);
          config.setHost(GCS.externalEndpoint());
          cfg.getData().getPrimaryStorage().getBackup().setGcs(config);
        });
  }

  private void configureNode(final TestApplication<?> node) {
    node.withProperty("management.endpoints.web.exposure.include", "*");
  }
}
