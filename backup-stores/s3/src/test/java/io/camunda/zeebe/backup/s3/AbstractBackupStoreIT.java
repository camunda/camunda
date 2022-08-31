/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.s3;

import static io.camunda.zeebe.backup.s3.support.BackupAssert.assertThatBackup;
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
import io.camunda.zeebe.backup.s3.support.TestBackupProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

@Execution(ExecutionMode.CONCURRENT)
public abstract class AbstractBackupStoreIT {

  protected abstract S3AsyncClient getClient();

  protected abstract S3BackupConfig getConfig();

  protected abstract S3BackupStore getStore();

  @Nested
  final class Saving {

    @ParameterizedTest
    @ArgumentsSource(TestBackupProvider.class)
    void savingBackupIsSuccessful(final Backup backup) {
      assertThat(getStore().save(backup)).succeedsWithin(Duration.ofSeconds(10));
    }

    @ParameterizedTest
    @ArgumentsSource(TestBackupProvider.class)
    void savesMetadata(final Backup backup) throws IOException {
      // when
      getStore().save(backup).join();

      // then
      final var metadataObject =
          getClient()
              .getObject(
                  GetObjectRequest.builder()
                      .bucket(getConfig().bucketName())
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
    void snapshotFilesExist(final Backup backup) {
      // given
      final var prefix = S3BackupStore.objectPrefix(backup.id()) + S3BackupStore.SNAPSHOT_PREFIX;

      final var expectedObjects =
          backup.snapshot().names().stream().map(name -> prefix + name).toList();

      // when
      getStore().save(backup).join();

      // then
      final var listed =
          getClient()
              .listObjectsV2(req -> req.bucket(getConfig().bucketName()).prefix(prefix))
              .join();

      assertThat(listed.contents().stream().map(S3Object::key))
          .allSatisfy(k -> assertThat(k).startsWith(prefix).isIn(expectedObjects));
    }

    @ParameterizedTest
    @ArgumentsSource(TestBackupProvider.class)
    void segmentFilesExist(final Backup backup) {
      // given
      final var prefix = S3BackupStore.objectPrefix(backup.id()) + S3BackupStore.SEGMENTS_PREFIX;

      final var expectedObjects =
          backup.segments().names().stream().map(name -> prefix + name).toList();

      // when
      getStore().save(backup).join();

      // then
      final var listed =
          getClient()
              .listObjectsV2(req -> req.bucket(getConfig().bucketName()).prefix(prefix))
              .join();

      assertThat(listed.contents().stream().map(S3Object::key))
          .allSatisfy(k -> assertThat(k).startsWith(prefix).isIn(expectedObjects));
    }

    @ParameterizedTest
    @ArgumentsSource(TestBackupProvider.class)
    void bucketContainsExpectedObjectsOnly(final Backup backup) {
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
      getStore().save(backup).join();

      // then
      final var listedObjects =
          getClient()
              .listObjectsV2(req -> req.bucket(getConfig().bucketName()))
              .join()
              .contents()
              .stream()
              .map(S3Object::key)
              .toList();

      assertThat(listedObjects).containsExactlyInAnyOrderElementsOf(expectedObjects);
    }

    @ParameterizedTest
    @ArgumentsSource(TestBackupProvider.class)
    void backupFailsIfBackupAlreadyExists(final Backup backup) {
      // when
      getStore().save(backup).join();

      // then
      assertThat(getStore().save(backup))
          .failsWithin(Duration.ofSeconds(10))
          .withThrowableOfType(Throwable.class)
          .withRootCauseInstanceOf(BackupInInvalidStateException.class);
    }

    @ParameterizedTest
    @ArgumentsSource(TestBackupProvider.class)
    void backupFailsIfFilesAreMissing(final Backup backup) throws IOException {
      // when
      final var deletedFile = backup.segments().files().stream().findFirst().orElseThrow();
      Files.delete(deletedFile);

      // then
      assertThat(getStore().save(backup))
          .failsWithin(Duration.ofMinutes(1))
          .withThrowableOfType(Throwable.class)
          .withRootCauseInstanceOf(NoSuchFileException.class)
          .withMessageContaining(deletedFile.toString());
    }
  }

  @Nested
  final class UpdatingStatus {

    @ParameterizedTest
    @ArgumentsSource(TestBackupProvider.class)
    void backupIsMarkedAsCompleted(final Backup backup) throws IOException {
      // when
      getStore().save(backup).join();

      // then
      final var statusObject =
          getClient()
              .getObject(
                  GetObjectRequest.builder()
                      .bucket(getConfig().bucketName())
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
    void backupCanBeMarkedAsFailed(final Backup backup) throws IOException {
      // given
      getStore().save(backup).join();

      // when
      getStore().markFailed(backup.id()).join();

      // then
      final var statusObject =
          getClient()
              .getObject(
                  GetObjectRequest.builder()
                      .bucket(getConfig().bucketName())
                      .key(S3BackupStore.objectPrefix(backup.id()) + Status.OBJECT_KEY)
                      .build(),
                  AsyncResponseTransformer.toBytes())
              .join();

      final var objectMapper = S3BackupStore.MAPPER;
      final var readStatus = objectMapper.readValue(statusObject.asByteArray(), Status.class);

      assertThat(readStatus.statusCode()).isEqualTo(BackupStatusCode.FAILED);
      assertThat(readStatus.failureReason()).isNotEmpty();
    }
  }

  @Nested
  final class QueryingStatus {

    @ParameterizedTest
    @ArgumentsSource(TestBackupProvider.class)
    void canGetStatus(final Backup backup) {
      // given
      getStore().save(backup).join();

      // when
      final var status = getStore().getStatus(backup.id());

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
    void statusIsFailedAfterMarkingAsFailed(final Backup backup) {
      // given
      getStore().save(backup).join();

      // when
      getStore().markFailed(backup.id()).join();
      final var status = getStore().getStatus(backup.id());

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
    void statusQueryFailsIfStatusIsCorrupt(final Backup backup) {
      // given
      getStore().save(backup).join();

      // when
      getClient()
          .putObject(
              req ->
                  req.bucket(getConfig().bucketName())
                      .key(S3BackupStore.objectPrefix(backup.id()) + Status.OBJECT_KEY),
              AsyncRequestBody.fromString("{s"))
          .join();

      // then
      final var status = getStore().getStatus(backup.id());
      assertThat(status)
          .failsWithin(Duration.ofSeconds(10))
          .withThrowableOfType(Throwable.class)
          .withCauseInstanceOf(StatusParseException.class);
    }

    @ParameterizedTest
    @ArgumentsSource(TestBackupProvider.class)
    void statusQueryFailsIfMetadataIsCorrupt(final Backup backup) {
      // given
      getStore().save(backup).join();

      // when
      getClient()
          .putObject(
              req ->
                  req.bucket(getConfig().bucketName())
                      .key(S3BackupStore.objectPrefix(backup.id()) + Metadata.OBJECT_KEY),
              AsyncRequestBody.fromString("{s"))
          .join();

      // then
      final var status = getStore().getStatus(backup.id());
      assertThat(status)
          .failsWithin(Duration.ofSeconds(10))
          .withThrowableOfType(Throwable.class)
          .withCauseInstanceOf(MetadataParseException.class);
    }

    @Test
    void statusQueryFailsIfBackupDoesNotExist() {
      // when
      final var result = getStore().getStatus(new BackupIdentifierImpl(1, 1, 15));
      // then
      assertThat(result)
          .succeedsWithin(Duration.ofSeconds(10))
          .returns(BackupStatusCode.DOES_NOT_EXIST, from(BackupStatus::statusCode));
    }
  }

  @Nested
  final class Deleting {

    @ParameterizedTest
    @ArgumentsSource(TestBackupProvider.class)
    void allBackupObjectsAreDeleted(final Backup backup) {
      // given
      getStore().save(backup).join();

      // when
      getStore().delete(backup.id()).join();

      // then
      final var listed =
          getClient()
              .listObjectsV2(
                  req ->
                      req.bucket(getConfig().bucketName())
                          .prefix(S3BackupStore.objectPrefix(backup.id())))
              .join();
      assertThat(listed.contents()).isEmpty();
    }

    @Test
    void deletingNonExistingBackupSucceeds() {
      // when
      final var delete = getStore().delete(new BackupIdentifierImpl(1, 2, 3));

      // then
      assertThat(delete).succeedsWithin(Duration.ofSeconds(10));
    }

    @ParameterizedTest
    @ArgumentsSource(TestBackupProvider.class)
    void deletingPartialBackupSucceeds(final Backup backup) {
      // given
      getStore().save(backup).join();

      // when
      getClient()
          .deleteObject(
              delete ->
                  delete
                      .bucket(getConfig().bucketName())
                      .key(S3BackupStore.objectPrefix(backup.id()) + Status.OBJECT_KEY))
          .join();

      // then
      assertThat(getStore().delete(backup.id())).succeedsWithin(Duration.ofSeconds(10));
    }

    @ParameterizedTest
    @ArgumentsSource(TestBackupProvider.class)
    void deletingInProgressBackupFails(final Backup backup) {
      // given
      getStore().save(backup).join();

      // when
      getStore().setStatus(backup.id(), new Status(BackupStatusCode.IN_PROGRESS)).join();
      final var delete = getStore().delete(backup.id());

      // then
      assertThat(delete)
          .failsWithin(Duration.ofSeconds(10))
          .withThrowableOfType(Throwable.class)
          .withRootCauseInstanceOf(BackupInInvalidStateException.class);
    }
  }

  @Nested
  final class Restoring {

    @ParameterizedTest
    @ArgumentsSource(TestBackupProvider.class)
    void restoreIsSuccessful(final Backup backup, @TempDir final Path targetDir) {
      // given
      getStore().save(backup).join();

      // when
      final var result = getStore().restore(backup.id(), targetDir);

      // then
      assertThat(result).succeedsWithin(Duration.ofSeconds(10));
    }

    @ParameterizedTest
    @ArgumentsSource(TestBackupProvider.class)
    void restoredBackupHasSameContents(final Backup originalBackup, @TempDir final Path targetDir) {
      // given
      getStore().save(originalBackup).join();

      // when
      final var restored = getStore().restore(originalBackup.id(), targetDir).join();

      // then
      assertThatBackup(restored).hasSameContentsAs(originalBackup).residesInPath(targetDir);
    }
  }
}
