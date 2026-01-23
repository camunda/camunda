/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.azure;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.batch.BlobBatch;
import com.azure.storage.blob.batch.BlobBatchClient;
import com.azure.storage.blob.models.BlobStorageException;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.azure.util.AzuriteContainer;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.testkit.support.TestBackupProvider;
import java.lang.reflect.Field;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class AzureBackupStoreBatchDeletionTest {
  @Container private static final AzuriteContainer AZURITE = new AzuriteContainer();
  private final String containerName = UUID.randomUUID().toString();

  @Test
  void shouldNotDeleteManifestOnContentException() throws Exception {
    // given
    final var config =
        new AzureBackupConfig.Builder()
            .withConnectionString(AZURITE.getConnectString())
            .withContainerName(containerName)
            .build();

    final BlobServiceClient realClient = buildClient(config);

    final var backupId = new BackupIdentifierImpl(1, 2, 3);
    final var backupId2 = new BackupIdentifierImpl(1, 2, 5);
    final var backup = TestBackupProvider.simpleBackupWithId(backupId);
    final var backup2 = TestBackupProvider.simpleBackupWithId(backupId2);

    final var failedSegment = backup.segments().names().stream().findFirst().orElseThrow();
    // Azure encodes paths in blob URLs
    final var fullSegmentPath =
        URLEncoder.encode("contents/2/3/1/segments/" + failedSegment, StandardCharsets.UTF_8);

    // Create store with intercepted batch client
    final var store = createStoreWithInterceptedBatchClient(config, realClient, fullSegmentPath);

    try {
      store.save(backup).thenCompose(ignore -> store.save(backup2)).join();

      // when
      store.delete(List.of(backupId, backupId2)).join();

      // then
      assertThat(store.getStatus(backupId2))
          .succeedsWithin(Duration.ofSeconds(10))
          .extracting(BackupStatus::statusCode)
          .isEqualTo(BackupStatusCode.DOES_NOT_EXIST);

      assertThat(store.getStatus(backupId))
          .succeedsWithin(Duration.ofSeconds(10))
          .extracting(BackupStatus::statusCode)
          .isEqualTo(BackupStatusCode.COMPLETED);
    } finally {
      store.closeAsync().join();
    }
  }

  private static BlobServiceClient buildClient(final AzureBackupConfig config) {
    return new BlobServiceClientBuilder().connectionString(config.connectionString()).buildClient();
  }

  private AzureBackupStore createStoreWithInterceptedBatchClient(
      final AzureBackupConfig config,
      final BlobServiceClient realClient,
      final String fullSegmentPath)
      throws Exception {

    final var store = new AzureBackupStore(config, realClient);

    // Spy the blobBatchClient
    final Field blobBatchClientField = AzureBackupStore.class.getDeclaredField("blobBatchClient");
    blobBatchClientField.setAccessible(true);

    final var realBatchClient = (BlobBatchClient) blobBatchClientField.get(store);
    final var spyBatchClient = spy(realBatchClient);

    // Track batches that should fail using identity
    final Set<BlobBatch> failingBatches = Collections.newSetFromMap(new ConcurrentHashMap<>());

    doAnswer(
            invocation -> {
              final var realBatch = (BlobBatch) invocation.callRealMethod();
              final var spyBatch = spy(realBatch);

              // Intercept deleteBlob to track if this batch contains the failing segment
              doAnswer(
                      deleteInvocation -> {
                        final String blobUrl = deleteInvocation.getArgument(0);
                        if (blobUrl.contains(fullSegmentPath)) {
                          failingBatches.add(spyBatch);
                        }
                        return deleteInvocation.callRealMethod();
                      })
                  .when(spyBatch)
                  .deleteBlob(any(String.class));

              return spyBatch;
            })
        .when(spyBatchClient)
        .getBlobBatch();

    doAnswer(
            invocation -> {
              final BlobBatch batch = invocation.getArgument(0);
              if (failingBatches.remove(batch)) {
                throw new BlobStorageException("Simulated batch failure", null, null);
              }
              return invocation.callRealMethod();
            })
        .when(spyBatchClient)
        .submitBatch(any(BlobBatch.class));

    blobBatchClientField.set(store, spyBatchClient);

    return store;
  }
}
