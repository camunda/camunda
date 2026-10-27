/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.azure;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.specialized.BlobLeaseClientBuilder;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.azure.util.AzuriteContainer;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.testkit.support.TestBackupProvider;
import java.time.Duration;
import java.util.UUID;
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

    final BlobServiceClient client = buildClient(config);
    final BlobContainerClient containerClient = client.getBlobContainerClient(containerName);

    final var backupId = new BackupIdentifierImpl(1, 2, 3);
    final var backup = TestBackupProvider.simpleBackupWithId(backupId);

    final var store = new AzureBackupStore(config, client);

    try {
      store.save(backup).join();
      store.markDeleted(backupId).join();

      // Acquire leases on segment blobs for backupId to make them undeletable
      acquireLeasesOnSegmentBlobs(containerClient, backupId);

      // when - delete fails due to leased blobs
      store.delete(backupId).exceptionally(t -> null).join();

      // then - manifest should still exist since batch deletion failed
      assertThat(store.getStatus(backupId))
          .succeedsWithin(Duration.ofSeconds(10))
          .extracting(BackupStatus::statusCode)
          .isEqualTo(BackupStatusCode.DELETED);
    } finally {
      store.closeAsync().join();
    }
  }

  private void acquireLeasesOnSegmentBlobs(
      final BlobContainerClient containerClient, final BackupIdentifierImpl backupId) {
    final var prefix =
        "contents/"
            + backupId.partitionId()
            + "/"
            + backupId.checkpointId()
            + "/"
            + backupId.nodeId()
            + "/segments/";

    containerClient.listBlobs().stream()
        .filter(blob -> blob.getName().startsWith(prefix))
        .forEach(
            blob -> {
              final var blobClient = containerClient.getBlobClient(blob.getName());
              final var leaseClient =
                  new BlobLeaseClientBuilder().blobClient(blobClient).buildClient();
              leaseClient.acquireLease(60);
            });
  }

  private static BlobServiceClient buildClient(final AzureBackupConfig config) {
    return new BlobServiceClientBuilder().connectionString(config.connectionString()).buildClient();
  }
}
