/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.gcs;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Storage.BlobTargetOption;
import com.google.cloud.storage.StorageException;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupImpl;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import io.camunda.zeebe.backup.gcs.GcsBackupStoreException.UnexpectedManifestState;
import io.camunda.zeebe.backup.gcs.manifest.Manifest;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

final class ManifestManagerTest {
  @Test
  void shouldCreateInitialManifest() {
    // given
    final var client = Mockito.mock(Storage.class);
    final var manager = new ManifestManager(client, BucketInfo.of("bucket"), "basePath");
    final var backup =
        new BackupImpl(
            new BackupIdentifierImpl(1, 2, 3),
            new BackupDescriptorImpl(Optional.empty(), 1, 1, "version"),
            new NamedFileSetImpl(
                Map.of("snapshotFile1", Path.of("file1"), "snapshotFile2", Path.of("file2"))),
            new NamedFileSetImpl(Map.of("segmentFile1", Path.of("file3"))));
    final var expected = Manifest.createInProgress(backup);

    final var blob = Mockito.mock(Blob.class);
    Mockito.when(blob.getGeneration()).thenReturn(1L);
    Mockito.when(
            client.create(Mockito.any(BlobInfo.class), Mockito.any(byte[].class), Mockito.any()))
        .thenReturn(blob);

    // when
    final var persisted = manager.createInitialManifest(backup);

    // then
    Assertions.assertThat(persisted.generation()).isEqualTo(1L);
    Assertions.assertThat(persisted.manifest())
        .usingRecursiveComparison()
        .ignoringFields("modifiedAt", "createdAt")
        .isEqualTo(expected);
  }

  @Test
  void shouldCompleteManifest() throws IOException {
    // given
    final var client = Mockito.mock(Storage.class);
    final var manager = new ManifestManager(client, BucketInfo.of("bucket"), "basePath");
    final var backup =
        new BackupImpl(
            new BackupIdentifierImpl(1, 2, 3),
            new BackupDescriptorImpl(Optional.empty(), 1, 1, "version"),
            new NamedFileSetImpl(
                Map.of("snapshotFile1", Path.of("file1"), "snapshotFile2", Path.of("file2"))),
            new NamedFileSetImpl(Map.of("segmentFile1", Path.of("file3"))));
    final var expectedManifest = Manifest.createInProgress(backup).complete();

    final var blob = Mockito.mock(Blob.class);
    Mockito.when(blob.getGeneration()).thenReturn(1L);
    Mockito.when(
            client.create(Mockito.any(BlobInfo.class), Mockito.any(byte[].class), Mockito.any()))
        .thenReturn(blob);
    final var persisted = manager.createInitialManifest(backup);

    // when
    manager.completeManifest(persisted);

    // then
    final var captor = ArgumentCaptor.forClass(byte[].class);
    Mockito.verify(client)
        .create(
            Mockito.any(BlobInfo.class),
            captor.capture(),
            Mockito.eq(BlobTargetOption.generationMatch(persisted.generation())));

    final var actualManifest = ManifestManager.MAPPER.readValue(captor.getValue(), Manifest.class);
    Assertions.assertThat(actualManifest)
        .usingRecursiveComparison()
        .ignoringFields("modifiedAt", "createdAt")
        .isEqualTo(expectedManifest);
    Assertions.assertThat(actualManifest.createdAt()).isEqualTo(persisted.manifest().createdAt());
  }

  @Test
  void shouldThrowWhenManifestAlreadyExists() {
    // given
    final var client = Mockito.mock(Storage.class);
    final var manager = new ManifestManager(client, BucketInfo.of("bucket"), "basePath");
    final var backup =
        new BackupImpl(
            new BackupIdentifierImpl(1, 2, 3),
            new BackupDescriptorImpl(Optional.empty(), 1, 1, "version"),
            new NamedFileSetImpl(Map.of()),
            new NamedFileSetImpl(Map.of()));

    // when
    Mockito.when(
            client.create(Mockito.any(BlobInfo.class), Mockito.any(byte[].class), Mockito.any()))
        .thenThrow(new StorageException(412, "expected"));

    // then
    Assertions.assertThatThrownBy(() -> manager.createInitialManifest(backup))
        .isInstanceOf(UnexpectedManifestState.class)
        .hasMessageContaining("but already exists");
  }

