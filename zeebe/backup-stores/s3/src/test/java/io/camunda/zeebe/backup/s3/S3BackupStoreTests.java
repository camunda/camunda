/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.s3;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard.CheckpointPattern;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierWildcardImpl;
import io.camunda.zeebe.backup.s3.S3BackupStore.Directory;
import io.camunda.zeebe.backup.s3.S3BackupStoreException.BackupInInvalidStateException;
import io.camunda.zeebe.backup.s3.S3BackupStoreException.ManifestParseException;
import io.camunda.zeebe.backup.s3.manifest.CompletedBackupManifest;
import io.camunda.zeebe.backup.s3.manifest.Manifest;
import io.camunda.zeebe.backup.s3.util.S3TestBackupProvider;
import io.camunda.zeebe.backup.testkit.BackupStoreTestKit;
import io.camunda.zeebe.util.SemanticVersion;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

  @ParameterizedTest
  @MethodSource("provideBackups")
  default void savesManifest(final Backup backup) throws IOException {
    // when
    getStore().save(backup).join();

    // then
    final var manifestObject =
        getClient()
            .getObject(
                GetObjectRequest.builder()
                    .bucket(getConfig().bucketName())
                    .key(
                        getStore().derivePath(backup.descriptor(), backup.id(), Directory.MANIFESTS)
                            + S3BackupStore.MANIFEST_OBJECT_KEY)
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
  @MethodSource("provideBackups")
  default void snapshotFilesExist(final Backup backup) {
    // given
    final var prefix =
        getStore().derivePath(backup.descriptor(), backup.id(), Directory.CONTENTS)
            + S3BackupStore.SNAPSHOT_PREFIX;

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
  @MethodSource("provideBackups")
  default void segmentFilesExist(final Backup backup) {
    // given
    final var prefix =
        getStore().derivePath(backup.descriptor(), backup.id(), Directory.CONTENTS)
            + S3BackupStore.SEGMENTS_PREFIX;

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
  @MethodSource("provideBackups")
  default void bucketContainsExpectedObjectsOnly(final Backup backup) {
    // given
    final var brokerVersion =
        SemanticVersion.parse(backup.descriptor().brokerVersion())
            .orElseThrow(
                () ->
                    new ManifestParseException(
                        "Invalid broker version format in backup: "
                            + backup.descriptor().brokerVersion(),
                        null));

    final var prefix =
        brokerVersion.minor() <= 8
            ? getStore().legacyObjectPrefix(backup.id())
            : getStore().objectPrefix(backup.id(), Directory.CONTENTS);

    final var manifestPrefix =
        brokerVersion.minor() <= 8
            ? getStore().legacyObjectPrefix(backup.id())
            : getStore().objectPrefix(backup.id(), Directory.MANIFESTS);

    final var manifest = manifestPrefix + S3BackupStore.MANIFEST_OBJECT_KEY;
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
  @MethodSource("provideBackups")
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
                                  .prefix(getStore().legacyObjectPrefix(backup.id())))
                      .join();
              Assertions.assertThat(listed.contents()).isEmpty();
            });
  }

  @ParameterizedTest
  @MethodSource("provideBackups")
  default void statusQueryFailsIfManifestIsCorrupt(final Backup backup) {
    // given
    getStore().save(backup).join();

    // when
    getClient()
        .putObject(
            req ->
                req.bucket(getConfig().bucketName())
                    .key(
                        getStore().derivePath(backup.descriptor(), backup.id(), Directory.MANIFESTS)
                            + S3BackupStore.MANIFEST_OBJECT_KEY),
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
  @MethodSource("provideBackups")
  default void deletingPartialBackupSucceeds(final Backup backup) {
    // given
    getStore().save(backup).join();

    // when
    getClient()
        .deleteObject(
            delete ->
                delete
                    .bucket(getConfig().bucketName())
                    .key(
                        getStore().legacyObjectPrefix(backup.id())
                            + S3BackupStore.MANIFEST_OBJECT_KEY))
        .join();

    // then
    Assertions.assertThat(getStore().delete(backup.id())).succeedsWithin(Duration.ofSeconds(10));
  }

  @ParameterizedTest
  @MethodSource("provideBackups")
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
  default void shouldListBackupsForBothStructures() throws IOException {
    // given
    final var legacyBackup =
        S3TestBackupProvider.simpleBackupWithId(new BackupIdentifierImpl(1, 2, 3), true);
    final var backup =
        S3TestBackupProvider.simpleBackupWithId(new BackupIdentifierImpl(1, 2, 10), false);

    getStore().save(legacyBackup).join();
    getStore().save(backup).join();

    // when
    final var pattern =
        new BackupIdentifierWildcardImpl(Optional.of(1), Optional.of(2), CheckpointPattern.any());
    final var listedBackups = getStore().list(pattern).join();

    // then
    assertThat(listedBackups)
        .hasSize(2)
        .extracting(BackupStatus::id)
        .containsExactlyInAnyOrder(legacyBackup.id(), backup.id());
  }

  @Test
  default void backupStructureShouldBeDistinct() throws IOException {
    // given
    final var legacyBackup =
        S3TestBackupProvider.simpleBackupWithId(new BackupIdentifierImpl(1, 2, 3), true);
    final var backup =
        S3TestBackupProvider.simpleBackupWithId(new BackupIdentifierImpl(1, 2, 10), false);

    // when
    getStore().save(legacyBackup).join();
    getStore().save(backup).join();

    // then

    // no objects exist under the legacy prefix for the new version backup
    assertThat(
            getClient()
                .listObjectsV2(
                    req ->
                        req.bucket(getConfig().bucketName())
                            .prefix(getStore().legacyObjectPrefix(backup.id())))
                .join()
                .contents())
        .isEmpty();

    final var legacyContent =
        getClient()
            .listObjectsV2(
                req ->
                    req.bucket(getConfig().bucketName())
                        .prefix(getStore().legacyObjectPrefix(legacyBackup.id())))
            .join()
            .contents();

    // a single manifest exists
    assertThat(legacyContent.stream().filter(s -> s.key().endsWith("manifest.json")).count())
        .isOne();

    final var legacySegmentFiles = legacyBackup.segments().names();
    final var legacySnapshotFiles = legacyBackup.snapshot().names();
    final var legacyPath = getStore().legacyObjectPrefix(legacyBackup.id());
    final var legacyFileNames =
        Stream.concat(
                legacySegmentFiles.stream().map(name -> "segments/" + name),
                legacySnapshotFiles.stream().map(name -> "snapshot/" + name))
            .collect(Collectors.joining("|"));
    final var legacyContentRegex = Pattern.quote(legacyPath) + "(" + legacyFileNames + ")";

    // all objects follow the legacy pattern
    assertThat(legacyContent.stream().filter(f -> !f.key().endsWith("manifest.json")).toList())
        .allSatisfy(obj -> assertThat(obj.key()).matches(legacyContentRegex));

    // New structured backup has a single manifest
    final var manifestPath = getStore().objectPrefix(backup.id(), Directory.MANIFESTS);
    assertThat(
            getClient()
                .listObjectsV2(req -> req.bucket(getConfig().bucketName()).prefix(manifestPath))
                .join()
                .contents())
        .hasSize(1)
        .satisfiesOnlyOnce(s -> assertThat(s.key()).isEqualTo(manifestPath + "manifest.json"));

    final var segmentFiles = backup.segments().names();
    final var snapshotFiles = backup.snapshot().names();
    final var path = getStore().objectPrefix(backup.id(), Directory.CONTENTS);
    final var fileNames =
        Stream.concat(
                segmentFiles.stream().map(name -> "segments/" + name),
                snapshotFiles.stream().map(name -> "snapshot/" + name))
            .collect(Collectors.joining("|"));
    final var contentRegex = Pattern.quote(path) + "(" + fileNames + ")";

    // all objects follow the new structure pattern for the new backup
    assertThat(
            getClient()
                .listObjectsV2(req -> req.bucket(getConfig().bucketName()).prefix(path))
                .join()
                .contents())
        .allSatisfy(obj -> assertThat(obj.key()).matches(contentRegex));

    // no objects exist under the old structure for the new backup
    assertThat(
            getClient()
                .listObjectsV2(
                    req ->
                        req.bucket(getConfig().bucketName())
                            .prefix(getStore().legacyObjectPrefix(backup.id())))
                .join()
                .contents())
        .isEmpty();
  }

  static Stream<? extends Arguments> provideBackups() throws Exception {
    return S3TestBackupProvider.provideArguments();
  }
}
