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
import com.google.cloud.BatchResult.Callback;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageBatch;
import com.google.cloud.storage.StorageBatchResult;
import com.google.cloud.storage.StorageException;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.FileSet;
import io.camunda.zeebe.backup.common.FileSet.NamedFile;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import io.camunda.zeebe.backup.gcs.GcsBackupStoreException.BatchOperationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
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
  private final byte[] fileContent;

  FileSetManagerTest(@Mock final Storage storage) {
    this.storage = storage;
    fileContent = new byte[1024];
    Arrays.fill(fileContent, 0, 1024, (byte) 1);
    manager =
        new FileSetManager(
            storage,
            BucketInfo.of("bucket"),
            "basePath",
            Executors.newVirtualThreadPerTaskExecutor(),
            50,
            1024 * 1024);
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
    verify(storage, times(2)).createFrom(any(), any(InputStream.class), anyInt(), any());
  }

  @Test
  void shouldUseFileSizeAsBufferSize(@TempDir final Path tempDir) throws IOException {
    // given
    final var backupIdentifier = new BackupIdentifierImpl(1, 2, 3);
    final var file1 = Files.createFile(tempDir.resolve("file1"));
    final var file2 = Files.createFile(tempDir.resolve("file2"));
    final var namedFileSet =
        new NamedFileSetImpl(Map.of("snapshotFile1", file1, "snapshotFile2", file2));
    Files.write(file1, fileContent);
    Files.write(file2, fileContent);

    // when
    manager.save(backupIdentifier, FileSetManager.SNAPSHOT_FILESET_NAME, namedFileSet);

    // then - the file size is used as the upload buffer size
    verify(storage, times(2)).createFrom(any(), any(InputStream.class), eq(1024), any());
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
    when(storage.createFrom(any(), any(InputStream.class), anyInt(), any()))
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
    verify(storage, times(2)).createFrom(any(), any(InputStream.class), anyInt(), any());
  }

  @Test
  void shouldThrowExceptionOnSaveSegments(@TempDir final Path tempDir) throws IOException {
    // given
    final var backupIdentifier = new BackupIdentifierImpl(1, 2, 3);
    final var file1 = Files.createFile(tempDir.resolve("file1"));
    final var file2 = Files.createFile(tempDir.resolve("file2"));
    final var namedFileSet =
        new NamedFileSetImpl(Map.of("segmentFile1", file1, "segmentFile2", file2));
    when(storage.createFrom(any(), any(InputStream.class), anyInt(), any()))
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
  void shouldCollectAndDeleteFileSetBlobs() {
    // given
    final var backupIdentifier = new BackupIdentifierImpl(1, 2, 3);
    final var blobId = BlobId.of("bucket", "basePath/contents/1/2/3/filesetName/file");
    final var mockBlob = mock(Blob.class);
    when(mockBlob.getBlobId()).thenReturn(blobId);
    final var mockPage = mock(Page.class);
    when(mockPage.iterateAll()).thenReturn(List.of(mockBlob));
    when(storage.list(eq("bucket"), any())).thenReturn(mockPage);
    final var mockBatch = mock(StorageBatch.class);
    when(storage.batch()).thenReturn(mockBatch);
    when(mockBatch.delete(any(BlobId.class))).thenReturn(mock(StorageBatchResult.class));

    // when
    final var collected = manager.collectBlobIds(backupIdentifier, "filesetName");
    manager.deleteBlobs(new ArrayList<>(collected));

    // then
    verify(storage).batch();
    verify(mockBatch).delete(blobId);
    verify(mockBatch).submit();
  }

  @Test
  void shouldThrowExceptionOnCollectWhenListThrows() {
    // given
    final var backupIdentifier = new BackupIdentifierImpl(1, 2, 3);
    when(storage.list(eq("bucket"), any())).thenThrow(new StorageException(412, "expected"));

    // when/then
    Assertions.assertThatThrownBy(() -> manager.collectBlobIds(backupIdentifier, "filesetName"))
        .isInstanceOf(StorageException.class)
        .hasMessageContaining("expected");
  }

  @SuppressWarnings("unchecked")
  @Test
  void shouldThrowExceptionWhenBatchDeleteThrows() {
    // given
    final var blobId = BlobId.of("bucket", "basePath/contents/1/2/3/filesetName/file");
    final var mockBatch = mock(StorageBatch.class);
    when(storage.batch()).thenReturn(mockBatch);
    final var mockResult = mock(StorageBatchResult.class);
    when(mockBatch.delete(any(BlobId.class))).thenReturn(mockResult);
    doAnswer(
            inv -> {
              final Callback<Boolean, StorageException> cb = inv.getArgument(0);
              cb.error(new StorageException(412, "expected"));
              return null;
            })
        .when(mockResult)
        .notify(any());

    // when/then
    Assertions.assertThatThrownBy(() -> manager.deleteBlobs(List.of(blobId)))
        .isInstanceOf(BatchOperationException.class)
        .hasMessageContaining("Failures detected in the blob batch deletion");
  }

  @SuppressWarnings("unchecked")
  @Test
  void shouldSplitDeleteAcrossMultipleBatches() {
    // given - 101 blobs exceeds MAX_DELETE_BATCH_SIZE (100)
    final var blobIds =
        IntStream.range(0, 101)
            .mapToObj(i -> BlobId.of("bucket", "contents/1/2/3/file" + i))
            .toList();
    final var mockBatch = mock(StorageBatch.class);
    when(storage.batch()).thenReturn(mockBatch);
    when(mockBatch.delete(any(BlobId.class))).thenReturn(mock(StorageBatchResult.class));

    // when
    manager.deleteBlobs(blobIds);

    // then - two separate batch calls: first 100, then 1
    verify(storage, times(2)).batch();
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
