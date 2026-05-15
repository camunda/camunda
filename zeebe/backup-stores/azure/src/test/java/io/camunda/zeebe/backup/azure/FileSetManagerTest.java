/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.azure;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.batch.BlobBatch;
import com.azure.storage.blob.batch.BlobBatchClient;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class FileSetManagerTest {

  @Mock private BlobContainerClient containerClient;
  @Mock private BlobBatchClient blobBatchClient;

  private FileSetManager manager;

  @BeforeEach
  void setUp() {
    manager = new FileSetManager(containerClient, blobBatchClient, false);
  }

  @Test
  void shouldSplitDeleteAcrossMultipleBatches() {
    // given - 257 URLs exceeds MAX_DELETE_BLOB_BATCH_SIZE (256)
    final var blobBatch = mock(BlobBatch.class);
    when(blobBatchClient.getBlobBatch()).thenReturn(blobBatch);
    final var urls =
        IntStream.range(0, 257)
            .mapToObj(i -> "https://test.blob.core.windows.net/container/file" + i)
            .toList();

    // when
    manager.deleteBlobs(urls);

    // then - submitBatch called twice: first 256, then 1
    verify(blobBatchClient, times(2)).submitBatch(any());
  }
}