  @Test
  void shouldThrowWhenUnexpectedStorageExceptionOccurs() {
    // given
    final var client = Mockito.mock(Storage.class);
    final var manager = new ManifestManager(client, BucketInfo.of("bucket"), "basePath");
    final var backup =
        new BackupImpl(
            new BackupIdentifierImpl(1, 2, 3),
            new BackupDescriptorImpl(Optional.empty(), 1, 1, "version"),
            new NamedFileSetImpl(Map.of()),
            new NamedFileSetImpl(Map.of()));

    // when
    Mockito.when(
            client.create(Mockito.any(BlobInfo.class), Mockito.any(byte[].class), Mockito.any()))
        .thenThrow(new StorageException(500, "expected but unhandled"));

    // then
    Assertions.assertThatThrownBy(() -> manager.createInitialManifest(backup))
        .isInstanceOf(StorageException.class)
        .hasMessageContaining("expected but unhandled");
  }

  @Test
  void shouldThrowWhenManifestChangedBeforeCompletion() {
    // given
    final var client = Mockito.mock(Storage.class);
    final var manager = new ManifestManager(client, BucketInfo.of("bucket"), "basePath");
    final var backup =
        new BackupImpl(
            new BackupIdentifierImpl(1, 2, 3),
            new BackupDescriptorImpl(Optional.empty(), 1, 1, "version"),
            new NamedFileSetImpl(Map.of()),
            new NamedFileSetImpl(Map.of()));

    final var blob = Mockito.mock(Blob.class);
    Mockito.when(blob.getGeneration()).thenReturn(1L);

    Mockito.when(
            client.create(Mockito.any(BlobInfo.class), Mockito.any(byte[].class), Mockito.any()))
        .thenReturn(blob);

    final var persisted = manager.createInitialManifest(backup);

    // when
    Mockito.when(
            client.create(Mockito.any(BlobInfo.class), Mockito.any(byte[].class), Mockito.any()))
        .thenThrow(new StorageException(412, "expected"));

    // then
    Assertions.assertThatThrownBy(() -> manager.completeManifest(persisted))
        .isInstanceOf(UnexpectedManifestState.class)
        .hasMessageContaining("modification was detected");
  }

  @Test
  void shouldThrowWhenCompletingManifestThrowsUnexpectedStorageException() {
    // given
    final var client = Mockito.mock(Storage.class);
    final var manager = new ManifestManager(client, BucketInfo.of("bucket"), "basePath");
    final var backup =
        new BackupImpl(
            new BackupIdentifierImpl(1, 2, 3),
            new BackupDescriptorImpl(Optional.empty(), 1, 1, "version"),
            new NamedFileSetImpl(Map.of()),
            new NamedFileSetImpl(Map.of()));

    final var blob = Mockito.mock(Blob.class);
    Mockito.when(blob.getGeneration()).thenReturn(1L);

    Mockito.when(
            client.create(Mockito.any(BlobInfo.class), Mockito.any(byte[].class), Mockito.any()))
        .thenReturn(blob);

    final var persisted = manager.createInitialManifest(backup);

    // when
    Mockito.when(
            client.create(Mockito.any(BlobInfo.class), Mockito.any(byte[].class), Mockito.any()))
        .thenThrow(new StorageException(500, "expected but unhandled"));

    // then
    Assertions.assertThatThrownBy(() -> manager.completeManifest(persisted))
        .isInstanceOf(StorageException.class)
        .hasMessageContaining("expected but unhandled");
  }
}
