/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.s3;

import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupImpl;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import io.camunda.zeebe.backup.s3.S3BackupConfig.Builder;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.test.util.testcontainers.ContainerLogsDumper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

@Testcontainers
final class BackupUploadIT {
  private static final String BUCKET_NAME = RandomStringUtils.randomAlphabetic(10).toLowerCase();

  @Container
  private static final LocalStackContainer S3 =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack"))
          .withServices(Service.S3)
          .withEnv("LS_LOG", "trace");

  @RegisterExtension
  final ContainerLogsDumper logsWatcher = new ContainerLogsDumper(() -> Map.of("localstack", S3));

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

  public S3BackupStore buildBackupStore(
      final int parallelUploadsLimit, final Duration connectionAcquisitionTimeout) {
    final S3BackupConfig backupConfig =
        new Builder()
            .withBucketName(BUCKET_NAME)
            .withBasePath(RandomStringUtils.randomAlphabetic(10).toLowerCase())
            .withEndpoint(S3.getEndpointOverride(Service.S3).toString())
            .withRegion(S3.getRegion())
            .withCredentials(S3.getAccessKey(), S3.getSecretKey())
            .withApiCallTimeout(null)
            .forcePathStyleAccess(false)
            .withCompressionAlgorithm(null)
            .withConnectionAcquisitionTimeout(connectionAcquisitionTimeout)
            .withMaxConcurrentConnections(parallelUploadsLimit)
            .build();

    final S3AsyncClient asyncClient = S3BackupStore.buildClient(backupConfig);
    return new S3BackupStore(backupConfig, asyncClient);
  }

  @Test
  void shouldSaveBackupWithManyFiles() throws IOException {
    // given
    // Default values for the configuration

    final CompletableFuture<Void> saveFuture =
        buildBackupStore(50, Duration.ofSeconds(10)).save(backupWithManyFiles(4_000));

    // then
    Assertions.assertThat(saveFuture).succeedsWithin(Duration.ofSeconds(60));
  }

  @Test
  void shouldNotTimeoutForAcquisitionOfConnection() throws IOException {
    // given
    // Even with just one connection, and low timeout limit, the second upload should not start
    // until a connection is available, and therefore should not throw AcquisitionConnectionTimeout
    final CompletableFuture<Void> saveFuture =
        buildBackupStore(1, Duration.ofMillis(50)).save(backupWithLargeFiles(50_000_000));

    // then
    Assertions.assertThat(saveFuture).succeedsWithin(Duration.ofSeconds(60));
  }

  Backup backupWithManyFiles(final int numberOfSegments) throws IOException {
    final var tempDir = Files.createTempDirectory("backup");
    Files.createDirectory(tempDir.resolve("segments/"));
    Files.createDirectory(tempDir.resolve("snapshot/"));
    final Map<String, Path> largeNumberOfSegments = new HashMap<>();
    final var s1 = Files.createFile(tempDir.resolve("snapshot/snapshot-file-1"));
    final var s2 = Files.createFile(tempDir.resolve("snapshot/snapshot-file-2"));

    for (int i = 0; i < numberOfSegments; i++) {
      final var seg =
          Files.createFile(tempDir.resolve(("segments/segment" + "-file-%d").formatted(i)));
      largeNumberOfSegments.put("segment-file-%d".formatted(i), seg);
      // We need to actually write some bytes onto the file, see issue:
      // https://github.com/camunda/camunda/issues/18177
      Files.write(seg, RandomUtils.nextBytes(16));
    }

    return new BackupImpl(
        new BackupIdentifierImpl(1, 2, 3),
        new BackupDescriptorImpl(
            Optional.of("test-snapshot-id"),
            4,
            5,
            "test",
            Instant.now(),
            CheckpointType.MANUAL_BACKUP),
        new NamedFileSetImpl(largeNumberOfSegments),
        new NamedFileSetImpl(Map.of("snapshot-file-1", s1, "snapshot-file-2", s2)));
  }

  Backup backupWithLargeFiles(final int sizeOfFileInBytes) throws IOException {
    final var tempDir = Files.createTempDirectory("backup");
    Files.createDirectory(tempDir.resolve("segments/"));
    final var seg1 = Files.createFile(tempDir.resolve("segments/segment-file-1"));
    final var seg2 = Files.createFile(tempDir.resolve("segments/segment-file-2"));
    Files.write(seg1, RandomUtils.nextBytes(sizeOfFileInBytes));
    Files.write(seg2, RandomUtils.nextBytes(sizeOfFileInBytes));

    Files.createDirectory(tempDir.resolve("snapshot/"));
    final var s1 = Files.createFile(tempDir.resolve("snapshot/snapshot-file-1"));
    final var s2 = Files.createFile(tempDir.resolve("snapshot/snapshot-file-2"));
    Files.write(s1, RandomUtils.nextBytes(sizeOfFileInBytes));
    Files.write(s2, RandomUtils.nextBytes(sizeOfFileInBytes));

    return new BackupImpl(
        new BackupIdentifierImpl(1, 2, 3),
        new BackupDescriptorImpl(
            Optional.of("test-snapshot-id"),
            4,
            5,
            "test",
            Instant.now(),
            CheckpointType.MANUAL_BACKUP),
        new NamedFileSetImpl(Map.of("snapshot-file-1", s1, "snapshot-file-2", s2)),
        new NamedFileSetImpl(Map.of("segment-file-1", seg1, "segment-file-2", seg2)));
  }
}
