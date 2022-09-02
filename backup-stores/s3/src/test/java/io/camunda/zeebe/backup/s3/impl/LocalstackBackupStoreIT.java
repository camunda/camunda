/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.s3.impl;

import io.camunda.zeebe.backup.s3.S3BackupConfig;
import io.camunda.zeebe.backup.s3.S3BackupStore;
import io.camunda.zeebe.backup.s3.S3BackupStoreTestTemplate;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

@Testcontainers
final class LocalstackBackupStoreIT extends S3BackupStoreTestTemplate {

  @Container
  private static final LocalStackContainer S3 =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:0.14.5"))
          .withServices(Service.S3);

  private static S3AsyncClient client;
  private S3BackupStore store;
  private S3BackupConfig config;

  @BeforeAll
  static void setup() {
    client =
        S3AsyncClient.builder()
            .endpointOverride(S3.getEndpointOverride(Service.S3))
            .region(Region.of(S3.getRegion()))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(S3.getAccessKey(), S3.getSecretKey())))
            .build();
  }

  @BeforeEach
  void setupBucket() {
    config = new S3BackupConfig(RandomStringUtils.randomAlphabetic(10).toLowerCase());
    store = new S3BackupStore(config, client);
    client.createBucket(CreateBucketRequest.builder().bucket(config.bucketName()).build()).join();
  }

  @Override
  protected S3AsyncClient getClient() {
    return client;
  }

  @Override
  protected S3BackupConfig getConfig() {
    return config;
  }

  @Override
  protected S3BackupStore getStore() {
    return store;
  }
}
