/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.s3;

import io.camunda.zeebe.backup.s3.S3BackupConfig.Credentials;
import java.time.Duration;
import java.util.Optional;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

@Testcontainers
final class MinioBackupStoreIT implements S3BackupStoreTests {
  public static final String ACCESS_KEY = "letmein";
  public static final String SECRET_KEY = "letmein1234";
  public static final int DEFAULT_PORT = 9000;

  @SuppressWarnings("resource")
  @Container
  private static final GenericContainer<?> S3 =
      new GenericContainer<>(DockerImageName.parse("minio/minio"))
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

  private static S3AsyncClient client;
  private S3BackupStore store;
  private S3BackupConfig config;

  @BeforeEach
  void setupBucket() {
    config =
        new S3BackupConfig(
            RandomStringUtils.randomAlphabetic(10).toLowerCase(),
            Optional.of("http://%s:%d".formatted(S3.getHost(), S3.getMappedPort(DEFAULT_PORT))),
            Optional.of(Region.US_EAST_1.id()),
            Optional.of(new Credentials(ACCESS_KEY, SECRET_KEY)),
            Optional.empty(),
            true);
    client = S3BackupStore.buildClient(config);
    store = new S3BackupStore(config, client);
    client.createBucket(CreateBucketRequest.builder().bucket(config.bucketName()).build()).join();
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
}
