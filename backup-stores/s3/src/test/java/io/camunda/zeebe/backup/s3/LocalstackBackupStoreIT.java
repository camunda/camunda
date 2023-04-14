/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.s3;

import io.camunda.zeebe.backup.s3.S3BackupConfig.Builder;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

@Testcontainers
final class LocalstackBackupStoreIT implements S3BackupStoreTests {
  private static final String BUCKET_NAME = RandomStringUtils.randomAlphabetic(10).toLowerCase();

  @Container
  private static final LocalStackContainer S3 =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack"))
          .withServices(Service.S3);

  private S3AsyncClient client;
  private S3BackupStore store;
  private S3BackupConfig config;

  @BeforeAll
  static void setupBucket() {
    final var config =
        new Builder()
            .withBucketName(BUCKET_NAME)
            .withEndpoint(S3.getEndpointOverride(Service.S3).toString())
            .withRegion(S3.getRegion())
            .withCredentials(S3.getAccessKey(), S3.getSecretKey())
            .build();
    try (final var client = S3BackupStore.buildClient(config)) {
      client.createBucket(CreateBucketRequest.builder().bucket(config.bucketName()).build()).join();
    }
  }

  @BeforeEach
  void setup() {
    config =
        new Builder()
            .withBucketName(BUCKET_NAME)
            .withBasePath(RandomStringUtils.randomAlphabetic(10).toLowerCase())
            .withEndpoint(S3.getEndpointOverride(Service.S3).toString())
            .withRegion(S3.getRegion())
            .withCredentials(S3.getAccessKey(), S3.getSecretKey())
            .withApiCallTimeout(null)
            .forcePathStyleAccess(false)
            .withCompressionAlgorithm(null)
            .build();
    client = S3BackupStore.buildClient(config);
    store = new S3BackupStore(config, client);
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
