/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.s3;

import io.camunda.zeebe.backup.logarchive.store.LogArchiveStore;
import io.camunda.zeebe.backup.testkit.LogArchiveStoreContractTest;
import io.camunda.zeebe.test.testcontainers.S3MockTestContainer;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

@Testcontainers
final class S3LogArchiveStoreIT extends LogArchiveStoreContractTest {

  @Container private static final S3MockTestContainer S3 = new S3MockTestContainer();

  private static S3LogArchiveStore store;

  @BeforeAll
  static void setUp() {
    final var config =
        new S3BackupConfig.Builder()
            .withBucketName(RandomStringUtils.randomAlphabetic(10).toLowerCase())
            .withEndpoint(S3.externalEndpoint())
            .withRegion(S3.region())
            .withCredentials(S3.accessKey(), S3.secretKey())
            .forcePathStyleAccess(true)
            .withBasePath("custom-base")
            .build();
    try (final var client = S3BackupStore.buildClient(config)) {
      client.createBucket(CreateBucketRequest.builder().bucket(config.bucketName()).build()).join();
    }
    store = S3LogArchiveStore.of(config);
  }

  @AfterAll
  static void tearDown() {
    store.closeAsync().join();
  }

  @Override
  protected LogArchiveStore store() {
    return store;
  }
}
