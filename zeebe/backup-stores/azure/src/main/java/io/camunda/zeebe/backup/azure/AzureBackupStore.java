/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.azure;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;
import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard;
import io.camunda.zeebe.backup.api.BackupIndexHandle;
import io.camunda.zeebe.backup.api.BackupIndexIdentifier;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.azure.AzureBackupStoreException.ContainerDoesNotExist;
import io.camunda.zeebe.backup.common.BackupImpl;
import io.camunda.zeebe.backup.common.BackupStatusImpl;
import io.camunda.zeebe.backup.common.BackupStoreException.UnexpectedManifestState;
import io.camunda.zeebe.backup.common.Manifest;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
  private static final Logger LOG = LoggerFactory.getLogger(AzureBackupStore.class);
  private final ExecutorService executor;
  private final FileSetManager fileSetManager;
  private final ManifestManager manifestManager;
  private final AzureIndexManager indexManager;

  AzureBackupStore(final AzureBackupConfig config) {
    this(config, buildClient(config));
  }

  AzureBackupStore(final AzureBackupConfig config, final BlobServiceClient client) {
    executor = Executors.newVirtualThreadPerTaskExecutor();
    final BlobContainerClient blobContainerClient = getContainerClient(client, config);

    fileSetManager = new FileSetManager(blobContainerClient, isCreateContainer(config));
    manifestManager = new ManifestManager(blobContainerClient, isCreateContainer(config));
    indexManager = new AzureIndexManager(blobContainerClient, isCreateContainer(config));
  }

  public static BlobServiceClient buildClient(final AzureBackupConfig config) {
    // BlobServiceClientBuilder has their own validations, for building the client
    if (config.sasTokenConfig() != null) {
      return new BlobServiceClientBuilder()
          .sasToken(config.sasTokenConfig().value())
          .endpoint(config.endpoint())
          .buildClient();
    } else if (config.connectionString() != null) {
      return new BlobServiceClientBuilder()
          .connectionString(config.connectionString())
          .buildClient();
    } else if (config.accountName() != null || config.accountKey() != null) {
      final var credential =
          new StorageSharedKeyCredential(
              Objects.requireNonNull(
                  config.accountName(),
                  "Account key is specified but no account name was provided."),
              Objects.requireNonNull(
                  config.accountKey(),
                  "Account name is specified but no account key was provided."));
      return new BlobServiceClientBuilder()
          .endpoint(config.endpoint())
          .credential(credential)
          .buildClient();
    } else {
      LOG.info(
          "No connection string, sas token or account credentials are configured, using DefaultAzureCredentialBuilder for authentication.");
      return new BlobServiceClientBuilder()
          .endpoint(config.endpoint())
          .credential(new DefaultAzureCredentialBuilder().build())
          .buildClient();
    }
  }

  BlobContainerClient getContainerClient(
      final BlobServiceClient client, final AzureBackupConfig config) {
    final BlobContainerClient blobContainerClient =
        client.getBlobContainerClient(config.containerName());

    if (!config.createContainer()) {
      LOG.debug(
          "Setting up Azure Store with existing container: {}",
          blobContainerClient.getBlobContainerName());
      // (delegation and service) sas token don't have the permissions to list containers,
      //  we trust that the user has created the container beforehand.
      if ((config.sasTokenConfig() == null || config.sasTokenConfig().type().isAccount())
          && !blobContainerClient.exists()) {
        throw new ContainerDoesNotExist(
            ("The container %s does not exist. Please create it before using "
                    + "the backup store. Otherwise set createContainer to true, "
                    + "to enable the creation of the container during Azure "
                    + "backup store initialization.")
                .formatted(blobContainerClient.getBlobContainerName()));
      }
    }

    return blobContainerClient;
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
            manifestManager.markAsFailed(persistedManifest.manifest().id(), e.getMessage());
            throw e;
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
          return Manifest.toStatus(manifest);
        },
        executor);
  }

  @Override
  public CompletableFuture<Collection<BackupStatus>> list(final BackupIdentifierWildcard wildcard) {
    return CompletableFuture.supplyAsync(
        () -> manifestManager.listManifests(wildcard).stream().map(Manifest::toStatus).toList(),
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
            throw new UnexpectedManifestState(ERROR_MSG_BACKUP_NOT_FOUND.formatted(id));
          }
          return switch (manifest.statusCode()) {
            case FAILED, IN_PROGRESS ->
                throw new UnexpectedManifestState(
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
  public CompletableFuture<BackupIndexHandle> storeIndex(final BackupIndexHandle indexHandle) {
    if (!(indexHandle instanceof final AzureBackupIndexHandle azureIndexFile)) {
      throw new IllegalArgumentException(
          "Expected index file of type %s but got %s: %s"
              .formatted(
                  AzureBackupIndexHandle.class.getSimpleName(),
                  indexHandle.getClass().getSimpleName(),
                  indexHandle));
    }
    return CompletableFuture.supplyAsync(() -> indexManager.upload(azureIndexFile), executor);
  }

  @Override
  public CompletableFuture<BackupIndexHandle> restoreIndex(
      final BackupIndexIdentifier id, final Path targetPath) {
    return CompletableFuture.supplyAsync(() -> indexManager.download(id, targetPath), executor);
  }

  @Override
  public CompletableFuture<Void> closeAsync() {
    return CompletableFuture.runAsync(
        () -> {
          try {
            executor.shutdown();
            final var closed = executor.awaitTermination(1, TimeUnit.MINUTES);
            if (!closed) {
              LOG.warn("Failed to orderly shutdown Azure Store Executor within one minute.");
              executor.shutdownNow();
            }
          } catch (final Exception e) {
            LOG.error("Failed to shutdown of Azure Store Executor.");
            throw new RuntimeException(e);
          }
        });
  }

  public static void validateConfig(final AzureBackupConfig config) {
    if (config.sasTokenConfig() != null && !config.sasTokenConfig().type().isAccount()) {
      LOG.info(
          "User delegation or service SAS tokens are enabled, which do "
              + "not have permissions to access/create containers. The "
              + "creation and checks of the container existence will be skipped.");
    }
    if (moreThanOneNonNull(
        config.accountKey(), config.connectionString(), config.sasTokenConfig())) {
      LOG.warn(
          "More than one authentication method is configured, if present account SAS token will be used, "
              + "followed by connection string, and then account name with account key.");
    }
    if (config.connectionString() == null && config.endpoint() == null) {
      throw new IllegalArgumentException("Connection string or endpoint is required");
    }
    if (config.accountKey() != null && config.accountName() == null) {
      throw new IllegalArgumentException("Account key is specified but account name is missing");
    }
    if (config.accountName() != null && config.accountKey() == null) {
      throw new IllegalArgumentException("Account name is specified but account key is missing");
    }
    if (config.containerName() == null) {
      throw new IllegalArgumentException("Container name cannot be null.");
    }
  }

  private boolean isCreateContainer(final AzureBackupConfig config) {
    // if sas token is enabled, and its of the type delegation or service, then we don't create the
    // container.
    if (config.sasTokenConfig() != null && !config.sasTokenConfig().type().isAccount()) {
      return false;
    } else {
      return config.createContainer();
    }
  }

  private static boolean moreThanOneNonNull(final Object... values) {
    int count = 0;
    for (final Object value : values) {
      if (value != null) {
        count++;
      }
      if (count > 1) {
        return true;
      }
    }
    return false;
  }

  public static BackupStore of(final AzureBackupConfig storeConfig) {
    return new AzureBackupStore(storeConfig);
  }
}
