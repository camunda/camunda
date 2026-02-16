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
            50,
            -1);
  }

  @Test
  void shouldSaveSnapshotFilesInParallel(@TempDir final Path tempDir) throws IOException {
    // given - large files that should be uploaded individually
    final var backupIdentifier = new BackupIdentifierImpl(1, 2, 3);
    final var file1 = tempDir.resolve("file1");
    final var file2 = tempDir.resolve("file2");
    // Create files larger than 1 MiB to ensure individual uploads
    final var largeContent = new byte[2 * 1024 * 1024]; // 2 MiB
    Files.write(file1, largeContent);
    Files.write(file2, largeContent);
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
    // given - large files that should be uploaded individually
    final var backupIdentifier = new BackupIdentifierImpl(1, 2, 3);
    final var file1 = tempDir.resolve("file1");
    final var file2 = tempDir.resolve("file2");
    // Create files larger than 1 MiB to ensure individual uploads
    final var largeContent = new byte[2 * 1024 * 1024]; // 2 MiB
    Files.write(file1, largeContent);
    Files.write(file2, largeContent);
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

  @Test
  void shouldBatchSmallFilesIntoZip(@TempDir final Path tempDir) throws IOException {
    // given - multiple small files (< 1 MiB)
    final var backupIdentifier = new BackupIdentifierImpl(1, 2, 3);
    final var file1 = Files.createFile(tempDir.resolve("small1"));
    final var file2 = Files.createFile(tempDir.resolve("small2"));
    final var file3 = Files.createFile(tempDir.resolve("small3"));
    Files.writeString(file1, "content1");
    Files.writeString(file2, "content2");
    Files.writeString(file3, "content3");
    final var namedFileSet =
        new NamedFileSetImpl(Map.of("small1", file1, "small2", file2, "small3", file3));

    // when
    manager.save(backupIdentifier, FileSetManager.SNAPSHOT_FILESET_NAME, namedFileSet);

    // then - only one upload for the batch zip, not three individual uploads
    verify(storage, times(1)).createFrom(any(), any(InputStream.class), any());
  }

  @Test
  void shouldUploadLargeFilesIndividually(@TempDir final Path tempDir) throws IOException {
    // given - files larger than 1 MiB
    final var backupIdentifier = new BackupIdentifierImpl(1, 2, 3);
    final var largeFile1 = tempDir.resolve("large1");
    final var largeFile2 = tempDir.resolve("large2");

    // Create files larger than 1 MiB (1024 * 1024 bytes)
    final var largeContent = new byte[2 * 1024 * 1024]; // 2 MiB
    Files.write(largeFile1, largeContent);
    Files.write(largeFile2, largeContent);

    final var namedFileSet =
        new NamedFileSetImpl(Map.of("large1", largeFile1, "large2", largeFile2));

    // when
    manager.save(backupIdentifier, FileSetManager.SNAPSHOT_FILESET_NAME, namedFileSet);

    // then - two separate uploads, one for each large file
    verify(storage, times(2)).createFrom(any(), any(InputStream.class), any());
  }

  @Test
  void shouldHandleMixedSmallAndLargeFiles(@TempDir final Path tempDir) throws IOException {
    // given - mix of small and large files
    final var backupIdentifier = new BackupIdentifierImpl(1, 2, 3);
    final var smallFile1 = Files.createFile(tempDir.resolve("small1"));
    final var smallFile2 = Files.createFile(tempDir.resolve("small2"));
    Files.writeString(smallFile1, "small content 1");
    Files.writeString(smallFile2, "small content 2");

    final var largeFile = tempDir.resolve("large");
    final var largeContent = new byte[2 * 1024 * 1024]; // 2 MiB
    Files.write(largeFile, largeContent);

    final var namedFileSet =
        new NamedFileSetImpl(
            Map.of("small1", smallFile1, "small2", smallFile2, "large", largeFile));

    // when
    manager.save(backupIdentifier, FileSetManager.SNAPSHOT_FILESET_NAME, namedFileSet);

    // then - two uploads: one for batch zip (small files) and one for large file
    verify(storage, times(2)).createFrom(any(), any(InputStream.class), any());
  }

  @Test
  void shouldRestoreFromBatchZip(@TempDir final Path tempDir) throws IOException {
    // given - a backup with batch zip
    final var backupIdentifier = new BackupIdentifierImpl(1, 2, 3);
    final var fileSet = new FileSet(List.of(new NamedFile("small1"), new NamedFile("small2")));
    final var restorePath = tempDir.resolve("restore");
    Files.createDirectories(restorePath);

    // Mock the batch zip blob exists
    final Blob mockBlob = mock(Blob.class);
    when(storage.get(any(com.google.cloud.storage.BlobId.class))).thenReturn(mockBlob);

    // Create a mock zip file that will be downloaded
    doAnswer(
            invocation -> {
              final var targetPath = (Path) invocation.getArgument(1);
              // Create a simple zip with the expected files
              try (final var zipOut =
                  new java.util.zip.ZipOutputStream(Files.newOutputStream(targetPath))) {
                zipOut.putNextEntry(new java.util.zip.ZipEntry("small1"));
                zipOut.write("content1".getBytes());
                zipOut.closeEntry();
                zipOut.putNextEntry(new java.util.zip.ZipEntry("small2"));
                zipOut.write("content2".getBytes());
                zipOut.closeEntry();
              }
              return null;
            })
        .when(storage)
        .downloadTo(any(), any(Path.class));

    // when
    final var result = manager.restore(backupIdentifier, "filesetName", fileSet, restorePath);

    // then - batch zip was downloaded once, not individual files
    verify(storage, times(1)).downloadTo(any(), any(Path.class));
    Assertions.assertThat(result.names()).containsExactlyInAnyOrder("small1", "small2");
  }
}
