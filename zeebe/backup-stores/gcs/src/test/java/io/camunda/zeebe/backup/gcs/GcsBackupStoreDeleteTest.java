/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.gcs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.Manifest;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class GcsBackupStoreDeleteTest {

  @Mock private ManifestManager manifestManager;
  @Mock private FileSetManager fileSetManager;

  private GcsBackupStore store;
  private final BackupIdentifierImpl id = new BackupIdentifierImpl(1, 2, 3L);

  @BeforeEach
  void setUp() throws Exception {
    final var config =
        new GcsBackupConfig.Builder().withBucketName("bucket").withoutAuthentication().build();
    store = new GcsBackupStore(config, mock(Storage.class));
    setField(store, "manifestManager", manifestManager);
    setField(store, "fileSetManager", fileSetManager);
  }

  @Test
  void shouldNotDeleteManifestWhenContentsDeletionFails() {
    // given
    final Manifest deletedManifest = Manifest.createFailed(id).delete();
    when(manifestManager.getManifest(id)).thenReturn(deletedManifest);
    when(fileSetManager.collectBlobIds(eq(id), any()))
        .thenReturn(List.of(BlobId.of("bucket", "contents/1/3/2/snapshot/file1")));
    doThrow(new StorageException(500, "simulated contents deletion failure"))
        .when(fileSetManager)
        .deleteBlobs(any());

    // when
    assertThatThrownBy(() -> store.delete(id).join())
        .hasCauseInstanceOf(StorageException.class)
        .hasMessageContaining("simulated contents deletion failure");

    // then - manifest was never deleted
    verify(manifestManager, never()).deleteManifest(any());
  }

  @Test
  void shouldBoundConcurrentDeletions() throws Exception {
    // given - twice as many deletions as may run at once, each blocked until all are submitted
    final var deletedManifest = Manifest.createFailed(id).delete();
    final var running = new AtomicInteger();
    final var maxRunning = new AtomicInteger();
    final var permittedStarted = new CountDownLatch(GcsBackupStore.DELETE_PARALLELISM);
    final var release = new CountDownLatch(1);
    when(manifestManager.getManifest(any()))
        .thenAnswer(
            invocation -> {
              maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
              permittedStarted.countDown();
              release.await();
              running.decrementAndGet();
              return deletedManifest;
            });
    when(fileSetManager.collectBlobIds(any(), any())).thenReturn(List.of());

    // when
    final var deletions =
        IntStream.range(0, 2 * GcsBackupStore.DELETE_PARALLELISM)
            .mapToObj(checkpointId -> store.delete(new BackupIdentifierImpl(1, 2, checkpointId)))
            .toArray(CompletableFuture[]::new);
    assertThat(permittedStarted.await(10, TimeUnit.SECONDS)).isTrue();
    release.countDown();
    CompletableFuture.allOf(deletions).get(10, TimeUnit.SECONDS);

    // then
    assertThat(maxRunning).hasValue(GcsBackupStore.DELETE_PARALLELISM);
    verify(manifestManager, times(2 * GcsBackupStore.DELETE_PARALLELISM))
        .deleteManifest(deletedManifest);
  }

  private static void setField(final Object target, final String name, final Object value)
      throws Exception {
    final Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }
}
