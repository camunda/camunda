/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.azure;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;
import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.azure.manifest.Manifest;
import io.camunda.zeebe.backup.common.BackupImpl;
import io.camunda.zeebe.backup.common.BackupStatusImpl;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * {@link BackupStore} for Azure. Stores all backups in a given bucket.
 *
 * <p>All created object keys are prefixed by the {@link BackupIdentifier}, with the following
 * scheme: {@code basePath/partitionId/checkpointId/nodeId}.
 */
public final class AzureBackupStore implements BackupStore {
  public static final String ERROR_MSG_BACKUP_NOT_FOUND =
      "Expected to restore from backup with id '%s', but does not exist.";
  public static final String ERROR_MSG_BACKUP_WRONG_STATE_TO_RESTORE =
      "Expected to restore from completed backup with id '%s', but was in state '%s'";
  public static final String SNAPSHOT_FILESET_NAME = "snapshot";
  public static final String SEGMENTS_FILESET_NAME = "segments";
  private final ExecutorService executor;
  private final FileSetManager fileSetManager;
  private final BlobContainerClient blobContainerClient;
  private final ManifestManager manifestManager;

  public AzureBackupStore(final AzureBackupConfig config) {
    this(config, buildClient(config));
  }

  public AzureBackupStore(final AzureBackupConfig config, final BlobServiceClient client) {
    executor = Executors.newWorkStealingPool(4);
    blobContainerClient = client.getBlobContainerClient(config.containerName());
    fileSetManager = new FileSetManager(blobContainerClient);
    manifestManager = new ManifestManager(blobContainerClient);
  }

  public static BlobServiceClient buildClient(final AzureBackupConfig config) {

    // BlobServiceClientBuilder has their own validations, for building the client
    if (config.connectionString() != null) {
      return new BlobServiceClientBuilder()
          .connectionString(config.connectionString())
          .buildClient();
    } else {
      return new BlobServiceClientBuilder()
          .endpoint(config.endpoint())
          .credential(new StorageSharedKeyCredential(config.accountName(), config.accountKey()))
          .buildClient();
    }
  }

  @Override
  public CompletableFuture<Void> save(final Backup backup) {
    return CompletableFuture.runAsync(
        () -> {
          final var persistedManifest = manifestManager.createInitialManifest(backup);
          try {
            fileSetManager.save(backup.id(), SNAPSHOT_FILESET_NAME, backup.snapshot());
            fileSetManager.save(backup.id(), SEGMENTS_FILESET_NAME, backup.segments());
            manifestManager.completeManifest(persistedManifest);
          } catch (final Exception e) {
            manifestManager.markAsFailed(persistedManifest, e.getMessage());
            try {
              throw e;
            } catch (final NoSuchFileException ex) {
              throw new RuntimeException(ex);
            }
          }
        },
        executor);
  }

  @Override
  public CompletableFuture<BackupStatus> getStatus(final BackupIdentifier id) {
    return CompletableFuture.supplyAsync(
        () -> {
          final var manifest = manifestManager.getManifest(id);
          if (manifest == null) {
            return BackupStatusImpl.doesNotExist(id);
          }
          return toStatus(manifest);
        },
        executor);
  }

  @Override
  public CompletableFuture<Collection<BackupStatus>> list(final BackupIdentifierWildcard wildcard) {
    return CompletableFuture.supplyAsync(
        () ->
            manifestManager.listManifests(wildcard).stream()
                .map(AzureBackupStore::toStatus)
                .toList(),
        executor);
  }

  @Override
  public CompletableFuture<Void> delete(final BackupIdentifier id) {
    return CompletableFuture.runAsync(
        () -> {
          manifestManager.deleteManifest(id);
          fileSetManager.delete(id, SNAPSHOT_FILESET_NAME);
          fileSetManager.delete(id, SEGMENTS_FILESET_NAME);
        },
        executor);
  }

  @Override
  public CompletableFuture<Backup> restore(final BackupIdentifier id, final Path targetFolder) {
    return CompletableFuture.supplyAsync(
        () -> {
          final var manifest = manifestManager.getManifest(id);
          if (manifest == null) {
            throw new RuntimeException(ERROR_MSG_BACKUP_NOT_FOUND.formatted(id));
          }
          return switch (manifest.statusCode()) {
            case FAILED, IN_PROGRESS -> throw new RuntimeException(
                ERROR_MSG_BACKUP_WRONG_STATE_TO_RESTORE.formatted(id, manifest.statusCode()));
            case COMPLETED -> {
              final var completed = manifest.asCompleted();
              final var snapshot =
                  fileSetManager.restore(
                      id, SNAPSHOT_FILESET_NAME, completed.snapshot(), targetFolder);
              final var segments =
                  fileSetManager.restore(
                      id, SEGMENTS_FILESET_NAME, completed.segments(), targetFolder);
              yield new BackupImpl(id, manifest.descriptor(), snapshot, segments);
            }
          };
        },
        executor);
  }

  @Override
  public CompletableFuture<BackupStatusCode> markFailed(
      final BackupIdentifier id, final String failureReason) {
    return CompletableFuture.supplyAsync(
        () -> {
          manifestManager.markAsFailed(id, failureReason);
          return BackupStatusCode.FAILED;
        },
        executor);
  }

  @Override
  public CompletableFuture<Void> closeAsync() {
    return CompletableFuture.runAsync(
        () -> {
          try {
            executor.shutdown();
            final var closed = executor.awaitTermination(1, TimeUnit.MINUTES);
            if (!closed) {
              executor.shutdownNow();
            }
            if (blobContainerClient.exists()) {
              blobContainerClient.delete();
            }
          } catch (final Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  private static BackupStatus toStatus(final Manifest manifest) {
    return switch (manifest.statusCode()) {
      case IN_PROGRESS -> new BackupStatusImpl(
          manifest.id(),
          Optional.ofNullable(manifest.descriptor()),
          BackupStatusCode.IN_PROGRESS,
          Optional.empty(),
          Optional.ofNullable(manifest.createdAt()),
          Optional.ofNullable(manifest.modifiedAt()));
      case COMPLETED -> new BackupStatusImpl(
          manifest.id(),
          Optional.ofNullable(manifest.descriptor()),
          BackupStatusCode.COMPLETED,
          Optional.empty(),
          Optional.ofNullable(manifest.createdAt()),
          Optional.ofNullable(manifest.modifiedAt()));
      case FAILED -> new BackupStatusImpl(
          manifest.id(),
          Optional.ofNullable(manifest.descriptor()),
          BackupStatusCode.FAILED,
          Optional.ofNullable(manifest.asFailed().failureReason()),
          Optional.ofNullable(manifest.createdAt()),
          Optional.ofNullable(manifest.modifiedAt()));
    };
  }
}
