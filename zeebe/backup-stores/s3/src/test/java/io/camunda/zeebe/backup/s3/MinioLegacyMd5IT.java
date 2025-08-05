/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.s3;

import io.camunda.zeebe.backup.s3.S3BackupConfig.Builder;
import java.nio.file.NoSuchFileException;
import java.time.Duration;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

@Testcontainers
public class MinioLegacyMd5IT implements S3BackupStoreTests {
  public static final String ACCESS_KEY = "letmein";
  public static final String SECRET_KEY = "letmein1234";
  public static final int DEFAULT_PORT = 9000;
  // Deliberately using an old MinIO image to verify AWS legacy MD5 support
  private static final String LEGACY_MINIO_IMAGE = "minio/minio:RELEASE.2023-11-20T22-40-07Z";
  private static final Logger LOG = LoggerFactory.getLogger(MinioLegacyMd5IT.class);
  private static final String BUCKET_NAME = RandomStringUtils.randomAlphabetic(10).toLowerCase();

  @SuppressWarnings("resource")
  @Container
  private static final GenericContainer<?> S3 =
      new GenericContainer<>(DockerImageName.parse(LEGACY_MINIO_IMAGE))
          .withCommand("server /data")
          .withExposedPorts(DEFAULT_PORT)
          .withEnv("MINIO_ACCESS_KEY", ACCESS_KEY)
          .withEnv("MINIO_SECRET_KEY", SECRET_KEY)
          .withEnv("MINIO_DOMAIN", "localhost")
          .waitingFor(
              new HttpWaitStrategy()
                  .forPath("/minio/health/ready")
                  .forPort(DEFAULT_PORT)
                  .withStartupTimeout(Duration.ofMinutes(1)));

  private S3AsyncClient client;
  private S3BackupStore store;
  private S3BackupConfig config;

  @BeforeAll
  static void setupBucket() {
    final var config =
        new Builder()
            .withBucketName(BUCKET_NAME)
            .withEndpoint("http://%s:%d".formatted(S3.getHost(), S3.getMappedPort(DEFAULT_PORT)))
            .withRegion(Region.US_EAST_1.id())
            .withCredentials(ACCESS_KEY, SECRET_KEY)
            .forcePathStyleAccess(true)
            .build();
    try (final var client = S3BackupStore.buildClient(config)) {
      client.createBucket(CreateBucketRequest.builder().bucket(config.bucketName()).build()).join();
    }
  }

  @BeforeEach
  void setup(final TestInfo testInfo) {
    final String basePath = RandomStringUtils.randomAlphabetic(10).toLowerCase();
    config =
        new Builder()
            .withBucketName(BUCKET_NAME)
            .withBasePath(basePath)
            .withEndpoint("http://%s:%d".formatted(S3.getHost(), S3.getMappedPort(DEFAULT_PORT)))
            .withRegion(Region.US_EAST_1.id())
            .withCredentials(ACCESS_KEY, SECRET_KEY)
            .forcePathStyleAccess(true)
            .withSupportLegacyMd5(true)
            .build();
    client = S3BackupStore.buildClient(config);
    store = new S3BackupStore(config, client);

    LOG.info("{} is running with base path {}", testInfo.getDisplayName(), basePath);
  }

  @AfterEach
  void tearDown() {
    store.closeAsync();
  }

  @Override
  public S3AsyncClient getClient() {
    return client;
  }

  @Override
  public S3BackupConfig getConfig() {
    return config;
  }

  @Override
  public S3BackupStore getStore() {
    return store;
  }

  @Override
  public Class<? extends Exception> getFileNotFoundExceptionClass() {
    return NoSuchFileException.class;
  }
}
