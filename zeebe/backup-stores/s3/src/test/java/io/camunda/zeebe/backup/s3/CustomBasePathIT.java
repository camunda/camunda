/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.s3;

import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.s3.S3BackupConfig.Builder;
import io.camunda.zeebe.backup.testkit.support.BackupAssert;
import io.camunda.zeebe.backup.testkit.support.TestBackupProvider;
import java.nio.file.Path;
import java.time.Duration;
import org.apache.commons.lang3.RandomStringUtils;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.InstanceOfAssertFactory;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

@Testcontainers
final class CustomBasePathIT {
  private static final String ACCESS_KEY = "letmein";
  private static final String SECRET_KEY = "letmein1234";
  private static final int DEFAULT_PORT = 9000;

  @Nested
  final class NoBasePathConfigured {
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

    @ParameterizedTest
    @ArgumentsSource(TestBackupProvider.class)
    void canBackupAndRestore(final Backup backup, @TempDir final Path target) {
      // given
      final var config =
          new Builder()
              .withBucketName(RandomStringUtils.randomAlphabetic(10).toLowerCase())
              .withEndpoint("http://%s:%d".formatted(S3.getHost(), S3.getMappedPort(DEFAULT_PORT)))
              .withRegion(Region.US_EAST_1.id())
              .withCredentials(ACCESS_KEY, SECRET_KEY)
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
  }

  @Nested
  final class BasePathConfigured {
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

    @ParameterizedTest
    @ArgumentsSource(TestBackupProvider.class)
    void canBackupAndRestore(final Backup backup, @TempDir final Path target) {
      // given
      final var config =
          new Builder()
              .withBucketName(RandomStringUtils.randomAlphabetic(10).toLowerCase())
              .withEndpoint("http://%s:%d".formatted(S3.getHost(), S3.getMappedPort(DEFAULT_PORT)))
              .withRegion(Region.US_EAST_1.id())
              .withCredentials(ACCESS_KEY, SECRET_KEY)
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
  }
}
