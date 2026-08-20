/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.gcs;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import com.google.cloud.BatchResult.Callback;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageBatch;
import com.google.cloud.storage.StorageBatchResult;
import com.google.cloud.storage.StorageException;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.gcs.util.GcsContainer;
import io.camunda.zeebe.backup.testkit.support.TestBackupProvider;
import java.time.Duration;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class GcsBackupStoreBatchDeletionTest {
  @Container private static final GcsContainer GCS = new GcsContainer();
  private static final String BUCKET_NAME = RandomStringUtils.randomAlphabetic(10).toLowerCase();

  @Test
  void shouldNotDeleteManifestOnContentException() throws Exception {
    // given
    final var config =
        new GcsBackupConfig.Builder()
            .withBucketName(BUCKET_NAME)
            .withHost(GCS.externalEndpoint())
            .withoutAuthentication()
            .build();

    final Storage realClient = GcsBackupStore.buildClient(config);
    realClient.create(BucketInfo.of(BUCKET_NAME));

    final Storage spyClient = spy(realClient);
    final var store = new GcsBackupStore(config, spyClient);

    final var backupId = new BackupIdentifierImpl(1, 2, 3);
    final var backup = TestBackupProvider.simpleBackupWithId(backupId);

    final var failedSegment = backup.segments().names().stream().findFirst().orElseThrow();
    final var fullSegmentPath = "2/3/1/segments/" + failedSegment;

    interceptSegmentDeletion(realClient, fullSegmentPath, spyClient);

    try {
      store.save(backup).join();
      store.markDeleted(backupId).join();

      // when - delete fails due to intercepted segment deletion error
      store.delete(backupId).exceptionally(t -> null).join();

      // then - manifest should still exist since content deletion failed
      assertThat(store.getStatus(backupId))
          .succeedsWithin(Duration.ofSeconds(10))
          .extracting(BackupStatus::statusCode)
          .isEqualTo(BackupStatusCode.DELETED);
    } finally {
      store.closeAsync().join();
      realClient.close();
    }
  }

  private static void interceptSegmentDeletion(
      final Storage realClient, final String fullSegmentPath, final Storage spyClient) {
    doAnswer(invocation -> createInterceptedBatch(realClient, fullSegmentPath))
        .when(spyClient)
        .batch();
  }

  private static StorageBatch createInterceptedBatch(
      final Storage realClient, final String fullSegmentPath) {
    final StorageBatch realBatch = realClient.batch();
    final StorageBatch spyBatch = spy(realBatch);

    doAnswer(
            deleteInvocation ->
                interceptDelete(deleteInvocation.getArgument(0), fullSegmentPath, realBatch))
        .when(spyBatch)
        .delete(any(BlobId.class));

    return spyBatch;
  }

  private static StorageBatchResult<Boolean> interceptDelete(
      final BlobId blobId, final String fullSegmentPath, final StorageBatch realBatch) {
    if (blobId.getName().contains(fullSegmentPath)) {
      return createFailingBatchResult();
    }
    return realBatch.delete(blobId);
  }

  @SuppressWarnings("unchecked")
  private static StorageBatchResult<Boolean> createFailingBatchResult() {
    final var result = mock(StorageBatchResult.class);
    doAnswer(
            notifyInvocation -> {
              final Callback<Boolean, StorageException> callback = notifyInvocation.getArgument(0);
              callback.error(new StorageException(403, "Access Denied"));
              return null;
            })
        .when(result)
        .notify(any());
    return result;
  }
}
