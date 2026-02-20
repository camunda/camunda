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
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.FileSet;
import io.camunda.zeebe.backup.common.FileSet.NamedFile;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
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
    // Mock StorageOptions to allow TransferManager to be created
    final var opts = spy(StorageOptions.newBuilder().build());
    doReturn(opts).when(storage).getOptions();
    doReturn("agent").when(opts).getUserAgent();
    manager = new FileSetManager(storage, BucketInfo.of("bucket"), "basePath/");
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

  @SuppressWarnings("unchecked")
  @Test
  void shouldRestoreFileSetFromTarGzArchive(@TempDir final Path tempDir) throws IOException {
    // given
    final var backupIdentifier = new BackupIdentifierImpl(1, 2, 3);
    final var fileSet =
        new FileSet(List.of(new NamedFile("snapshotFile"), new NamedFile("snapshotFile2")));
    final Path restorePath = tempDir.resolve("restore");
    Files.createDirectories(restorePath);

    // Mock the list call to return a tar.gz archive
    final var mockBlob = mock(Blob.class);
    when(mockBlob.getName()).thenReturn("basePath/contents/2/3/1/filesetName/filesetName-0.tar.gz");
    when(mockBlob.getBlobId())
        .thenReturn(
            BlobId.of("bucket", "basePath/contents/2/3/1/filesetName/filesetName-0.tar.gz"));

    final var mockPage = mock(Page.class);
    when(mockPage.iterateAll()).thenReturn(List.of(mockBlob));
    when(storage.list(eq("bucket"), any())).thenReturn(mockPage);

    // Mock download to create a tar.gz archive containing the files
    doAnswer(
            invocation -> {
              final Path targetPath = invocation.getArgument(1);
              createTarGzArchive(
                  targetPath, Map.of("snapshotFile", "content1", "snapshotFile2", "content2"));
              return null;
            })
        .when(storage)
        .downloadTo(any(BlobId.class), any(Path.class));

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
    verify(storage, times(1)).downloadTo(any(BlobId.class), any(Path.class));
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
    // Verify no download operations occurred (only getOptions() in constructor)
    verify(storage, never()).downloadTo(any(BlobId.class), any(Path.class));
    verify(storage, never()).get(any(BlobId.class));
  }

  @Test
  void shouldNotSaveWhenFileSetIsEmpty() {
    // given
    final var backupIdentifier = new BackupIdentifierImpl(1, 2, 3);
    final var namedFileSet = new NamedFileSetImpl(Map.of());

    // when
    manager.save(backupIdentifier, FileSetManager.SNAPSHOT_FILESET_NAME, namedFileSet);

    // then - no upload should happen (only getOptions() in constructor)
    verify(storage, never()).create(any(BlobInfo.class), any(byte[].class));
  }

  @SuppressWarnings("unchecked")
  @Test
  void shouldThrowRestoreFileSetWhenDownloadToFails(@TempDir final Path tempDir) {
    // given
    final var backupIdentifier = new BackupIdentifierImpl(1, 2, 3);
    final var fileSet =
        new FileSet(List.of(new NamedFile("snapshotFile"), new NamedFile("snapshotFile2")));
    final Path restorePath = tempDir.resolve("restore");

    // Mock the list call to return a tar.gz archive
    final var mockBlob = mock(Blob.class);
    when(mockBlob.getName()).thenReturn("basePath/contents/2/3/1/filesetName/filesetName-0.tar.gz");
    when(mockBlob.getBlobId())
        .thenReturn(
            BlobId.of("bucket", "basePath/contents/2/3/1/filesetName/filesetName-0.tar.gz"));

    final var mockPage = mock(Page.class);
    when(mockPage.iterateAll()).thenReturn(List.of(mockBlob));
    when(storage.list(eq("bucket"), any())).thenReturn(mockPage);

    doThrow(new StorageException(412, "expected"))
        .when(storage)
        .downloadTo(any(BlobId.class), any(Path.class));

    // when - then throw
    assertThatThrownBy(() -> manager.restore(backupIdentifier, "filesetName", fileSet, restorePath))
        .isInstanceOf(UncheckedIOException.class)
        .hasCauseInstanceOf(StorageException.class);
  }

  @SuppressWarnings("unchecked")
  @Test
  void shouldRestoreFileSetFromMultipleTarGzArchives(@TempDir final Path tempDir)
      throws IOException {
    // given - multiple archives to trigger parallel restore
    final var backupIdentifier = new BackupIdentifierImpl(1, 2, 3);
    final var fileSet =
        new FileSet(
            List.of(
                new NamedFile("file1"),
                new NamedFile("file2"),
                new NamedFile("file3"),
                new NamedFile("file4")));
    final Path restorePath = tempDir.resolve("restore");
    Files.createDirectories(restorePath);

    // Mock the list call to return multiple tar.gz archives
    final var mockBlob1 = mock(Blob.class);
    when(mockBlob1.getName())
        .thenReturn("basePath/contents/2/3/1/filesetName/filesetName-0.tar.gz");
    when(mockBlob1.getBlobId())
        .thenReturn(
            BlobId.of("bucket", "basePath/contents/2/3/1/filesetName/filesetName-0.tar.gz"));

    final var mockBlob2 = mock(Blob.class);
    when(mockBlob2.getName())
        .thenReturn("basePath/contents/2/3/1/filesetName/filesetName-1.tar.gz");
    when(mockBlob2.getBlobId())
        .thenReturn(
            BlobId.of("bucket", "basePath/contents/2/3/1/filesetName/filesetName-1.tar.gz"));

    final var mockPage = mock(Page.class);
    when(mockPage.iterateAll()).thenReturn(List.of(mockBlob1, mockBlob2));
    when(storage.list(eq("bucket"), any())).thenReturn(mockPage);

    // Mock download to create tar.gz archives
    doAnswer(
            invocation -> {
              final BlobId blobId = invocation.getArgument(0);
              final Path targetPath = invocation.getArgument(1);
              if (blobId.getName().contains("-0.tar.gz")) {
                createTarGzArchive(targetPath, Map.of("file1", "content1", "file2", "content2"));
              } else {
                createTarGzArchive(targetPath, Map.of("file3", "content3", "file4", "content4"));
              }
              return null;
            })
        .when(storage)
        .downloadTo(any(BlobId.class), any(Path.class));

    // when
    final var namedFileSet = manager.restore(backupIdentifier, "filesetName", fileSet, restorePath);

    // then
    Assertions.assertThat(namedFileSet.namedFiles()).hasSize(4);
    Assertions.assertThat(Files.readString(restorePath.resolve("file1"))).isEqualTo("content1");
    Assertions.assertThat(Files.readString(restorePath.resolve("file2"))).isEqualTo("content2");
    Assertions.assertThat(Files.readString(restorePath.resolve("file3"))).isEqualTo("content3");
    Assertions.assertThat(Files.readString(restorePath.resolve("file4"))).isEqualTo("content4");
  }

  private void createTarGzArchive(final Path archivePath, final Map<String, String> files)
      throws IOException {
    try (final var fileOut = Files.newOutputStream(archivePath);
        final var gzipOut = new GZIPOutputStream(fileOut);
        final var tarOut = new TarArchiveOutputStream(gzipOut)) {
      for (final var entry : files.entrySet()) {
        final byte[] content = entry.getValue().getBytes();
        final TarArchiveEntry tarEntry = new TarArchiveEntry(entry.getKey());
        tarEntry.setSize(content.length);
        tarOut.putArchiveEntry(tarEntry);
        tarOut.write(content);
        tarOut.closeArchiveEntry();
      }
      tarOut.finish();
    }
  }
}
