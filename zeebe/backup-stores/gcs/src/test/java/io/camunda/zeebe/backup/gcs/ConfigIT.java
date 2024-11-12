/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.gcs;

import com.google.cloud.storage.BucketInfo;
import io.camunda.zeebe.backup.gcs.util.GcsContainer;
import org.apache.commons.lang3.RandomStringUtils;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class ConfigIT {
  @Container private static final GcsContainer GCS = new GcsContainer();

  @Test
  void shouldSuccessfullyValidateConfiguration() throws Exception {
    // given
    final var bucketName = RandomStringUtils.randomAlphabetic(12);
    final var config =
        new GcsBackupConfig.Builder()
            .withHost(GCS.externalEndpoint())
            .withBucketName(bucketName)
            .withoutAuthentication()
            .build();

    try (final var client = GcsBackupStore.buildClient(config)) {
      client.create(BucketInfo.of(bucketName));
    }

    // then
    Assertions.assertThatCode(() -> GcsBackupStore.validateConfig(config))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldNotFailValidationIfBucketDoesNotExist() {
    // given
    final var bucketName = RandomStringUtils.randomAlphabetic(12);
    final var config =
        new GcsBackupConfig.Builder()
            .withHost(GCS.externalEndpoint())
            .withBucketName(bucketName)
            .withoutAuthentication()
            .build();

    // then
    Assertions.assertThatCode(() -> GcsBackupStore.validateConfig(config))
        .doesNotThrowAnyException();
  }
}
