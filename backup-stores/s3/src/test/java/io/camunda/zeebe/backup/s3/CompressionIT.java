/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.s3;

import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupImpl;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import io.camunda.zeebe.backup.s3.S3BackupConfig.Builder;
import io.camunda.zeebe.backup.testkit.support.BackupAssert;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.InstanceOfAssertFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

@Testcontainers
final class CompressionIT {
  private static final String ACCESS_KEY = "letmein";
  private static final String SECRET_KEY = "letmein1234";
  private static final int DEFAULT_PORT = 9000;

  private static final byte[] COMPRESSIBLE_BYTES = compressibleBytes();

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

  private S3BackupStore store;

  @BeforeEach
  void setupBucket() {
    final var config =
        new Builder()
            .withBucketName(RandomStringUtils.randomAlphabetic(10).toLowerCase())
            .withEndpoint("http://%s:%d".formatted(S3.getHost(), S3.getMappedPort(DEFAULT_PORT)))
            .withRegion(Region.US_EAST_1.id())
            .withCredentials(ACCESS_KEY, SECRET_KEY)
            .forcePathStyleAccess(true)
            .withCompressionAlgorithm("zstd")
            .build();
    final var client = S3BackupStore.buildClient(config);
    store = new S3BackupStore(config, client);
    client.createBucket(CreateBucketRequest.builder().bucket(config.bucketName()).build()).join();
  }

  @Test
  void canBackupWithCompression() throws IOException {
    // given
    final var backup = compressibleBackup();

    // when
    final var result = store.save(backup);

    // then
    Assertions.assertThat(result).succeedsWithin(Duration.ofSeconds(30));
  }

  @Test
  void canRestoreBackupWithCompression(@TempDir final Path target) throws IOException {
    // given
    final var backup = compressibleBackup();

    // when
    Assertions.assertThat(store.save(backup)).succeedsWithin(Duration.ofSeconds(30));

    // then
    Assertions.assertThat(store.restore(backup.id(), target))
        .succeedsWithin(Duration.ofSeconds(30))
        .asInstanceOf(new InstanceOfAssertFactory<>(Backup.class, BackupAssert::assertThatBackup))
        .hasSameContentsAs(backup);
  }

  private Backup compressibleBackup() throws IOException {
    final var tempDir = Files.createTempDirectory("backup");
    Files.createDirectory(tempDir.resolve("segments/"));
    final var seg1 = Files.createFile(tempDir.resolve("segments/segment-file-1"));
    final var seg2 = Files.createFile(tempDir.resolve("segments/segment-file-2"));
    Files.write(seg1, COMPRESSIBLE_BYTES);
    Files.write(seg2, RandomUtils.nextBytes(1024));

    Files.createDirectory(tempDir.resolve("snapshot/"));
    final var s1 = Files.createFile(tempDir.resolve("snapshot/snapshot-file-1"));
    final var s2 = Files.createFile(tempDir.resolve("snapshot/snapshot-file-2"));
    Files.write(s1, COMPRESSIBLE_BYTES);
    Files.write(s2, COMPRESSIBLE_BYTES);

    return new BackupImpl(
        new BackupIdentifierImpl(1, 2, 3),
        new BackupDescriptorImpl(Optional.of("test-snapshot-id"), 4, 5, "test"),
        new NamedFileSetImpl(Map.of("segment-file-1", seg1, "segment-file-2", seg2)),
        new NamedFileSetImpl(Map.of("snapshot-file-1", s1, "snapshot-file-2", s2)));
  }

  /**
   * Generates a byte array that should be compressible by most compression algorithms. The byte
   * array consists of 1000 parts, each randomly chosen from 10 templates that contain between 8 and
   * 64 KiB of random data.
   */
  private static byte[] compressibleBytes() {
    final Supplier<Integer> partSize = () -> RandomUtils.nextInt(8, 65) * 1024; // 8-64 KiB
    final var totalPartCount = 1000; // Count of parts chosen for the final result
    final var templateCount = 10; // Number of templates that are generated

    // Generate templates, each containing random data
    final var templates =
        IntStream.range(0, templateCount)
            .mapToObj(i -> ArrayUtils.toObject(RandomUtils.nextBytes(partSize.get())))
            .toArray(Byte[][]::new);

    // Build the result by randomly picking from the templates
    return ArrayUtils.toPrimitive(
        IntStream.range(0, totalPartCount)
            .mapToObj(i -> templates[(RandomUtils.nextInt(0, templateCount))])
            .flatMap(Arrays::stream)
            .toArray(Byte[]::new));
  }
}
