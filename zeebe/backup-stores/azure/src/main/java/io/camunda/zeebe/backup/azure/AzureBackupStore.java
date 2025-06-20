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
import io.camunda.unifiedconfig.AzureStore;
import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard;
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
import org.slf4j.event.Level;

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

  public AzureBackupStore(final AzureStore azureStoreConfig) {
    this(azureStoreConfig, buildClient(azureStoreConfig));
  }

  public AzureBackupStore(final AzureStore azureStoreConfig, final BlobServiceClient client) {
    executor = Executors.newVirtualThreadPerTaskExecutor();
    final BlobContainerClient blobContainerClient = getContainerClient(client, azureStoreConfig);

    fileSetManager = new FileSetManager(blobContainerClient, isCreateContainer(azureStoreConfig));
    manifestManager = new ManifestManager(blobContainerClient, isCreateContainer(azureStoreConfig));
  }

  public static BlobServiceClient buildClient(final AzureStore azureStoreConfig) {
    // BlobServiceClientBuilder has their own validations, for building the client
    if (azureStoreConfig.getSasTokenType() != null && azureStoreConfig.getSasToken() != null) {
      return new BlobServiceClientBuilder()
          .sasToken(azureStoreConfig.getSasToken())
          .endpoint(azureStoreConfig.getEndpoint())
          .buildClient();
    } else if (azureStoreConfig.getConnectionString() != null) {
      return new BlobServiceClientBuilder()
          .connectionString(azureStoreConfig.getConnectionString())
          .buildClient();
    } else if (azureStoreConfig.getAccountName() != null || azureStoreConfig.getAccountKey() != null) {
      final var credential =
          new StorageSharedKeyCredential(
              Objects.requireNonNull(
                  azureStoreConfig.getAccountName(),
                  "Account key is specified but no account name was provided."),
              Objects.requireNonNull(
                  azureStoreConfig.getAccountKey(),
                  "Account name is specified but no account key was provided."));
      return new BlobServiceClientBuilder()
          .endpoint(azureStoreConfig.getEndpoint())
          .credential(credential)
          .buildClient();
    } else {
      LOG.info(
          "No connection string, sas token or account credentials are configured, using DefaultAzureCredentialBuilder for authentication.");
      return new BlobServiceClientBuilder()
          .endpoint(azureStoreConfig.getEndpoint())
          .credential(new DefaultAzureCredentialBuilder().build())
          .buildClient();
    }
  }

  BlobContainerClient getContainerClient(
      final BlobServiceClient client,
      final AzureStore azureStoreConfig) {
    final BlobContainerClient blobContainerClient =
        client.getBlobContainerClient(azureStoreConfig.getContainerName());

    if (!azureStoreConfig.isCreateContainer()) {
      LOG.debug(
          "Setting up Azure Store with existing container: {}",
          blobContainerClient.getBlobContainerName());
      // (delegation and service) sas token don't have the permissions to list containers,
      //  we trust that the user has created the container beforehand.
      if ((azureStoreConfig.getSasTokenType() == null || "account".equals(azureStoreConfig.getSasTokenType()))
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

  public static void validateConfig(final AzureStore azureStoreConfig) {
    if (azureStoreConfig.getSasTokenType() != null &&
        !"account".equals(azureStoreConfig.getSasToken())) {
      LOG.info(
          "User delegation or service SAS tokens are enabled, which do "
              + "not have permissions to access/create containers. The "
              + "creation and checks of the container existence will be skipped.");
    }
    if (moreThanOneNonNull(
        azureStoreConfig.getAccountKey(),
        azureStoreConfig.getConnectionString(),
        azureStoreConfig.getSasToken())) {
      LOG.warn(
          "More than one authentication method is configured, if present account SAS token will be used, "
              + "followed by connection string, and then account name with account key.");
    }
    if (azureStoreConfig.getConnectionString() == null && azureStoreConfig.getEndpoint() == null) {
      throw new IllegalArgumentException("Connection string or endpoint is required");
    }
    if (azureStoreConfig.getAccountKey() != null && azureStoreConfig.getAccountName() == null) {
      throw new IllegalArgumentException("Account key is specified but account name is missing");
    }
    if (azureStoreConfig.getAccountName() != null && azureStoreConfig.getAccountKey() == null) {
      throw new IllegalArgumentException("Account name is specified but account key is missing");
    }
    if (azureStoreConfig.getContainerName() == null) {
      throw new IllegalArgumentException("Container name cannot be null.");
    }
  }

  private boolean isCreateContainer(final AzureStore azureStoreConfig) {
    // if sas token is enabled, and its of the type delegation or service, then we don't create the
    // container.
    if (azureStoreConfig.getSasTokenType() != null &&
        !"account".equals(azureStoreConfig.getSasTokenType())) {
      return false;
    } else {
      return azureStoreConfig.isCreateContainer();
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

  public static BackupStore of(final AzureStore azureStoreConfig) {
    return new AzureBackupStore(azureStoreConfig).logging(LOG, Level.INFO);
  }
}
