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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.azure.core.http.HttpPipeline;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceVersion;
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
import java.util.concurrent.ExecutionException;
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
    when(blobServiceClient.getHttpPipeline()).thenReturn(mock(HttpPipeline.class));
    when(blobServiceClient.getAccountUrl()).thenReturn("https://test.blob.core.windows.net");
    when(blobServiceClient.getServiceVersion()).thenReturn(BlobServiceVersion.V2020_10_02);
    final var realStore = new AzureBackupStore(config, blobServiceClient);

    /* Inject mocks */
    final Field manifestManagerField = AzureBackupStore.class.getDeclaredField("manifestManager");
    manifestManagerField.setAccessible(true);
    manifestManagerField.set(realStore, manifestManager);

    final Field fileSetManagerField = AzureBackupStore.class.getDeclaredField("fileSetManager");
    fileSetManagerField.setAccessible(true);
    fileSetManagerField.set(realStore, fileSetManager);

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
  void shouldUploadFileSetsAndCompleteManifestOnSuccess() {
    // given
    when(manifestManager.createInitialManifest(any())).thenReturn(persistedManifest);

    // when
    store.save(backup).join();

    // then
    verify(fileSetManager)
        .save(backup.id(), AzureBackupStore.SNAPSHOT_FILESET_NAME, backup.snapshot());
    verify(fileSetManager)
        .save(backup.id(), AzureBackupStore.SEGMENTS_FILESET_NAME, backup.segments());
    verify(manifestManager).completeManifest(persistedManifest);
  }

  @Test
  void shouldCompleteExceptionallyAndMarkAsFailedWhenUploadFails() {
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
  void shouldCompleteExceptionallyWhenCreateInitialManifestFails() {
    // given
    Mockito.when(manifestManager.createInitialManifest(any()))
        .thenThrow(new RuntimeException("manifest creation failed"));

    // when / then
    assertThat(store.save(backup))
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(RuntimeException.class);
  }
}
