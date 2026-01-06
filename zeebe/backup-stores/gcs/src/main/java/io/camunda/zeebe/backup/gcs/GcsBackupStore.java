/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.gcs;

import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Storage.BlobListOption;
import com.google.cloud.storage.StorageOptions;
import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupImpl;
import io.camunda.zeebe.backup.common.BackupStatusImpl;
import io.camunda.zeebe.backup.common.Manifest;
import io.camunda.zeebe.backup.gcs.GcsBackupStoreException.ConfigurationException;
import io.camunda.zeebe.backup.gcs.GcsConnectionConfig.Authentication.None;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

public final class GcsBackupStore implements BackupStore {
  public static final String ERROR_MSG_BACKUP_NOT_FOUND =
      "Expected to restore from backup with id '%s', but does not exist.";
  public static final String ERROR_MSG_BACKUP_WRONG_STATE_TO_RESTORE =
      "Expected to restore from completed backup with id '%s', but was in state '%s'";
  public static final String ERROR_VALIDATION_FAILED =
      "Invalid configuration for GcsBackupStore: %s";
  public static final String SNAPSHOT_FILESET_NAME = "snapshot";
  public static final String SEGMENTS_FILESET_NAME = "segments";
  private static final Logger LOG = LoggerFactory.getLogger(GcsBackupStore.class);
  private final ExecutorService executor;
  private final ManifestManager manifestManager;
  private final FileSetManager fileSetManager;
  private final Storage client;

  GcsBackupStore(final GcsBackupConfig config) {
    this(config, buildClient(config));
  }

  GcsBackupStore(final GcsBackupConfig config, final Storage client) {
    final var bucketInfo = BucketInfo.of(config.bucketName());
    final var basePath = Optional.ofNullable(config.basePath()).map(s -> s + "/").orElse("");
    this.client = client;
    executor = Executors.newWorkStealingPool(4);
    manifestManager = new ManifestManager(client, bucketInfo, basePath);
    fileSetManager = new FileSetManager(client, bucketInfo, basePath);
  }

  public static BackupStore of(final GcsBackupConfig config) {
    return new GcsBackupStore(config).logging(LOG, Level.INFO);
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
            manifestManager.markAsFailed(persistedManifest.manifest(), e.getMessage());
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
            throw new RuntimeException(ERROR_MSG_BACKUP_NOT_FOUND.formatted(id));
          }
          return switch (manifest.statusCode()) {
            case FAILED, IN_PROGRESS ->
                throw new RuntimeException(
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
            client.close();
          } catch (final Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  public static Storage buildClient(final GcsBackupConfig config) {
    final var builder =
        StorageOptions.newBuilder()
            .setHost(config.connection().host())
            .setCredentials(config.connection().auth().credentials());

    // Without authentication, we need to set the project ID explicitly
    if (config.connection().auth() instanceof None(final String projectId)) {
      builder.setProjectId(projectId);
    }

    return builder.build().getService();
  }

  public static void validateConfig(final GcsBackupConfig config) {
    try (final var storage = buildClient(config)) {
      try {
        storage.list(config.bucketName(), BlobListOption.pageSize(1));
      } catch (final Exception e) {
        LOG.warn(
            "Unable to verify that the bucket %s exists, initialization will continue as it can be a transient network issue"
                .formatted(config.bucketName()),
            e);
      }
    } catch (final Exception e) {
      throw new ConfigurationException(ERROR_VALIDATION_FAILED.formatted(config), e);
    }
  }
}
