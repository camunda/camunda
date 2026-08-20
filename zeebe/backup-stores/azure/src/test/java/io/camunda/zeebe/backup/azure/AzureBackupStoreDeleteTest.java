/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.azure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.azure.core.http.HttpPipeline;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceVersion;
import com.azure.storage.blob.models.BlobStorageException;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.Manifest;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class AzureBackupStoreDeleteTest {

  @Mock private ManifestManager manifestManager;
  @Mock private FileSetManager fileSetManager;
  @Mock private BlobServiceClient blobServiceClient;

  private AzureBackupStore store;
  private final BackupIdentifierImpl id = new BackupIdentifierImpl(1, 2, 3L);

  @BeforeEach
  void setUp() throws Exception {
    final var config =
        new AzureBackupConfig.Builder()
            .withCreateContainer(false)
            .withContainerName("test")
            .build();
    final var containerClient = mock(BlobContainerClient.class);
    when(blobServiceClient.getBlobContainerClient(anyString())).thenReturn(containerClient);
    when(containerClient.exists()).thenReturn(true);
    when(blobServiceClient.getHttpPipeline()).thenReturn(mock(HttpPipeline.class));
    when(blobServiceClient.getAccountUrl()).thenReturn("https://test.blob.core.windows.net");
    when(blobServiceClient.getServiceVersion()).thenReturn(BlobServiceVersion.getLatest());

    store = new AzureBackupStore(config, blobServiceClient);
    setField(store, "manifestManager", manifestManager);
    setField(store, "fileSetManager", fileSetManager);
  }

  @Test
  void shouldNotDeleteManifestWhenContentsDeletionFails() {
    // given
    final Manifest deletedManifest = Manifest.createFailed(id).delete();
    when(manifestManager.getManifest(id)).thenReturn(deletedManifest);
    when(fileSetManager.collectBlobUrls(eq(id), any()))
        .thenReturn(List.of("https://test.blob.core.windows.net/container/contents/file1"));
    doThrow(mock(BlobStorageException.class)).when(fileSetManager).deleteBlobs(any());

    // when
    assertThatThrownBy(() -> store.delete(id).join())
        .hasCauseInstanceOf(BlobStorageException.class);

    // then - manifest was never deleted
    verify(manifestManager, never()).deleteManifest(any());
  }

  private static void setField(final Object target, final String name, final Object value)
      throws Exception {
    final Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }
}
