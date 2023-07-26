/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.s3;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupImpl;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import io.camunda.zeebe.backup.s3.S3BackupStoreException.BackupInInvalidStateException;
import io.camunda.zeebe.backup.s3.S3BackupStoreException.ManifestParseException;
import io.camunda.zeebe.backup.s3.manifest.CompletedBackupManifest;
import io.camunda.zeebe.backup.s3.manifest.Manifest;
import io.camunda.zeebe.backup.testkit.BackupStoreTestKit;
import io.camunda.zeebe.backup.testkit.support.TestBackupProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.apache.commons.lang3.RandomUtils;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

public interface S3BackupStoreTests extends BackupStoreTestKit {

  S3AsyncClient getClient();

  S3BackupConfig getConfig();

  @Override
  S3BackupStore getStore();

  @Override
  default Class<? extends Exception> getBackupInInvalidStateExceptionClass() {
    return BackupInInvalidStateException.class;
  }

  void setConfigParallelConnectionsAndTimeout(
      final int parallelConnections, final Duration timeout);

  @ParameterizedTest
  @ArgumentsSource(TestBackupProvider.class)
  default void savesManifest(final Backup backup) throws IOException {
    // when
    getStore().save(backup).join();

    // then
    final var manifestObject =
        getClient()
            .getObject(
                GetObjectRequest.builder()
                    .bucket(getConfig().bucketName())
                    .key(getStore().objectPrefix(backup.id()) + S3BackupStore.MANIFEST_OBJECT_KEY)
                    .build(),
                AsyncResponseTransformer.toBytes())
            .join();

    final var readManifest =
        S3BackupStore.MAPPER.readValue(manifestObject.asByteArray(), CompletedBackupManifest.class);

    assertThat(readManifest.descriptor()).isEqualTo(backup.descriptor());
    assertThat(readManifest.id()).isEqualTo(backup.id());

    assertThat(readManifest.createdAt())
        .isBeforeOrEqualTo(Instant.now())
        .isBeforeOrEqualTo(readManifest.modifiedAt());
    assertThat(readManifest.modifiedAt()).isBeforeOrEqualTo(Instant.now());

    assertThat(readManifest.snapshotFiles().names()).isEqualTo(backup.snapshot().names());
    assertThat(readManifest.segmentFiles().names()).isEqualTo(backup.segments().names());
  }

  @ParameterizedTest
  @ArgumentsSource(TestBackupProvider.class)
  default void snapshotFilesExist(final Backup backup) {
    // given
    final var prefix = getStore().objectPrefix(backup.id()) + S3BackupStore.SNAPSHOT_PREFIX;

    final var expectedObjects =
        backup.snapshot().names().stream().map(name -> prefix + name).toList();

    // when
    getStore().save(backup).join();

    // then
    final var listed =
        getClient()
            .listObjectsV2(req -> req.bucket(getConfig().bucketName()).prefix(prefix))
            .join();

    Assertions.assertThat(listed.contents().stream().map(S3Object::key))
        .allSatisfy(k -> Assertions.assertThat(k).startsWith(prefix).isIn(expectedObjects));
  }

  @ParameterizedTest
  @ArgumentsSource(TestBackupProvider.class)
  default void segmentFilesExist(final Backup backup) {
    // given
    final var prefix = getStore().objectPrefix(backup.id()) + S3BackupStore.SEGMENTS_PREFIX;

    final var expectedObjects =
        backup.segments().names().stream().map(name -> prefix + name).toList();

    // when
    getStore().save(backup).join();

    // then
    final var listed =
        getClient()
            .listObjectsV2(req -> req.bucket(getConfig().bucketName()).prefix(prefix))
            .join();

    Assertions.assertThat(listed.contents().stream().map(S3Object::key))
        .allSatisfy(k -> Assertions.assertThat(k).startsWith(prefix).isIn(expectedObjects));
  }

  @ParameterizedTest
  @ArgumentsSource(TestBackupProvider.class)
  default void bucketContainsExpectedObjectsOnly(final Backup backup) {
    // given
    final var prefix = getStore().objectPrefix(backup.id());

    final var manifest = prefix + S3BackupStore.MANIFEST_OBJECT_KEY;
    final var snapshotObjects =
        backup.snapshot().names().stream()
            .map(name -> prefix + S3BackupStore.SNAPSHOT_PREFIX + name);
    final var segmentObjects =
        backup.segments().names().stream()
            .map(name -> prefix + S3BackupStore.SEGMENTS_PREFIX + name);

    final var contentObjects = Stream.concat(snapshotObjects, segmentObjects);
    final var managementObjects = Stream.of(manifest);
    final var expectedObjects = Stream.concat(managementObjects, contentObjects).toList();

    // when
    getStore().save(backup).join();

    // then
    final var listedObjects =
        getClient()
            .listObjectsV2(
                req ->
                    req.bucket(getConfig().bucketName())
                        .prefix(getConfig().basePath().map(base -> base + "/").orElse("")))
            .join()
            .contents()
            .stream()
            .map(S3Object::key)
            .toList();

    assertThat(listedObjects).containsExactlyInAnyOrderElementsOf(expectedObjects);
  }

