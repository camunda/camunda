/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.s3;

import java.net.URI;
import java.time.Duration;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
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
      new GenericContainer<>(DockerImageName.parse("minio/minio:edge"))
          .withCommand("server /data")
          .withExposedPorts(DEFAULT_PORT)
          .withEnv("MINIO_ACCESS_KEY", ACCESS_KEY)
          .withEnv("MINIO_SECRET_KEY", SECRET_KEY)
          .waitingFor(
              new HttpWaitStrategy()
                  .forPath("/minio/health/ready")
                  .forPort(DEFAULT_PORT)
                  .withStartupTimeout(Duration.ofMinutes(1)));

  private static S3AsyncClient client;
  private S3BackupStore store;
  private S3BackupConfig config;

  @BeforeAll
  static void setup() {
    client =
        S3AsyncClient.builder()
            .endpointOverride(
                URI.create("http://%s:%d".formatted(S3.getHost(), S3.getMappedPort(DEFAULT_PORT))))
            .region(Region.US_EAST_1) // the default region for MinIO
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
            .build();
  }

  @BeforeEach
  void setupBucket() {
    config = new S3BackupConfig(RandomStringUtils.randomAlphabetic(10).toLowerCase());
    store = new S3BackupStore(config, client);
    client.createBucket(CreateBucketRequest.builder().bucket(config.bucketName()).build()).join();
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
}
