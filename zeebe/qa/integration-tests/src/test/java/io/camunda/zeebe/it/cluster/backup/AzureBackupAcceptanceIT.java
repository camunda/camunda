/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.backup;

import io.camunda.configuration.PrimaryStorageBackup;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.azure.AzureBackupConfig;
import io.camunda.zeebe.backup.azure.AzureBackupStore;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.testcontainers.AzuriteContainer;
import io.camunda.zeebe.test.util.testcontainers.ContainerLogsDumper;
import java.util.Map;
import org.agrona.CloseHelper;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
final class AzureBackupAcceptanceIT implements BackupAcceptance {
  @Container private static final AzuriteContainer AZURITE_CONTAINER = new AzuriteContainer();
  private static final String CONTAINER_NAME = RandomStringUtils.randomAlphabetic(10).toLowerCase();

  @RegisterExtension
  @SuppressWarnings("unused")
  final ContainerLogsDumper logsWatcher =
      new ContainerLogsDumper(() -> Map.of("azurite", AZURITE_CONTAINER));

  // cannot auto start, as we need azurite to be started before we can configure the brokers
  @TestZeebe(autoStart = false)
  private final TestCluster cluster =
      TestCluster.builder()
          .withBrokersCount(2)
          .withGatewaysCount(1)
          .withReplicationFactor(1)
          .withPartitionsCount(2)
          .withEmbeddedGateway(false)
          .build();

  private BackupStore store;

  @BeforeEach
  void beforeEach() {
    final AzureBackupConfig config =
        new AzureBackupConfig.Builder()
            .withConnectionString(AZURITE_CONTAINER.getConnectString())
            .withContainerName(CONTAINER_NAME)
            .build();
    store = AzureBackupStore.of(config);

    // we have to configure the cluster here, after azurite is started, as otherwise we won't have
    // access to the exposed port
    cluster.brokers().values().forEach(this::configureBroker);
    cluster.start().awaitCompleteTopology();
  }

  @AfterEach
  void afterEach() {
    CloseHelper.quietCloseAll(() -> store.closeAsync().join());
  }

  @Override
  public TestCluster getTestCluster() {
    return cluster;
  }

  private void configureBroker(final TestStandaloneBroker broker) {
    broker.withUnifiedConfig(
        cfg -> {
          final var backup = cfg.getData().getPrimaryStorage().getBackup();
          final var azure = backup.getAzure();

          backup.setStore(PrimaryStorageBackup.BackupStoreType.AZURE);
          azure.setBasePath(CONTAINER_NAME);
          azure.setConnectionString(AZURITE_CONTAINER.getConnectString());
        });
  }
}
