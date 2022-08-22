/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupDescriptor;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.NamedFileSet;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

@Testcontainers
final class S3BackupStoreIT {

  @Container
  private static final LocalStackContainer S3 =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:0.14.5"))
          .withServices(Service.S3);

  private static S3AsyncClient client;
  private S3BackupConfig config;
  private S3BackupStore store;

  @BeforeEach
  void setupBucket() {
    config = new S3BackupConfig(RandomStringUtils.randomAlphabetic(10).toLowerCase());
    store = new S3BackupStore(config, client);
    client.createBucket(CreateBucketRequest.builder().bucket(config.bucketName()).build()).join();
  }

  @BeforeAll
  static void startClient() {
    client =
        S3AsyncClient.builder()
            .endpointOverride(S3.getEndpointOverride(Service.S3))
            .region(Region.of(S3.getRegion()))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(S3.getAccessKey(), S3.getSecretKey())))
            .build();
  }

  @AfterAll
  static void closeClient() {
    client.close();
  }

  @Test
  void savingBackupIsSuccessful(@TempDir Path tempDir) throws IOException {
    // given
    final var backup = prepareTestBackup(tempDir);

    // when
    final var result = store.save(backup);

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(10));
  }

  @Test
  void savesMetadata(@TempDir Path tempDir) throws IOException {
    // given
    final var backup = prepareTestBackup(tempDir);

    // when
    store.save(backup).join();

    // then
    final var metadataObject =
        client
            .getObject(
                GetObjectRequest.builder()
                    .bucket(config.bucketName())
                    .key(S3BackupStore.objectPrefix(backup.id()) + Metadata.OBJECT_KEY)
                    .build(),
                AsyncResponseTransformer.toBytes())
            .join();

    final var objectMapper = new ObjectMapper();
    final var readMetadata = objectMapper.readValue(metadataObject.asByteArray(), Metadata.class);

    assertThat(readMetadata.checkpointId()).isEqualTo(backup.id.checkpointId);
    assertThat(readMetadata.partitionId()).isEqualTo(backup.id.partitionId);
    assertThat(readMetadata.nodeId()).isEqualTo(backup.id.nodeId);

    assertThat(readMetadata.checkpointPosition()).isEqualTo(backup.descriptor.checkpointPosition);
    assertThat(readMetadata.snapshotId()).isEqualTo(backup.descriptor.snapshotId);
    assertThat(readMetadata.numberOfPartitions()).isEqualTo(backup.descriptor.numberOfPartitions);

    assertThat(readMetadata.snapshotFileNames()).isEqualTo(backup.snapshot.names());
    assertThat(readMetadata.segmentFileNames()).isEqualTo(backup.segments.names());
  }

  @Test
  void snapshotFilesExist(@TempDir Path tempDir) throws IOException {
    // given
    final var backup = prepareTestBackup(tempDir);
    final var prefix = S3BackupStore.objectPrefix(backup.id) + S3BackupStore.SNAPSHOT_PREFIX;

    final var expectedObjects =
        backup.snapshot.names().stream().map(name -> prefix + name).toList();

    // when
    store.save(backup).join();

    // then
    final var listed =
        client.listObjectsV2(req -> req.bucket(config.bucketName()).prefix(prefix)).join();

    assertThat(listed.contents().stream().map(S3Object::key))
        .isNotEmpty()
        .allSatisfy(k -> assertThat(k).startsWith(prefix).isIn(expectedObjects));
  }

  @Test
  void bucketContainsExpectedObjectsOnly(@TempDir Path tempDir) throws IOException {
    // given
    final var backup = prepareTestBackup(tempDir);
    final var prefix = S3BackupStore.objectPrefix(backup.id);

    final var snapshotFiles =
        backup.snapshot.names().stream().map(name -> prefix + S3BackupStore.SNAPSHOT_PREFIX + name);
    final var metadata = prefix + Metadata.OBJECT_KEY;
    final var expectedObjects = Stream.concat(snapshotFiles, Stream.of(metadata)).toList();

    // when
    store.save(backup).join();

    // then
    final var listed =
        client.listObjectsV2(req -> req.bucket(config.bucketName())).join().contents().stream()
            .map(S3Object::key)
            .toList();

    assertThat(listed).containsExactlyInAnyOrderElementsOf(expectedObjects);
  }

  @Test
  void backupFailsIfFilesAreMissing(@TempDir Path tempDir) throws IOException {
    // given
    final var backup = prepareTestBackup(tempDir);

    // when
    Files.delete(backup.snapshot().files().stream().findFirst().orElseThrow());

    // then
    assertThatCode(() -> store.save(backup)).hasCauseInstanceOf(NoSuchFileException.class);
  }

  private TestBackup prepareTestBackup(Path tempDir) throws IOException {
    Files.createDirectory(tempDir.resolve("segments/"));
    final var seg1 = Files.createFile(tempDir.resolve("segments/segment-file-1"));
    final var seg2 = Files.createFile(tempDir.resolve("segments/segment-file-2"));
    Files.write(seg1, RandomUtils.nextBytes(1024));
    Files.write(seg2, RandomUtils.nextBytes(1024));

    Files.createDirectory(tempDir.resolve("snapshot/"));
    final var s1 = Files.createFile(tempDir.resolve("snapshot/snapshot-file-1"));
    final var s2 = Files.createFile(tempDir.resolve("snapshot/snapshot-file-2"));
    Files.write(s1, RandomUtils.nextBytes(1024));
    Files.write(s2, RandomUtils.nextBytes(1024));

    return new TestBackup(
        new TestBackupIdentifier(1, 2, 3),
        new TestBackupDescriptor(4, 5, "test-snapshot-id"),
        new TestNamedFileSet(Map.of("segment-file-1", seg1, "segment-file-2", seg2)),
        new TestNamedFileSet(Map.of("snapshot-file-1", s1, "snapshot-file-2", s2)));
  }

  record TestBackup(
      TestBackupIdentifier id,
      TestBackupDescriptor descriptor,
      TestNamedFileSet segments,
      TestNamedFileSet snapshot)
      implements Backup {}

  record TestBackupIdentifier(long checkpointId, int partitionId, int nodeId)
      implements BackupIdentifier {}

  record TestBackupDescriptor(long checkpointPosition, int numberOfPartitions, String snapshotId)
      implements BackupDescriptor {}

  record TestNamedFileSet(Map<String, Path> namedFiles) implements NamedFileSet {

    @Override
    public Set<String> names() {
      return namedFiles.keySet();
    }

    @Override
    public Set<Path> files() {
      return Set.copyOf(namedFiles.values());
    }
  }
}
