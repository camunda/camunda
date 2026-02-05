/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.backup;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Storage.BlobListOption;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.Gcs;
import io.camunda.configuration.PrimaryStorageBackup;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.gcs.GcsBackupConfig;
import io.camunda.zeebe.backup.gcs.GcsBackupStore;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.testcontainers.GcsContainer;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.exception.UncheckedException;
import org.junit.jupiter.api.AfterEach;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ZeebeIntegration
public class GcsBackupRetentionAcceptanceIT implements BackupRetentionAcceptance {

  private static final String BUCKET_NAME = RandomStringUtils.randomAlphabetic(10).toLowerCase();
  @Container private static final GcsContainer GCS = new GcsContainer();
  private final String basePath = RandomStringUtils.randomAlphabetic(10).toLowerCase();
  private BackupStore backupStore;
  private Storage client;

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

  @AfterEach
  public void tearDown() throws Exception {
    if (client != null) {
      client.close();
    }
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
  public void containerSetup() {
    final var config =
        new GcsBackupConfig.Builder()
            .withoutAuthentication()
            .withHost(GCS.externalEndpoint())
            .withBucketName(BUCKET_NAME)
            .build();

    try (final var client = GcsBackupStore.buildClient(config)) {
      client.create(BucketInfo.of(BUCKET_NAME));
    } catch (final Exception e) {
      throw new UncheckedException(e);
    }
  }

  @Override
  public void assertManifestDoesNotExist(final long backupId) {
    IntStream.rangeClosed(1, PARTITION_COUNT)
        .forEach(
            partitionId ->
                IntStream.rangeClosed(0, BROKER_COUNT - 1)
                    .forEach(
                        brokerId -> {
                          final var manifest =
                              client.get(
                                  BUCKET_NAME,
                                  "%s/manifests/%d/%d/%d/manifest.json"
                                      .formatted(basePath, partitionId, backupId, brokerId));
                          assertThat(manifest).isNull();
                        }));
  }

  @Override
  public void assertContentsDoNotExist(final long backupId) {
    IntStream.rangeClosed(1, PARTITION_COUNT)
        .forEach(
            partitionId ->
                IntStream.rangeClosed(0, BROKER_COUNT - 1)
                    .forEach(
                        brokerId -> {
                          final var prefix =
                              "%s/contents/%d/%d/%d"
                                  .formatted(basePath, partitionId, backupId, brokerId);
                          final var blobs = client.list(BUCKET_NAME, BlobListOption.prefix(prefix));
                          assertThat(blobs.streamAll().count()).isZero();
                        }));
  }

  @Override
  public Consumer<Camunda> backupConfig() {
    return cfg -> {
      cfg.getData()
          .getPrimaryStorage()
          .getBackup()
          .setStore(PrimaryStorageBackup.BackupStoreType.GCS);

      final var gcs = cfg.getData().getPrimaryStorage().getBackup().getGcs();
      gcs.setAuth(Gcs.GcsBackupStoreAuth.NONE);
      gcs.setBasePath(basePath);
      gcs.setBucketName(BUCKET_NAME);
      gcs.setHost(GCS.externalEndpoint());

      final var brokerCfg =
          new GcsBackupConfig.Builder()
              .withoutAuthentication()
              .withHost(GCS.externalEndpoint())
              .withBucketName(BUCKET_NAME)
              .withBasePath(basePath)
              .build();

      backupStore = GcsBackupStore.of(brokerCfg);

      client = GcsBackupStore.buildClient(brokerCfg);
    };
  }
}
