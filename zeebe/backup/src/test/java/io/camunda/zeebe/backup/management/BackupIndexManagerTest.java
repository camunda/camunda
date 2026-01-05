/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.backup.api.BackupIdentifierWildcard;
import io.camunda.zeebe.backup.api.BackupIndex.IndexedBackup;
import io.camunda.zeebe.backup.api.BackupIndexHandle;
import io.camunda.zeebe.backup.api.BackupIndexIdentifier;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupIndexIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupStatusImpl;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BackupIndexManagerTest {

  @TempDir Path tempDir;
  @Mock BackupStore backupStore;

  private BackupIndexManager indexManager;
  private BackupIndexIdentifier indexId;

  @BeforeEach
  void setup() {
    indexId = new BackupIndexIdentifierImpl(1, 1);
    final var indexPath = tempDir.resolve("backup-index.bin");
    lenient()
        .when(backupStore.storeIndex(any()))
        .thenAnswer(invocation -> CompletableFuture.completedFuture(invocation.getArgument(0)));
    lenient()
        .when(backupStore.restoreIndex(any(), any()))
        .thenAnswer(
            invocation ->
                CompletableFuture.completedFuture(
                    new TestBackupIndexHandle(indexId, invocation.getArgument(1))));

    indexManager = new BackupIndexManager(backupStore, indexPath, indexId);
  }

  @AfterEach
  void cleanup() {
    if (indexManager != null) {
      indexManager.close();
    }
  }

  @Test
  void shouldAddBackupToIndex() {
    // given
    final var backup = createInProgressBackup(1L, 100L);

    // when
    indexManager.add(backup).join();

    // then
    final var backups = indexManager.indexedBackups().join().toList();
    assertThat(backups).hasSize(1);
    assertThat(backups.getFirst().checkpointId()).isEqualTo(1L);
    assertThat(backups.getFirst().lastLogPosition()).isEqualTo(100L);
  }

  @Test
  void shouldAddMultipleBackupsToIndex() {
    // given
    final var backup1 = createInProgressBackup(1L, 100L);
    final var backup2 = createInProgressBackup(2L, 200L);
    final var backup3 = createInProgressBackup(3L, 300L);

    // when
    indexManager.add(backup1).join();
    indexManager.add(backup2).join();
    indexManager.add(backup3).join();

    // then
    final var backups = indexManager.indexedBackups().join().toList();
    assertThat(backups).hasSize(3);
    assertThat(backups).extracting(IndexedBackup::checkpointId).containsExactly(1L, 2L, 3L);
  }

  @Test
  void shouldRemoveBackupFromIndex() {
    // given
    final var backup1 = createInProgressBackup(1L, 100L);
    final var backup2 = createInProgressBackup(2L, 200L);
    indexManager.add(backup1).join();
    indexManager.add(backup2).join();

    // when
    final var backupId = new BackupIdentifierImpl(1, 1, 1L);
    indexManager.remove(backupId).join();

    // then
    final var backups = indexManager.indexedBackups().join().toList();
    assertThat(backups).hasSize(1);
    assertThat(backups.getFirst().checkpointId()).isEqualTo(2L);
  }

  @Test
  void shouldRebuildIndexOnCorruption() {
    // given
    when(backupStore.restoreIndex(eq(indexId), any(Path.class)))
        .thenAnswer(
            invocation -> {
              final var path = (Path) invocation.getArgument(1);
              // Create a corrupted file
              Files.writeString(path, "corrupted data");
              return CompletableFuture.completedFuture(new TestBackupIndexHandle(indexId, path));
            });

    final var existingBackup1 = createBackupStatus(1L, 100L);
    final var existingBackup2 = createBackupStatus(2L, 200L);
    when(backupStore.list(any(BackupIdentifierWildcard.class)))
        .thenReturn(CompletableFuture.completedFuture(List.of(existingBackup1, existingBackup2)));

    // when
    final var backups = indexManager.indexedBackups().join().toList();

    // then
    assertThat(backups).hasSize(2);
    assertThat(backups).extracting(IndexedBackup::checkpointId).containsExactly(1L, 2L);
    verify(backupStore).list(any(BackupIdentifierWildcard.class));
    verify(backupStore).storeIndex(any());
  }

  @Test
  void shouldHandleConcurrentModificationWithRetry() {
    // given
    final var backup = createInProgressBackup(1L, 100L);

    // Simulate concurrent modification once, then succeed
    when(backupStore.storeIndex(any()))
        .thenThrow(new ConcurrentModificationException("Concurrent modification"))
        .thenAnswer(invocation -> CompletableFuture.completedFuture(invocation.getArgument(0)));

    // when
    indexManager.add(backup).join();

    // then - should have retried and succeeded
    verify(backupStore, times(2)).storeIndex(any());
    verify(backupStore, times(2)).restoreIndex(eq(indexId), any(Path.class));
  }

  @Test
  void shouldFailAfterMaxRetriesOnConcurrentModification() {
    // given
    final var backup = createInProgressBackup(1L, 100L);

    // Simulate persistent concurrent modification
    when(backupStore.storeIndex(any()))
        .thenThrow(new ConcurrentModificationException("Persistent concurrent modification"));

    // when
    indexManager.add(backup).join();

    // then - should have tried 10 times
    verify(backupStore, times(10)).storeIndex(any());
  }

  @Test
  void shouldStoreIndexAfterAdd() {
    // given
    final var backup = createInProgressBackup(1L, 100L);

    // when
    indexManager.add(backup).join();

    // then
    verify(backupStore).storeIndex(any());
  }

  @Test
  void shouldStoreIndexAfterRemove() {
    // given
    final var backupId = new BackupIdentifierImpl(1, 1, 1L);

    // when
    indexManager.remove(backupId).join();

    // then
    verify(backupStore, times(1)).storeIndex(any());
  }

  @Test
  void shouldNotStoreIndexWhenOnlyQuerying() {
    // when
    indexManager.indexedBackups().join();

    // then
    verify(backupStore, never()).storeIndex(any());
  }

  @Test
  void shouldRethrowStoreExceptionDuringRebuild() {
    // given
    when(backupStore.restoreIndex(eq(indexId), any(Path.class)))
        .thenAnswer(
            invocation -> {
              final Path path = invocation.getArgument(1);
              Files.writeString(path, "corrupted");
              return CompletableFuture.completedFuture(new TestBackupIndexHandle(indexId, path));
            });

    when(backupStore.list(any(BackupIdentifierWildcard.class)))
        .thenReturn(CompletableFuture.completedFuture(List.of()));

    final var storeException = new RuntimeException("Failed to store index");
    when(backupStore.storeIndex(any())).thenReturn(CompletableFuture.failedFuture(storeException));

    // when/then
    assertThatThrownBy(() -> indexManager.indexedBackups().join()).hasCause(storeException);
  }

  @Test
  void shouldHandleEmptyIndexCorrectly() {
    // when
    final var backups = indexManager.indexedBackups().join();

    // then
    assertThat(backups).isEmpty();
  }

  @Test
  void shouldPreserveIndexAcrossMultipleOperations() {
    // given
    final var backup1 = createInProgressBackup(1L, 100L);
    final var backup2 = createInProgressBackup(2L, 200L);
    final var backup3 = createInProgressBackup(3L, 300L);

    // when
    indexManager.add(backup1).join();
    indexManager.add(backup2).join();
    indexManager.add(backup3).join();

    final var backupId = new BackupIdentifierImpl(1, 1, 2L);
    indexManager.remove(backupId).join();

    // then
    final var backups = indexManager.indexedBackups().join().toList();
    assertThat(backups).hasSize(2);
    assertThat(backups).extracting(IndexedBackup::checkpointId).containsExactly(1L, 3L);
  }

  // Helper methods

  private InProgressBackup createInProgressBackup(final long checkpointId, final long position) {
    final var backupId = new BackupIdentifierImpl(1, 1, checkpointId);
    final var descriptor =
        new BackupDescriptorImpl(
            Optional.empty(), position, 1, "", Instant.now(), CheckpointType.MANUAL_BACKUP);

    return new InProgressBackupImpl(null, backupId, descriptor, null, null, null);
  }

  private BackupStatus createBackupStatus(final long checkpointId, final long position) {
    final var backupId = new BackupIdentifierImpl(1, 1, checkpointId);
    final var descriptor =
        new BackupDescriptorImpl(
            Optional.empty(), position, 1, "", Instant.now(), CheckpointType.MANUAL_BACKUP);
    return new BackupStatusImpl(
        backupId,
        Optional.of(descriptor),
        BackupStatusCode.COMPLETED,
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }

  private record TestBackupIndexHandle(BackupIndexIdentifier id, Path path)
      implements BackupIndexHandle {}
}
