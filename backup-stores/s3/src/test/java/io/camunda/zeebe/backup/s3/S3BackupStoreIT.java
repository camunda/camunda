/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.from;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupImpl;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
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
import software.amazon.awssdk.core.async.AsyncRequestBody;
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

    final var readMetadata =
        S3BackupStore.MAPPER.readValue(metadataObject.asByteArray(), Metadata.class);

    assertThat(readMetadata.descriptor()).isEqualTo(backup.descriptor());
    assertThat(readMetadata.id()).isEqualTo(backup.id());

    assertThat(readMetadata.snapshotFileNames()).isEqualTo(backup.snapshot().names());
    assertThat(readMetadata.segmentFileNames()).isEqualTo(backup.segments().names());
  }

  @Test
  void snapshotFilesExist(@TempDir Path tempDir) throws IOException {
    // given
    final var backup = prepareTestBackup(tempDir);
    final var prefix = S3BackupStore.objectPrefix(backup.id()) + S3BackupStore.SNAPSHOT_PREFIX;

    final var expectedObjects =
        backup.snapshot().names().stream().map(name -> prefix + name).toList();

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
  void segmentFilesExist(@TempDir Path tempDir) throws IOException {
    // given
    final var backup = prepareTestBackup(tempDir);
    final var prefix = S3BackupStore.objectPrefix(backup.id()) + S3BackupStore.SEGMENTS_PREFIX;

    final var expectedObjects =
        backup.segments().names().stream().map(name -> prefix + name).toList();

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
    final var prefix = S3BackupStore.objectPrefix(backup.id());

    final var metadata = prefix + Metadata.OBJECT_KEY;
    final var status = prefix + Status.OBJECT_KEY;
    final var snapshotObjects =
        backup.snapshot().names().stream()
            .map(name -> prefix + S3BackupStore.SNAPSHOT_PREFIX + name);
    final var segmentObjects =
        backup.segments().names().stream()
            .map(name -> prefix + S3BackupStore.SEGMENTS_PREFIX + name);

    final var contentObjects = Stream.concat(snapshotObjects, segmentObjects);
    final var managementObjects = Stream.of(metadata, status);
    final var expectedObjects = Stream.concat(managementObjects, contentObjects).toList();

    // when
    store.save(backup).join();

    // then
    final var listedObjects =
        client.listObjectsV2(req -> req.bucket(config.bucketName())).join().contents().stream()
            .map(S3Object::key)
            .toList();

    assertThat(listedObjects).containsExactlyInAnyOrderElementsOf(expectedObjects);
  }

  @Test
  void backupFailsIfFilesAreMissing(@TempDir Path tempDir) throws IOException {
    // given
    final var backup = prepareTestBackup(tempDir);

    // when
    final var deletedFile = backup.snapshot().files().stream().findFirst().orElseThrow();
    Files.delete(deletedFile);

    // then
    assertThat(store.save(backup))
        .failsWithin(Duration.ofMinutes(1))
        .withThrowableOfType(Throwable.class)
        .withRootCauseInstanceOf(NoSuchFileException.class)
        .withMessageContaining(deletedFile.toString());
  }

  @Test
  void backupIsMarkedAsCompleted(@TempDir Path tempDir) throws IOException {
    // given
    final var backup = prepareTestBackup(tempDir);

    // when
    store.save(backup).join();

    // then
    final var statusObject =
        client
            .getObject(
                GetObjectRequest.builder()
                    .bucket(config.bucketName())
                    .key(S3BackupStore.objectPrefix(backup.id()) + Status.OBJECT_KEY)
                    .build(),
                AsyncResponseTransformer.toBytes())
            .join();

    final var objectMapper = new ObjectMapper();
    final var readStatus = objectMapper.readValue(statusObject.asByteArray(), Status.class);

    assertThat(readStatus.statusCode()).isEqualTo(BackupStatusCode.COMPLETED);
  }

  @Test
  void backupCanBeMarkedAsFailed(@TempDir Path tempDir) throws IOException {
    // given
    final var backup = prepareTestBackup(tempDir);

    // when
    store.save(backup).join();
    store.markFailed(backup.id()).join();

    // then
    final var statusObject =
        client
            .getObject(
                GetObjectRequest.builder()
                    .bucket(config.bucketName())
                    .key(S3BackupStore.objectPrefix(backup.id()) + Status.OBJECT_KEY)
                    .build(),
                AsyncResponseTransformer.toBytes())
            .join();

    final var objectMapper = S3BackupStore.MAPPER;
    final var readStatus = objectMapper.readValue(statusObject.asByteArray(), Status.class);

    assertThat(readStatus.statusCode()).isEqualTo(BackupStatusCode.FAILED);
    assertThat(readStatus.failureReason()).isNotEmpty();
  }

  @Test
  void canGetStatus(@TempDir Path tempDir) throws IOException {
    // given
    final var backup = prepareTestBackup(tempDir);

    // when
    store.save(backup).join();
    final var status = store.getStatus(backup.id());

    // then
    assertThat(status)
        .succeedsWithin(Duration.ofSeconds(10))
        .returns(BackupStatusCode.COMPLETED, from(BackupStatus::statusCode))
        .returns(Optional.empty(), from(BackupStatus::failureReason))
        .returns(backup.id(), from(BackupStatus::id))
        .returns(backup.descriptor(), from(BackupStatus::descriptor));
  }

  @Test
  void statusIsFailedAfterMarkingAsFailed(@TempDir Path tempDir) throws IOException {
    // given
    final var backup = prepareTestBackup(tempDir);

    // when
    store.save(backup).join();
    store.markFailed(backup.id());
    final var status = store.getStatus(backup.id());

    // then
    assertThat(status)
        .succeedsWithin(Duration.ofSeconds(10))
        .returns(BackupStatusCode.FAILED, from(BackupStatus::statusCode))
        .doesNotReturn(Optional.empty(), from(BackupStatus::failureReason))
        .returns(backup.id(), from(BackupStatus::id))
        .returns(backup.descriptor(), from(BackupStatus::descriptor));
  }

  @Test
  void statusQueryFailsIfStatusIsCorrupt(@TempDir Path tempDir) throws IOException {
    // given
    final var backup = prepareTestBackup(tempDir);
    store.save(backup).join();

    // when
    client
        .putObject(
            req ->
                req.bucket(config.bucketName())
                    .key(S3BackupStore.objectPrefix(backup.id()) + Status.OBJECT_KEY),
            AsyncRequestBody.fromString("{s"))
        .join();

    // then
    final var status = store.getStatus(backup.id());
    assertThat(status)
        .failsWithin(Duration.ofSeconds(10))
        .withThrowableOfType(Throwable.class)
        .withRootCauseInstanceOf(JsonParseException.class);
  }

  private Backup prepareTestBackup(Path tempDir) throws IOException {
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

    return new BackupImpl(
        new BackupIdentifierImpl(1, 2, 3),
        new BackupDescriptorImpl("test-snapshot-id", 4, 5),
        new NamedFileSetImpl(Map.of("segment-file-1", seg1, "segment-file-2", seg2)),
        new NamedFileSetImpl(Map.of("snapshot-file-1", s1, "snapshot-file-2", s2)));
  }
}
