/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.azure;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import io.camunda.zeebe.backup.azure.util.AzuriteContainer;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupImpl;
import io.camunda.zeebe.backup.common.Manifest.StatusCode;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.util.VersionUtil;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
final class ManifestManagerTest {

  @Container private static final AzuriteContainer AZURITE_CONTAINER = new AzuriteContainer();

  private ManifestManager manifestManager;
  private BackupIdentifierImpl backupIdentifier;
  private BackupImpl backup;

  @BeforeEach
  void setUp() {
    final String containerName = UUID.randomUUID().toString();
    final BlobServiceClient blobServiceClient =
        new BlobServiceClientBuilder()
            .connectionString(AZURITE_CONTAINER.getConnectString())
            .buildClient();
    final BlobContainerClient blobContainerClient =
        blobServiceClient.getBlobContainerClient(containerName);
    manifestManager = new ManifestManager(blobContainerClient, true);
    backupIdentifier = new BackupIdentifierImpl(1337, 0, 42L);
    backup = createBackup(backupIdentifier);
  }

  private static BackupImpl createBackup(final BackupIdentifierImpl backupIdentifier) {
    return new BackupImpl(
        backupIdentifier,
        new BackupDescriptorImpl(
            backupIdentifier.checkpointId(),
            1,
            VersionUtil.getVersion(),
            Instant.now(),
            CheckpointType.MANUAL_BACKUP),
        new NamedFileSetImpl(Map.of()),
        new NamedFileSetImpl(Map.of()));
  }

  @Nested
  class ManifestDeleteTransitionTest {
    @Test
    void shouldMarkInProgressManifestAsDeleted() {
      // given
      final var persisted = manifestManager.createInitialManifest(backup);
      final var inProgressManifest = persisted.manifest();

      // when
      manifestManager.markAsDeleted(inProgressManifest);

      // then
      final var manifest = manifestManager.getManifest(backupIdentifier);
      assertThat(manifest.statusCode()).isEqualTo(StatusCode.DELETED);
      assertThat(manifest.id()).isEqualTo(inProgressManifest.id());
    }

    @Test
    void shouldMarkCompletedManifestAsDeleted() {
      // given
      final var persisted = manifestManager.createInitialManifest(backup);
      manifestManager.completeManifest(persisted);
      final var completedManifest = manifestManager.getManifest(backupIdentifier);

      // when
      manifestManager.markAsDeleted(completedManifest);

      // then
      final var manifest = manifestManager.getManifest(backupIdentifier);
      assertThat(manifest.statusCode()).isEqualTo(StatusCode.DELETED);
      assertThat(manifest.id()).isEqualTo(backupIdentifier);
    }

    @Test
    void shouldMarkFailedManifestAsDeleted() {
      // given
      manifestManager.createInitialManifest(backup);
      manifestManager.markAsFailed(backupIdentifier, "failure reason");
      final var failedManifest = manifestManager.getManifest(backupIdentifier);

      // when
      manifestManager.markAsDeleted(failedManifest);

      // then
      final var manifest = manifestManager.getManifest(backupIdentifier);
      assertThat(manifest.statusCode()).isEqualTo(StatusCode.DELETED);
      assertThat(manifest.id()).isEqualTo(backupIdentifier);
    }

    @Test
    void shouldNotUpdateAlreadyDeletedManifest() {
      // given
      final var persisted = manifestManager.createInitialManifest(backup);
      manifestManager.completeManifest(persisted);
      final var completedManifest = manifestManager.getManifest(backupIdentifier);
      manifestManager.markAsDeleted(completedManifest);
      final var deletedManifest = manifestManager.getManifest(backupIdentifier);
      final var modifiedAt = deletedManifest.asDeleted().modifiedAt();

      // when
      manifestManager.markAsDeleted(deletedManifest);

      // then - manifest should remain deleted and unchanged
      final var manifest = manifestManager.getManifest(backupIdentifier);
      assertThat(manifest.statusCode()).isEqualTo(StatusCode.DELETED);
      assertThat(manifest.asDeleted().modifiedAt()).isEqualTo(modifiedAt);
    }
  }
}
