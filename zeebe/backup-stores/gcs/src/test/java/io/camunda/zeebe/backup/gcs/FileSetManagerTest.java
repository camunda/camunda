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
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

final class FileSetManagerTest {
  @Test
  void shouldSaveFileSet() throws IOException {
    // given
    final var mockClient = mock(Storage.class);
    final var manager = new FileSetManager(mockClient, BucketInfo.of("bucket"), "basePath");
    final var backupIdentifier = new BackupIdentifierImpl(1, 2, 3);
    final var namedFileSet =
        new NamedFileSetImpl(
            Map.of("snapshotFile1", Path.of("file1"), "snapshotFile2", Path.of("file2")));

    // when
    manager.save(backupIdentifier, "filesetName", namedFileSet);

    // then
    verify(mockClient).createFrom(any(), eq(Path.of("file1")), any());
    verify(mockClient).createFrom(any(), eq(Path.of("file2")), any());
  }

  @Test
  void shouldThrowExceptionOnSaveFileSet() throws IOException {
    // given
    final var mockClient = mock(Storage.class);
    final var manager = new FileSetManager(mockClient, BucketInfo.of("bucket"), "basePath");
    final var backupIdentifier = new BackupIdentifierImpl(1, 2, 3);
    final var namedFileSet =
        new NamedFileSetImpl(
            Map.of("snapshotFile1", Path.of("file1"), "snapshotFile2", Path.of("file2")));
    when(mockClient.createFrom(any(), any(Path.class), any()))
        .thenThrow(new StorageException(412, "expected"));

    // when throw
    Assertions.assertThatThrownBy(() -> manager.save(backupIdentifier, "filesetName", namedFileSet))
        .isInstanceOf(StorageException.class)
        .hasMessageContaining("expected");
  }

  @SuppressWarnings("unchecked")
  @Test
  void shouldDeleteFileSet() {
    // given
    final var mockClient = mock(Storage.class);
    final var manager = new FileSetManager(mockClient, BucketInfo.of("bucket"), "basePath");
    final var backupIdentifier = new BackupIdentifierImpl(1, 2, 3);

    final var mockBlob = mock(Blob.class);
    final var mockPage = mock(Page.class);
    when(mockPage.iterateAll()).thenReturn(List.of(mockBlob));
    when(mockClient.list(eq("bucket"), any())).thenReturn(mockPage);

    // when
    manager.delete(backupIdentifier, "filesetName");

    // then
    verify(mockBlob).delete();
  }

  @Test
  void shouldThrowExceptionOnDeleteFileSetWhenListThrows() {
    // given
    final var mockClient = mock(Storage.class);
    final var manager = new FileSetManager(mockClient, BucketInfo.of("bucket"), "basePath");
    final var backupIdentifier = new BackupIdentifierImpl(1, 2, 3);
    when(mockClient.list(eq("bucket"), any())).thenThrow(new StorageException(412, "expected"));

    // when throw
    Assertions.assertThatThrownBy(() -> manager.delete(backupIdentifier, "filesetName"))
        .isInstanceOf(StorageException.class)
        .hasMessageContaining("expected");
  }

  @SuppressWarnings("unchecked")
  @Test
  void shouldThrowExceptionOnDeleteFileSetWhenBlobDeleteThrows() {
    // given
    final var mockClient = mock(Storage.class);
    final var manager = new FileSetManager(mockClient, BucketInfo.of("bucket"), "basePath");
    final var backupIdentifier = new BackupIdentifierImpl(1, 2, 3);

    final Blob mockBlob = mock(Blob.class);
    when(mockBlob.delete()).thenThrow(new StorageException(412, "expected"));
    final var mockPage = mock(Page.class);
    when(mockPage.iterateAll()).thenReturn(List.of(mockBlob));
    when(mockClient.list(eq("bucket"), any())).thenReturn(mockPage);

    // when throw
    Assertions.assertThatThrownBy(() -> manager.delete(backupIdentifier, "filesetName"))
        .isInstanceOf(StorageException.class)
        .hasMessageContaining("expected");
  }

  @Test
  void shouldRestoreFileSet() {
    // given
    final var mockClient = mock(Storage.class);
    final var manager = new FileSetManager(mockClient, BucketInfo.of("bucket"), "basePath");
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

    verify(mockClient).downloadTo(any(), eq(expectedPath1));
    verify(mockClient).downloadTo(any(), eq(expectedPath2));
  }

  @Test
  void shouldThrowRestoreFileSetWhenDownloadToFails() {
    // given
    final var mockClient = mock(Storage.class);
    final var manager = new FileSetManager(mockClient, BucketInfo.of("bucket"), "basePath");
    final var backupIdentifier = new BackupIdentifierImpl(1, 2, 3);
    final var fileSet =
        new FileSet(List.of(new NamedFile("snapshotFile"), new NamedFile("snapshotFile2")));
    final Path restorePath = Path.of("restorePath");
    doThrow(new StorageException(412, "expected"))
        .when(mockClient)
        .downloadTo(any(), any(Path.class));

    // when - then throw
    assertThatThrownBy(() -> manager.restore(backupIdentifier, "filesetName", fileSet, restorePath))
        .isInstanceOf(StorageException.class)
        .hasMessageContaining("expected");
  }
}
