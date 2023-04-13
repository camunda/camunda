/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.s3;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierWildcardImpl;
import io.camunda.zeebe.backup.s3.S3BackupConfig.Builder;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.regions.Region;

final class ConnectionErrorTest {
  private static final String ACCESS_KEY = "letmein";
  private static final String SECRET_KEY = "letmein1234";
  private static final int DEFAULT_PORT = 9000;
  private S3BackupStore store;

  @BeforeEach
  void createBackupStore() {
    final var config =
        new Builder()
            .withBucketName(RandomStringUtils.randomAlphabetic(10).toLowerCase())
            .withEndpoint("http://%s:%d".formatted("localhost", DEFAULT_PORT))
            .withRegion(Region.US_EAST_1.id())
            .withCredentials(ACCESS_KEY, SECRET_KEY)
            .forcePathStyleAccess(true)
            .build();
    final var client = S3BackupStore.buildClient(config);
    store = new S3BackupStore(config, client);
  }

  @Test
  void shouldFailListWhenStoreIsNotReachable() {
    // given
    // s3 container is not running

    // when
    final var res =
        store.list(
            new BackupIdentifierWildcardImpl(Optional.empty(), Optional.of(1), Optional.empty()));

    // then
    assertThat(res)
        .failsWithin(Duration.ofSeconds(10))
        .withThrowableOfType(ExecutionException.class)
        .withMessageContaining("Connection refused");
  }

  @Test
  void shouldFailGetWhenStoreIsNotReachable() {
    // given
    // s3 container is not running

    // when
    final var res = store.getStatus(new BackupIdentifierImpl(0, 1, 1));

    // then
    assertThat(res)
        .failsWithin(Duration.ofSeconds(10))
        .withThrowableOfType(ExecutionException.class);
  }
}
