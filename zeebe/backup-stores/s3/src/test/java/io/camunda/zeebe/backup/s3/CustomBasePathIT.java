/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.s3;

import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.s3.S3BackupConfig.Builder;
import io.camunda.zeebe.backup.s3.util.S3TestBackupProvider;
import io.camunda.zeebe.backup.testkit.support.BackupAssert;
import io.camunda.zeebe.test.testcontainers.S3MockTestContainer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.stream.Stream;
import org.apache.commons.lang3.RandomStringUtils;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.InstanceOfAssertFactory;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

@Testcontainers
final class CustomBasePathIT {

  @Nested
  final class NoBasePathConfigured {
    @Container private static final S3MockTestContainer S3 = new S3MockTestContainer();

    @ParameterizedTest
    @MethodSource("provideBackups")
    void canBackupAndRestore(final Backup backup, @TempDir final Path target) {
      // given
      final var config =
          new Builder()
              .withBucketName(RandomStringUtils.randomAlphabetic(10).toLowerCase())
              .withEndpoint(S3.externalEndpoint())
              .withRegion(S3.region())
              .withCredentials(S3.accessKey(), S3.secretKey())
              .forcePathStyleAccess(true)
              .withBasePath(RandomStringUtils.randomAlphabetic(10))
              .build();

      try (final var client = S3BackupStore.buildClient(config)) {
        client
            .createBucket(CreateBucketRequest.builder().bucket(config.bucketName()).build())
            .join();
      }
      final var store = new S3BackupStore(config);

      // when
      Assertions.assertThat(store.save(backup)).succeedsWithin(Duration.ofSeconds(30));

      // then
      Assertions.assertThat(store.restore(backup.id(), target))
          .succeedsWithin(Duration.ofSeconds(30))
          .asInstanceOf(new InstanceOfAssertFactory<>(Backup.class, BackupAssert::assertThatBackup))
          .hasSameContentsAs(backup);
    }

    static Stream<? extends Arguments> provideBackups() throws Exception {
      return S3TestBackupProvider.provideArguments();
    }
  }

  @Nested
  final class BasePathConfigured {
    @Container private static final S3MockTestContainer S3 = new S3MockTestContainer();

    @ParameterizedTest
    @MethodSource("provideBackups")
    void canBackupAndRestore(final Backup backup, @TempDir final Path target) {
      // given
      final var config =
          new Builder()
              .withBucketName(RandomStringUtils.randomAlphabetic(10).toLowerCase())
              .withEndpoint(S3.externalEndpoint())
              .withRegion(S3.region())
              .withCredentials(S3.accessKey(), S3.secretKey())
              .forcePathStyleAccess(true)
              .build();

      try (final var client = S3BackupStore.buildClient(config)) {
        client
            .createBucket(CreateBucketRequest.builder().bucket(config.bucketName()).build())
            .join();
      }
      final var store = new S3BackupStore(config);

      // when
      Assertions.assertThat(store.save(backup)).succeedsWithin(Duration.ofSeconds(30));

      // then
      Assertions.assertThat(store.restore(backup.id(), target))
          .succeedsWithin(Duration.ofSeconds(30))
          .asInstanceOf(new InstanceOfAssertFactory<>(Backup.class, BackupAssert::assertThatBackup))
          .hasSameContentsAs(backup);
    }

    static Stream<? extends Arguments> provideBackups() throws Exception {
      return S3TestBackupProvider.provideArguments();
    }
  }
}
