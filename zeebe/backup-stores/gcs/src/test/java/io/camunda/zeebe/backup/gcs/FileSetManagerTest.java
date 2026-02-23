/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.gcs;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.google.api.gax.paging.Page;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.FileSet;
import io.camunda.zeebe.backup.common.FileSet.NamedFile;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class FileSetManagerTest {
  private final Storage storage;
  private final FileSetManager manager;

  FileSetManagerTest(@Mock final Storage storage) {
    this.storage = storage;
    manager =
        new FileSetManager(
            storage,
            BucketInfo.of("bucket"),
            "basePath",
            Executors.newVirtualThreadPerTaskExecutor(),
            50);
  }

  @Test
  void shouldSaveSnapshotFilesInParallel(@TempDir final Path tempDir) throws IOException {
    // given
    final var backupIdentifier = new BackupIdentifierImpl(1, 2, 3);
    final var file1 = Files.createFile(tempDir.resolve("file1"));
    final var file2 = Files.createFile(tempDir.resolve("file2"));
    final var namedFileSet =
        new NamedFileSetImpl(Map.of("snapshotFile1", file1, "snapshotFile2", file2));

    // when
    manager.save(backupIdentifier, FileSetManager.SNAPSHOT_FILESET_NAME, namedFileSet);

    // then - snapshots are uploaded in parallel using the executor
    verify(storage, times(2)).createFrom(any(), any(InputStream.class), any());
  }

  @Test
  void shouldThrowExceptionOnSaveSnapshotWhenUploadFails(@TempDir final Path tempDir)
      throws IOException {
    // given
    final var backupIdentifier = new BackupIdentifierImpl(1, 2, 3);
    final var file1 = Files.createFile(tempDir.resolve("file1"));
    final var file2 = Files.createFile(tempDir.resolve("file2"));
    final var namedFileSet =
        new NamedFileSetImpl(Map.of("snapshotFile1", file1, "snapshotFile2", file2));
    when(storage.createFrom(any(), any(InputStream.class), any()))
        .thenThrow(new StorageException(412, "expected"));

    // when throw
    Assertions.assertThatThrownBy(
            () ->
                manager.save(backupIdentifier, FileSetManager.SNAPSHOT_FILESET_NAME, namedFileSet))
        .hasCauseInstanceOf(StorageException.class)
        .hasMessageContaining("expected");
  }

  @Test
  void shouldSaveSegmentFilesInParallel(@TempDir final Path tempDir) throws IOException {
    // given
    final var backupIdentifier = new BackupIdentifierImpl(1, 2, 3);
    final var file1 = Files.createFile(tempDir.resolve("file1"));
    final var file2 = Files.createFile(tempDir.resolve("file2"));
    final var namedFileSet =
        new NamedFileSetImpl(Map.of("segmentFile1", file1, "segmentFile2", file2));

    // when
    manager.save(backupIdentifier, FileSetManager.SEGMENTS_FILESET_NAME, namedFileSet);

    // then - segments are uploaded in parallel using the executor
    verify(storage, times(2)).createFrom(any(), any(InputStream.class), any());
  }

  @Test
  void shouldThrowExceptionOnSaveSegments(@TempDir final Path tempDir) throws IOException {
    // given
    final var backupIdentifier = new BackupIdentifierImpl(1, 2, 3);
    final var file1 = Files.createFile(tempDir.resolve("file1"));
    final var file2 = Files.createFile(tempDir.resolve("file2"));
    final var namedFileSet =
        new NamedFileSetImpl(Map.of("segmentFile1", file1, "segmentFile2", file2));
    when(storage.createFrom(any(), any(InputStream.class), any()))
        .thenThrow(new StorageException(412, "expected"));

    // when throw
    Assertions.assertThatThrownBy(
            () ->
                manager.save(backupIdentifier, FileSetManager.SEGMENTS_FILESET_NAME, namedFileSet))
        .hasCauseInstanceOf(StorageException.class)
        .hasMessageContaining("expected");
  }

  @SuppressWarnings("unchecked")
  @Test
  void shouldDeleteFileSet() {
    // given
    final var backupIdentifier = new BackupIdentifierImpl(1, 2, 3);

    final var mockBlob = mock(Blob.class);
    final var mockPage = mock(Page.class);
    when(mockPage.iterateAll()).thenReturn(List.of(mockBlob));
    when(storage.list(eq("bucket"), any())).thenReturn(mockPage);

    // when
    manager.delete(backupIdentifier, "filesetName");

    // then
    verify(mockBlob).delete();
  }

  @Test
  void shouldThrowExceptionOnDeleteFileSetWhenListThrows() {
    // given
    final var backupIdentifier = new BackupIdentifierImpl(1, 2, 3);
    when(storage.list(eq("bucket"), any())).thenThrow(new StorageException(412, "expected"));

    // when throw
    Assertions.assertThatThrownBy(() -> manager.delete(backupIdentifier, "filesetName"))
        .isInstanceOf(StorageException.class)
        .hasMessageContaining("expected");
  }

  @SuppressWarnings("unchecked")
  @Test
  void shouldThrowExceptionOnDeleteFileSetWhenBlobDeleteThrows() {
    // given
    final var backupIdentifier = new BackupIdentifierImpl(1, 2, 3);

    final Blob mockBlob = mock(Blob.class);
    when(mockBlob.delete()).thenThrow(new StorageException(412, "expected"));
    final var mockPage = mock(Page.class);
    when(mockPage.iterateAll()).thenReturn(List.of(mockBlob));
    when(storage.list(eq("bucket"), any())).thenReturn(mockPage);

    // when throw
    Assertions.assertThatThrownBy(() -> manager.delete(backupIdentifier, "filesetName"))
        .isInstanceOf(StorageException.class)
        .hasMessageContaining("expected");
  }

  @Test
  void shouldRestoreFileSet() {
    // given
    final var backupIdentifier = new BackupIdentifierImpl(1, 2, 3);
    final var fileSet =
        new FileSet(List.of(new NamedFile("snapshotFile"), new NamedFile("snapshotFile2")));
    final Path restorePath = Path.of("restorePath");

    // when
    final var namedFileSet = manager.restore(backupIdentifier, "filesetName", fileSet, restorePath);

    // then
    final Path expectedPath1 = Path.of("restorePath/snapshotFile");
    final Path expectedPath2 = Path.of("restorePath/snapshotFile2");

    Assertions.assertThat(namedFileSet.namedFiles())
        .isEqualTo(Map.of("snapshotFile", expectedPath1, "snapshotFile2", expectedPath2));

    verify(storage).downloadTo(any(), eq(expectedPath1));
    verify(storage).downloadTo(any(), eq(expectedPath2));
  }

  @Test
  void shouldThrowRestoreFileSetWhenDownloadToFails() {
    // given
    final var backupIdentifier = new BackupIdentifierImpl(1, 2, 3);
    final var fileSet =
        new FileSet(List.of(new NamedFile("snapshotFile"), new NamedFile("snapshotFile2")));
    final Path restorePath = Path.of("restorePath");
    doThrow(new StorageException(412, "expected")).when(storage).downloadTo(any(), any(Path.class));

    // when - then throw
    assertThatThrownBy(() -> manager.restore(backupIdentifier, "filesetName", fileSet, restorePath))
        .hasCauseInstanceOf(StorageException.class)
        .hasMessageContaining("expected");
  }
}
