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
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class AzureBackupStoreBatchDeletionTest {
  @Container private static final AzuriteContainer AZURITE = new AzuriteContainer();
  private final String containerName = UUID.randomUUID().toString();

  @Test
  void shouldNotDeleteManifestOnContentException() throws IOException {
    // given
    final var config =
        new AzureBackupConfig.Builder()
            .withConnectionString(AZURITE.getConnectString())
            .withContainerName(containerName)
            .build();

    final BlobServiceClient client = buildClient(config);
    final BlobContainerClient containerClient = client.getBlobContainerClient(containerName);

    final var backupId = new BackupIdentifierImpl(1, 2, 3);
    final var backupId2 = new BackupIdentifierImpl(1, 2, 5);
    final var backup = TestBackupProvider.simpleBackupWithId(backupId);
    final var backup2 = TestBackupProvider.simpleBackupWithId(backupId2);

    final var store = new AzureBackupStore(config, client);

    try {
      store.save(backup).thenCompose(ignore -> store.save(backup2)).join();

      // Acquire leases on segment blobs for backupId to make them undeletable
      acquireLeasesOnSegmentBlobs(containerClient, backupId);

      // when
      store.delete(List.of(backupId, backupId2)).join();

      // then - backupId2 should be deleted successfully
      assertThat(store.getStatus(backupId2))
          .succeedsWithin(Duration.ofSeconds(10))
          .extracting(BackupStatus::statusCode)
          .isEqualTo(BackupStatusCode.DOES_NOT_EXIST);

      // backupId manifest should still exist since batch deletion failed
      assertThat(store.getStatus(backupId))
          .succeedsWithin(Duration.ofSeconds(10))
          .extracting(BackupStatus::statusCode)
          .isEqualTo(BackupStatusCode.COMPLETED);

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
              // Acquire a lease for 60 seconds (minimum is 15 seconds)
              leaseClient.acquireLease(60);
            });
  }

  private static BlobServiceClient buildClient(final AzureBackupConfig config) {
    return new BlobServiceClientBuilder().connectionString(config.connectionString()).buildClient();
  }
}
