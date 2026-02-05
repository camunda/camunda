/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.backup;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.PrimaryStorageBackup;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.s3.S3BackupConfig;
import io.camunda.zeebe.backup.s3.S3BackupConfig.Builder;
import io.camunda.zeebe.backup.s3.S3BackupStore;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.testcontainers.MinioContainer;
import io.camunda.zeebe.test.util.testcontainers.ContainerLogsDumper;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.apache.commons.lang3.RandomStringUtils;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

@Testcontainers
@ZeebeIntegration
public class S3BackupRetentionAcceptanceIT implements BackupRetentionAcceptance {

  private final String bucketName = RandomStringUtils.randomAlphabetic(10).toLowerCase();

  @Container
  private final MinioContainer minio = new MinioContainer().withDomain("minio.local", bucketName);

  @RegisterExtension
  @SuppressWarnings("unused")
  final ContainerLogsDumper logsWatcher = new ContainerLogsDumper(() -> Map.of("minio", minio));

  private BackupStore backupStore;
  private S3AsyncClient client;
  @TestZeebe(autoStart = false)
  private final TestCluster cluster =
      TestCluster.builder()
          .withBrokersCount(BROKER_COUNT)
          .withGatewaysCount(1)
          .withReplicationFactor(REPLICATION_FACTOR)
          .withPartitionsCount(PARTITION_COUNT)
          .withEmbeddedGateway(false)
          .withNodeConfig(
              node ->
                  node.withProperty("zeebe.clock.controlled", true)
                      .withProperty("management.endpoints.web.exposure.include", "*"))
          .build();

  @AfterEach
  public void tearDown() {
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
        new Builder()
            .withBucketName(bucketName)
            .withEndpoint(minio.externalEndpoint())
            .withRegion(minio.region())
            .withCredentials(minio.accessKey(), minio.secretKey())
            .withApiCallTimeout(Duration.ofSeconds(25))
            .forcePathStyleAccess(true)
            .build();

    try (final var client = S3BackupStore.buildClient(config)) {
      // it's possible to query to fast and get a 503 from the server here, so simply retry after
      Awaitility.await("until bucket is created")
          .untilAsserted(
              () -> client.createBucket(builder -> builder.bucket(bucketName).build()).join());
    }
  }

  @Override
  public void assertManifestDoesNotExist(final long backupId) {
    IntStream.rangeClosed(1, PARTITION_COUNT)
        .forEach(
            partitionId ->
                IntStream.rangeClosed(0, BROKER_COUNT - 1)
                    .forEach(
                        brokerId ->
                            assertThatThrownBy(
                                    () ->
                                        client
                                            .headObject(
                                                req ->
                                                    req.bucket(bucketName)
                                                        .key(
                                                            "manifests/%d/%d/%d/manifest.json"
                                                                .formatted(
                                                                    partitionId,
                                                                    backupId,
                                                                    brokerId)))
                                            .join())
                                .hasCauseInstanceOf(NoSuchKeyException.class)));
  }

  @Override
  public void assertContentsDoNotExist(final long backupId) {
    IntStream.rangeClosed(1, PARTITION_COUNT)
        .forEach(
            partitionId ->
                IntStream.rangeClosed(0, BROKER_COUNT - 1)
                    .forEach(
                        brokerId -> {
                          final var response =
                              client
                                  .listObjectsV2(
                                      req ->
                                          req.bucket(bucketName)
                                              .prefix(
                                                  "contents/%d/%d/%d"
                                                      .formatted(partitionId, backupId, brokerId)))
                                  .join();

                          assertThat(response.contents().isEmpty()).isTrue();
                        }));
  }

  @Override
  public Consumer<Camunda> backupConfig() {
    return cfg -> {
      final var backup = cfg.getData().getPrimaryStorage().getBackup();
      final var s3 = backup.getS3();

      backup.setStore(PrimaryStorageBackup.BackupStoreType.S3);
      s3.setBucketName(bucketName);
      s3.setEndpoint(minio.externalEndpoint());
      s3.setRegion(minio.region());
      s3.setAccessKey(minio.accessKey());
      s3.setSecretKey(minio.secretKey());
      s3.setForcePathStyleAccess(true);

      final var brokerCfg =
          new S3BackupConfig.Builder()
              .withBucketName(bucketName)
              .withEndpoint(minio.externalEndpoint())
              .withRegion(minio.region())
              .withCredentials(minio.accessKey(), minio.secretKey())
              .forcePathStyleAccess(true)
              .build();

      backupStore = S3BackupStore.of(brokerCfg);
      client = S3BackupStore.buildClient(brokerCfg);
    };
  }
}
