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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
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
    manager = new FileSetManager(storage, BucketInfo.of("bucket"), "basePath");
  }

  // Note: Save tests that verify TransferManager behavior are covered by integration tests
  // (GcsBackupStoreIT) since TransferManager creates its own internal service and cannot
  // be easily mocked.

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
  void shouldRestoreFileSetFromArchive(@TempDir final Path tempDir) throws IOException {
    // given
    final var backupIdentifier = new BackupIdentifierImpl(1, 2, 3);
    final var fileSet =
        new FileSet(List.of(new NamedFile("snapshotFile"), new NamedFile("snapshotFile2")));
    final Path restorePath = tempDir.resolve("restore");
    Files.createDirectories(restorePath);

    // Create a mock archive that will be "downloaded"
    doAnswer(
            invocation -> {
              final Path targetPath = invocation.getArgument(1);
              createTestArchive(
                  targetPath,
                  Map.of(
                      "snapshotFile", "content1",
                      "snapshotFile2", "content2"));
              return null;
            })
        .when(storage)
        .downloadTo(any(), any(Path.class));

    // when
    final var namedFileSet = manager.restore(backupIdentifier, "filesetName", fileSet, restorePath);

    // then
    Assertions.assertThat(namedFileSet.namedFiles()).hasSize(2);
    Assertions.assertThat(namedFileSet.namedFiles().get("snapshotFile"))
        .isEqualTo(restorePath.resolve("snapshotFile"));
    Assertions.assertThat(namedFileSet.namedFiles().get("snapshotFile2"))
        .isEqualTo(restorePath.resolve("snapshotFile2"));
    Assertions.assertThat(Files.readString(restorePath.resolve("snapshotFile")))
        .isEqualTo("content1");
    Assertions.assertThat(Files.readString(restorePath.resolve("snapshotFile2")))
        .isEqualTo("content2");

    // Verify single archive download
    verify(storage, times(1)).downloadTo(any(), any(Path.class));
  }

  @Test
  void shouldReturnEmptyFileSetWhenNoFilesToRestore(@TempDir final Path tempDir) {
    // given
    final var backupIdentifier = new BackupIdentifierImpl(1, 2, 3);
    final var fileSet = new FileSet(List.of());

    // when
    final var namedFileSet = manager.restore(backupIdentifier, "filesetName", fileSet, tempDir);

    // then
    Assertions.assertThat(namedFileSet.namedFiles()).isEmpty();
    verifyNoInteractions(storage);
  }

  @Test
  void shouldNotSaveWhenFileSetIsEmpty() {
    // given
    final var backupIdentifier = new BackupIdentifierImpl(1, 2, 3);
    final var namedFileSet = new NamedFileSetImpl(Map.of());

    // when
    manager.save(backupIdentifier, FileSetManager.SNAPSHOT_FILESET_NAME, namedFileSet);

    // then - no upload should happen
    verifyNoInteractions(storage);
  }

  @Test
  void shouldThrowRestoreFileSetWhenDownloadToFails(@TempDir final Path tempDir) {
    // given
    final var backupIdentifier = new BackupIdentifierImpl(1, 2, 3);
    final var fileSet =
        new FileSet(List.of(new NamedFile("snapshotFile"), new NamedFile("snapshotFile2")));
    final Path restorePath = tempDir.resolve("restore");
    doThrow(new StorageException(412, "expected")).when(storage).downloadTo(any(), any(Path.class));

    // when - then throw
    assertThatThrownBy(() -> manager.restore(backupIdentifier, "filesetName", fileSet, restorePath))
        .hasCauseInstanceOf(StorageException.class)
        .hasMessageContaining("expected");
  }

  private void createTestArchive(final Path archivePath, final Map<String, String> files)
      throws IOException {
    try (final var fileOut = Files.newOutputStream(archivePath);
        final var gzipOut = new GZIPOutputStream(fileOut);
        final var zipOut = new ZipOutputStream(gzipOut)) {
      for (final var entry : files.entrySet()) {
        zipOut.putNextEntry(new ZipEntry(entry.getKey()));
        zipOut.write(entry.getValue().getBytes());
        zipOut.closeEntry();
      }
    }
  }
}
