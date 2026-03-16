/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.azure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.azure.ManifestManager.PersistedManifest;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupImpl;
import io.camunda.zeebe.backup.common.Manifest;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class AzureBackupStoreSaveTest {

  @Mock private FileSetManager fileSetManager;
  @Mock private ManifestManager manifestManager;
  @Mock private BlobServiceClient blobServiceClient;

  private AzureBackupStore store;
  private Backup backup;
  private PersistedManifest persistedManifest;
  private Semaphore saveSemaphore;

  @BeforeEach
  void setUp() throws Exception {
    final AzureBackupConfig config =
        new AzureBackupConfig.Builder()
            .withCreateContainer(false)
            .withContainerName("test")
            .build();
    final var containerClient = mock(BlobContainerClient.class);
    when(blobServiceClient.getBlobContainerClient(anyString())).thenReturn(containerClient);
    when(containerClient.exists()).thenReturn(true);
    final var realStore = new AzureBackupStore(config, blobServiceClient);

    /* Inject mocks */
    final Field manifestManagerField = AzureBackupStore.class.getDeclaredField("manifestManager");
    manifestManagerField.setAccessible(true);
    manifestManagerField.set(realStore, manifestManager);

    final Field fileSetManagerField = AzureBackupStore.class.getDeclaredField("fileSetManager");
    fileSetManagerField.setAccessible(true);
    fileSetManagerField.set(realStore, fileSetManager);

    final Field semaphoreField = AzureBackupStore.class.getDeclaredField("saveSemaphore");
    semaphoreField.setAccessible(true);
    saveSemaphore = (Semaphore) semaphoreField.get(realStore);

    store = spy(realStore);

    final var id = new BackupIdentifierImpl(1, 0, 1L);
    final var descriptor =
        new BackupDescriptorImpl(1L, 2, "1.2.0", Instant.now(), CheckpointType.MANUAL_BACKUP);
    backup =
        new BackupImpl(
            id, descriptor, new NamedFileSetImpl(Map.of()), new NamedFileSetImpl(Map.of()));

    final var manifest = Manifest.createInProgress(backup);
    persistedManifest = new PersistedManifest("etag-1", manifest);
  }

  @AfterEach
  void tearDown() {
    store.closeAsync().join();
  }

  @Test
  void shouldCompleteExceptionallyWhenSnapshotUploadFails() {
    // given
    when(manifestManager.createInitialManifest(any())).thenReturn(persistedManifest);
    final var uploadError = new RuntimeException("snapshot upload failed");
    doThrow(uploadError).when(fileSetManager).save(any(), anyString(), any());

    // when
    final var future = store.save(backup);

    // then
    assertThat(future)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(RuntimeException.class);
    // whenCompleteAsync receives a CompletionException wrapping the original error
    final var expectedFailureMessage = new CompletionException(uploadError).getMessage();
    verify(manifestManager).markAsFailed(backup.id(), expectedFailureMessage);
  }

  @Test
  void shouldAcquireAndReleaseTheSemaphoreOnSuccess() {
    // given
    when(manifestManager.createInitialManifest(any())).thenReturn(persistedManifest);
    final int permitsBefore = saveSemaphore.availablePermits();

    // when
    store.save(backup).join();

    // then
    assertThat(saveSemaphore.availablePermits())
        .as("semaphore permits should be fully restored after a successful save")
        .isEqualTo(permitsBefore);
  }

  @Test
  void shouldAcquireAndReleaseTheSemaphoreWhenUploadFails() {
    // given

    lenient()
        .doThrow(new RuntimeException("upload failed"))
        .when(fileSetManager)
        .save(any(), anyString(), any());
    final int permitsBefore = saveSemaphore.availablePermits();

    // when
    store.save(backup).exceptionally(e -> null).join();

    // then
    assertThat(saveSemaphore.availablePermits())
        .as("semaphore permits should be fully restored after a failed upload")
        .isEqualTo(permitsBefore);
  }

  @Test
  void shouldAcquireAndReleaseTheSemaphoreWhenCreateInitialManifestFails() {
    // given
    Mockito.lenient()
        .when(manifestManager.createInitialManifest(any()))
        .thenThrow(new RuntimeException("manifest creation failed"));
    final int permitsBefore = saveSemaphore.availablePermits();

    // when
    store.save(backup).exceptionally(e -> null).join();

    // then
    assertThat(saveSemaphore.availablePermits())
        .as("semaphore permits should be fully restored after a failed manifest creation")
        .isEqualTo(permitsBefore);
  }

  @Test
  void shouldDecrementPermitWhileSaveIsInFlight() throws Exception {
    // given – block the upload so the save is still in progress when we sample the semaphore
    when(manifestManager.createInitialManifest(any())).thenReturn(persistedManifest);
    final var uploadStarted = new CountDownLatch(1);
    final var releaseUpload = new CountDownLatch(1);
    Mockito.doAnswer(
            inv -> {
              uploadStarted.countDown();
              releaseUpload.await();
              return null;
            })
        .when(fileSetManager)
        .save(any(), anyString(), any());

    // when
    final var future = store.save(backup);
    uploadStarted.await();

    // then – one permit must be held while the save is in progress
    assertThat(saveSemaphore.availablePermits())
        .as("one permit should be held while a save is in progress")
        .isEqualTo(AzureBackupStore.MAX_CONCURRENT_SAVES - 1);

    // cleanup – let the upload finish and confirm the permit is restored
    releaseUpload.countDown();
    future.join();

    assertThat(saveSemaphore.availablePermits())
        .as("permit should be restored after the in-flight save completes")
        .isEqualTo(AzureBackupStore.MAX_CONCURRENT_SAVES);
  }
}
