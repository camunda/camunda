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
import java.util.List;
import java.util.stream.Stream;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class GcsBackupStoreBatchDeletionTest {
  @Container private static final GcsContainer GCS = new GcsContainer();
  private static final String BUCKET_NAME = RandomStringUtils.randomAlphabetic(10).toLowerCase();

  private static Stream<Arguments> basePathProvider() {
    return Stream.of(
        Arguments.of(""), Arguments.of(RandomStringUtils.randomAlphabetic(10).toLowerCase()));
  }

  @ParameterizedTest
  @MethodSource("basePathProvider")
  void shouldNotDeleteManifestOnContentException(final String basePath) throws Exception {
    // given
    final var configBuilder =
        new GcsBackupConfig.Builder()
            .withBucketName(BUCKET_NAME)
            .withHost(GCS.externalEndpoint())
            .withoutAuthentication();

    if (!basePath.isEmpty()) {
      configBuilder.withBasePath(basePath);
    }

    final var config = configBuilder.build();
    final Storage realClient = GcsBackupStore.buildClient(config);
    realClient.create(BucketInfo.of(BUCKET_NAME));

    final Storage spyClient = spy(realClient);
    final var store = new GcsBackupStore(config, spyClient);

    final var backupId = new BackupIdentifierImpl(1, 2, 3);
    final var backupId2 = new BackupIdentifierImpl(1, 2, 5);
    final var backup = TestBackupProvider.simpleBackupWithId(backupId);
    final var backup2 = TestBackupProvider.simpleBackupWithId(backupId2);

    final var failedSegment = backup.segments().names().stream().findFirst().orElseThrow();
    final var fullSegmentPath = "2/3/1/segments/" + failedSegment;

    interceptSegmentDeletion(realClient, fullSegmentPath, spyClient);

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
