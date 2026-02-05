/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.backup;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.ListBlobsOptions;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.PrimaryStorageBackup;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.azure.AzureBackupConfig;
import io.camunda.zeebe.backup.azure.AzureBackupStore;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.testcontainers.AzuriteContainer;
import io.camunda.zeebe.test.util.testcontainers.ContainerLogsDumper;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ZeebeIntegration
public class AzureBackupRetentionAcceptanceIT implements BackupRetentionAcceptance {
  @Container private static final AzuriteContainer AZURITE_CONTAINER = new AzuriteContainer();
  private static final String CONTAINER_NAME = RandomStringUtils.randomAlphabetic(10).toLowerCase();

  @RegisterExtension
  @SuppressWarnings("unused")
  final ContainerLogsDumper logsWatcher =
      new ContainerLogsDumper(() -> Map.of("azurite", AZURITE_CONTAINER));

  private BackupStore backupStore;
  private BlobServiceClient blobServiceClient;

  @TestZeebe(autoStart = false)
  private final TestCluster cluster =
      TestCluster.builder()
          .withBrokersCount(BROKER_COUNT)
          .withGatewaysCount(1)
          .withReplicationFactor(REPLICATION_FACTOR)
          .withPartitionsCount(PARTITION_COUNT)
          .withEmbeddedGateway(false)
          .withGatewayConfig(
              g -> {
                final var membership = g.unifiedConfig().getCluster().getMembership();
                membership.setProbeInterval(Duration.ofMillis(100));
                membership.setFailureTimeout(Duration.ofSeconds(2));
              })
          .withNodeConfig(
              node ->
                  node.withProperty("zeebe.clock.controlled", true)
                      .withProperty("management.endpoints.web.exposure.include", "*"))
          .build();

  @BeforeEach
  void configureBrokersAndStartCluster() {
    final AzureBackupConfig config =
        new AzureBackupConfig.Builder()
            .withConnectionString(AZURITE_CONTAINER.getConnectString())
            .withContainerName(CONTAINER_NAME)
            .build();
    backupStore = AzureBackupStore.of(config);
  }

  @Override
  public TestCluster getTestCluster() {
    return cluster;
  }

  @Override
  public BackupStore getBackupStore() {
    return backupStore;
  }

  @Override
  public void assertManifestDoesNotExist(final long backupId) {
    final var containerClient = blobServiceClient.getBlobContainerClient(CONTAINER_NAME);
    IntStream.rangeClosed(1, PARTITION_COUNT)
        .forEach(
            partitionId ->
                IntStream.rangeClosed(0, BROKER_COUNT - 1)
                    .forEach(
                        brokerId -> {
                          final var client =
                              containerClient.getBlobClient(
                                  "manifests/%d/%d/%d/manifest.json"
                                      .formatted(partitionId, backupId, brokerId));
                          assertThat(client.getBlockBlobClient().exists()).isFalse();
                        }));
  }

  @Override
  public void assertContentsDoNotExist(final long backupId) {
    final var containerClient = blobServiceClient.getBlobContainerClient(CONTAINER_NAME);
    IntStream.rangeClosed(1, PARTITION_COUNT)
        .forEach(
            partitionId ->
                IntStream.rangeClosed(0, BROKER_COUNT - 1)
                    .forEach(
                        brokerId -> {
                          final var prefix =
                              "contents/%d/%d/%d".formatted(partitionId, backupId, brokerId);
                          final var blobs =
                              containerClient.listBlobs(
                                  new ListBlobsOptions().setPrefix(prefix), null);
                          assertThat(blobs.stream().count()).isZero();
                        }));
  }

  @Override
  public Consumer<Camunda> backupConfig() {
    return cfg -> {
      final var backup = cfg.getData().getPrimaryStorage().getBackup();
      final var azure = backup.getAzure();

      backup.setStore(PrimaryStorageBackup.BackupStoreType.AZURE);
      azure.setBasePath(CONTAINER_NAME);
      azure.setConnectionString(AZURITE_CONTAINER.getConnectString());
      final var config =
          new AzureBackupConfig.Builder()
              .withConnectionString(AZURITE_CONTAINER.getConnectString())
              .withContainerName(CONTAINER_NAME)
              .build();
      blobServiceClient = AzureBackupStore.buildClient(config);
    };
  }
}