  @ParameterizedTest
  @ArgumentsSource(TestBackupProvider.class)
  default void allBackupObjectsAreDeleted(final Backup backup) {
    // given
    getStore().save(backup).join();

    // when
    getStore().delete(backup.id()).join();

    // then

    // Retry a couple of times because LocalStack takes a moment to actually delete every object.
    Awaitility.await("Finds no objects after deleting")
        .pollInterval(Duration.ofSeconds(1))
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final var listed =
                  getClient()
                      .listObjectsV2(
                          req ->
                              req.bucket(getConfig().bucketName())
                                  .prefix(getStore().objectPrefix(backup.id())))
                      .join();
              Assertions.assertThat(listed.contents()).isEmpty();
            });
  }

  @ParameterizedTest
  @ArgumentsSource(TestBackupProvider.class)
  default void statusQueryFailsIfManifestIsCorrupt(final Backup backup) {
    // given
    getStore().save(backup).join();

    // when
    getClient()
        .putObject(
            req ->
                req.bucket(getConfig().bucketName())
                    .key(getStore().objectPrefix(backup.id()) + S3BackupStore.MANIFEST_OBJECT_KEY),
            AsyncRequestBody.fromString("{s"))
        .join();

    // then
    final var status = getStore().getStatus(backup.id());
    assertThat(status)
        .failsWithin(Duration.ofSeconds(10))
        .withThrowableOfType(Throwable.class)
        .withCauseInstanceOf(ManifestParseException.class);
  }

  @ParameterizedTest
  @ArgumentsSource(TestBackupProvider.class)
  default void deletingPartialBackupSucceeds(final Backup backup) {
    // given
    getStore().save(backup).join();

    // when
    getClient()
        .deleteObject(
            delete ->
                delete
                    .bucket(getConfig().bucketName())
                    .key(getStore().objectPrefix(backup.id()) + S3BackupStore.MANIFEST_OBJECT_KEY))
        .join();

    // then
    Assertions.assertThat(getStore().delete(backup.id())).succeedsWithin(Duration.ofSeconds(10));
  }

  @ParameterizedTest
  @ArgumentsSource(TestBackupProvider.class)
  default void deletingInProgressBackupFails(final Backup backup) {
    // given
    getStore().save(backup).join();

    // when
    getStore().updateManifestObject(backup.id(), manifest -> Manifest.fromNewBackup(backup)).join();
    final var delete = getStore().delete(backup.id());

    // then
    Assertions.assertThat(delete)
        .failsWithin(Duration.ofSeconds(10))
        .withThrowableOfType(Throwable.class)
        .withRootCauseInstanceOf(BackupInInvalidStateException.class);
  }

  @Test
  default void shouldSaveBackupWithManyFiles() throws IOException {
    // given
    final CompletableFuture<Void> saveFuture = getStore().save(largeNumberOfSegmentsBackup(5_000));

    // then
    Assertions.assertThat(saveFuture).succeedsWithin(Duration.ofSeconds(60));
  }

  @Test
  default void shouldNotTimeoutForAcquisitionOfConnection() throws IOException {
    // given
    // Even with just one connection, and low timeout limit, the second upload should not start
    // until a connection is available, and therefore should not throw AcquisitionConnectionTimeout
    setConfigParallelConnectionsAndTimeout(1, Duration.ofMillis(50));
    final CompletableFuture<Void> saveFuture = getStore().save(configurableLargeBackup(500_000));

    // then
    Assertions.assertThat(saveFuture).succeedsWithin(Duration.ofSeconds(60));
  }

  default Backup largeNumberOfSegmentsBackup(final int numberOfSegments) throws IOException {
    final var tempDir = Files.createTempDirectory("backup");
    Files.createDirectory(tempDir.resolve("segments/"));
    Files.createDirectory(tempDir.resolve("snapshot/"));
    final Map<String, Path> largeNumberOfSegments = new HashMap<>();
    final var s1 = Files.createFile(tempDir.resolve("snapshot/snapshot-file-1"));
    final var s2 = Files.createFile(tempDir.resolve("snapshot/snapshot-file-2"));

    for (int i = 0; i < numberOfSegments; i++) {
      largeNumberOfSegments.put(
          "segment-file-%d".formatted(i),
          Files.createFile(tempDir.resolve("segments/segment-file-%d".formatted(i))));
    }

    return new BackupImpl(
        new BackupIdentifierImpl(1, 2, 3),
        new BackupDescriptorImpl(Optional.of("test-snapshot-id"), 4, 5, "test"),
        new NamedFileSetImpl(largeNumberOfSegments),
        new NamedFileSetImpl(Map.of("snapshot-file-1", s1, "snapshot-file-2", s2)));
  }

  default Backup configurableLargeBackup(final int sizeOfFileInBytes) throws IOException {
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
        new BackupDescriptorImpl(Optional.of("test-snapshot-id"), 4, 5, "test"),
        new NamedFileSetImpl(Map.of("snapshot-file-1", s1, "snapshot-file-2", s2)),
        new NamedFileSetImpl(Map.of("segment-file-1", seg1, "segment-file-2", seg2)));
  }
}
