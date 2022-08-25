/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.s3;

import static io.camunda.zeebe.backup.s3.BackupAssert.assertThatBackup;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.from;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.s3.S3BackupStoreException.BackupInInvalidStateException;
import io.camunda.zeebe.backup.s3.S3BackupStoreException.MetadataParseException;
import io.camunda.zeebe.backup.s3.S3BackupStoreException.StatusParseException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
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

  @ParameterizedTest
  @ArgumentsSource(TestBackupProvider.class)
  void savingBackupIsSuccessful(Backup backup) {
    assertThat(store.save(backup)).succeedsWithin(Duration.ofSeconds(10));
  }

  @ParameterizedTest
  @ArgumentsSource(TestBackupProvider.class)
  void savesMetadata(Backup backup) throws IOException {
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

  @ParameterizedTest
  @ArgumentsSource(TestBackupProvider.class)
  void snapshotFilesExist(Backup backup) {
    // given
    final var prefix = S3BackupStore.objectPrefix(backup.id()) + S3BackupStore.SNAPSHOT_PREFIX;

    final var expectedObjects =
        backup.snapshot().names().stream().map(name -> prefix + name).toList();

    // when
    store.save(backup).join();

    // then
    final var listed =
        client.listObjectsV2(req -> req.bucket(config.bucketName()).prefix(prefix)).join();

    assertThat(listed.contents().stream().map(S3Object::key))
        .allSatisfy(k -> assertThat(k).startsWith(prefix).isIn(expectedObjects));
  }

  @ParameterizedTest
  @ArgumentsSource(TestBackupProvider.class)
  void segmentFilesExist(Backup backup) {
    // given
    final var prefix = S3BackupStore.objectPrefix(backup.id()) + S3BackupStore.SEGMENTS_PREFIX;

    final var expectedObjects =
        backup.segments().names().stream().map(name -> prefix + name).toList();

    // when
    store.save(backup).join();

    // then
    final var listed =
        client.listObjectsV2(req -> req.bucket(config.bucketName()).prefix(prefix)).join();

    assertThat(listed.contents().stream().map(S3Object::key))
        .allSatisfy(k -> assertThat(k).startsWith(prefix).isIn(expectedObjects));
  }

  @ParameterizedTest
  @ArgumentsSource(TestBackupProvider.class)
  void bucketContainsExpectedObjectsOnly(Backup backup) {
    // given
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

  @ParameterizedTest
  @ArgumentsSource(TestBackupProvider.class)
  void backupFailsIfBackupAlreadyExists(Backup backup) {
    // when
    store.save(backup).join();

    // then
    assertThat(store.save(backup))
        .failsWithin(Duration.ofSeconds(10))
        .withThrowableOfType(Throwable.class)
        .withRootCauseInstanceOf(BackupInInvalidStateException.class);
  }

  @ParameterizedTest
  @ArgumentsSource(TestBackupProvider.class)
  void backupFailsIfFilesAreMissing(Backup backup) throws IOException {
    // when
    final var deletedFile = backup.segments().files().stream().findFirst().orElseThrow();
    Files.delete(deletedFile);

    // then
    assertThat(store.save(backup))
        .failsWithin(Duration.ofMinutes(1))
        .withThrowableOfType(Throwable.class)
        .withRootCauseInstanceOf(NoSuchFileException.class)
        .withMessageContaining(deletedFile.toString());
  }

  @ParameterizedTest
  @ArgumentsSource(TestBackupProvider.class)
  void backupIsMarkedAsCompleted(Backup backup) throws IOException {
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

  @ParameterizedTest
  @ArgumentsSource(TestBackupProvider.class)
  void backupCanBeMarkedAsFailed(Backup backup) throws IOException {
    // given
    store.save(backup).join();

    // when
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

  @ParameterizedTest
  @ArgumentsSource(TestBackupProvider.class)
  void canGetStatus(Backup backup) {
    // given
    store.save(backup).join();

    // when
    final var status = store.getStatus(backup.id());

    // then
    assertThat(status)
        .succeedsWithin(Duration.ofSeconds(10))
        .returns(BackupStatusCode.COMPLETED, from(BackupStatus::statusCode))
        .returns(Optional.empty(), from(BackupStatus::failureReason))
        .returns(backup.id(), from(BackupStatus::id))
        .returns(Optional.of(backup.descriptor()), from(BackupStatus::descriptor));
  }

  @ParameterizedTest
  @ArgumentsSource(TestBackupProvider.class)
  void statusIsFailedAfterMarkingAsFailed(Backup backup) {
    // given
    store.save(backup).join();

    // when
    store.markFailed(backup.id()).join();
    final var status = store.getStatus(backup.id());

    // then
    assertThat(status)
        .succeedsWithin(Duration.ofSeconds(10))
        .returns(BackupStatusCode.FAILED, from(BackupStatus::statusCode))
        .doesNotReturn(Optional.empty(), from(BackupStatus::failureReason))
        .returns(backup.id(), from(BackupStatus::id))
        .returns(Optional.of(backup.descriptor()), from(BackupStatus::descriptor));
  }

  @ParameterizedTest
  @ArgumentsSource(TestBackupProvider.class)
  void statusQueryFailsIfStatusIsCorrupt(Backup backup) {
    // given
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
        .withCauseInstanceOf(StatusParseException.class);
  }

  @ParameterizedTest
  @ArgumentsSource(TestBackupProvider.class)
  void statusQueryFailsIfMetadataIsCorrupt(Backup backup) {
    // given
    store.save(backup).join();

    // when
    client
        .putObject(
            req ->
                req.bucket(config.bucketName())
                    .key(S3BackupStore.objectPrefix(backup.id()) + Metadata.OBJECT_KEY),
            AsyncRequestBody.fromString("{s"))
        .join();

    // then
    final var status = store.getStatus(backup.id());
    assertThat(status)
        .failsWithin(Duration.ofSeconds(10))
        .withThrowableOfType(Throwable.class)
        .withCauseInstanceOf(MetadataParseException.class);
  }

  @Test
  void statusQueryFailsIfBackupDoesNotExist() {
    // when
    final var result = store.getStatus(new BackupIdentifierImpl(1, 1, 15));
    // then
    assertThat(result)
        .succeedsWithin(Duration.ofSeconds(10))
        .returns(BackupStatusCode.DOES_NOT_EXIST, from(BackupStatus::statusCode));
  }

  @ParameterizedTest
  @ArgumentsSource(TestBackupProvider.class)
  void allBackupObjectsAreDeleted(Backup backup) {
    // given
    store.save(backup).join();

    // when
    store.delete(backup.id()).join();

    // then
    final var listed =
        client
            .listObjectsV2(
                req ->
                    req.bucket(config.bucketName()).prefix(S3BackupStore.objectPrefix(backup.id())))
            .join();
    assertThat(listed.contents()).isEmpty();
  }

  @Test
  void deletingNonExistingBackupSucceeds() {
    // when
    final var delete = store.delete(new BackupIdentifierImpl(1, 2, 3));

    // then
    assertThat(delete).succeedsWithin(Duration.ofSeconds(10));
  }

  @ParameterizedTest
  @ArgumentsSource(TestBackupProvider.class)
  void deletingPartialBackupSucceeds(Backup backup) {
    // given
    store.save(backup).join();

    // when
    client
        .deleteObject(
            delete ->
                delete
                    .bucket(config.bucketName())
                    .key(S3BackupStore.objectPrefix(backup.id()) + Status.OBJECT_KEY))
        .join();

    // then
    assertThat(store.delete(backup.id())).succeedsWithin(Duration.ofSeconds(10));
  }

  @ParameterizedTest
  @ArgumentsSource(TestBackupProvider.class)
  void deletingInProgressBackupFails(Backup backup) {
    // given
    store.save(backup).join();

    // when
    store.setStatus(backup.id(), new Status(BackupStatusCode.IN_PROGRESS)).join();
    final var delete = store.delete(backup.id());

    // then
    assertThat(delete)
        .failsWithin(Duration.ofSeconds(10))
        .withThrowableOfType(Throwable.class)
        .withRootCauseInstanceOf(BackupInInvalidStateException.class);
  }

  @ParameterizedTest
  @ArgumentsSource(TestBackupProvider.class)
  void restoreIsSuccessful(Backup backup, @TempDir Path targetDir) {
    // given
    store.save(backup).join();

    // when
    final var result = store.restore(backup.id(), targetDir);

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(10));
  }

  @ParameterizedTest
  @ArgumentsSource(TestBackupProvider.class)
  void restoredBackupHasSameContents(Backup originalBackup, @TempDir Path targetDir) {
    // given
    store.save(originalBackup).join();

    // when
    final var restored = store.restore(originalBackup.id(), targetDir).join();

    // then
    assertThatBackup(restored).hasSameContentsAs(originalBackup).residesInPath(targetDir);
  }
}
