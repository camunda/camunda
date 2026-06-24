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
import io.camunda.zeebe.backup.s3.S3BackupConfig.Builder;
import io.camunda.zeebe.backup.s3.S3BackupStore;
import io.camunda.zeebe.qa.util.cluster.TestApplication;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.testcontainers.MinioContainer;
import io.camunda.zeebe.test.util.testcontainers.ContainerLogsDumper;
import java.time.Duration;
import java.util.Map;
import org.agrona.CloseHelper;
import org.apache.commons.lang3.RandomStringUtils;
import org.awaitility.Awaitility;
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
final class S3BackupAcceptanceIT implements BackupAcceptance {
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

  private BackupStore store;

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
    store = S3BackupStore.of(config);

    try (final var client = S3BackupStore.buildClient(config)) {
      // it's possible to query to fast and get a 503 from the server here, so simply retry after
      Awaitility.await("unil bucket is created")
          .untilAsserted(
              () ->
                  client
                      .createBucket(builder -> builder.bucket(config.bucketName()).build())
                      .join());
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

  @Override
  public TestCluster getTestCluster() {
    return cluster;
  }

  private void configureBroker(final TestStandaloneBroker broker) {
    broker.withUnifiedConfig(
        cfg -> {
          final var backup = cfg.getData().getPrimaryStorage().getBackup();
          final var s3 = backup.getS3();

          backup.setStore(PrimaryStorageBackup.BackupStoreType.S3);
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
