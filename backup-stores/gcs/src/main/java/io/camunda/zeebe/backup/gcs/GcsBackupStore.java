/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.gcs;

import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupStatusImpl;
import io.camunda.zeebe.backup.gcs.GcsBackupStoreException.ConfigurationException.BucketDoesNotExistException;
import io.camunda.zeebe.backup.gcs.manifest.Manifest;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public final class GcsBackupStore implements BackupStore {
  private final String prefix;
  private final Executor executor;
  private final ManifestManager manifestManager;
  private final FileSetManager fileSetManager;

  public GcsBackupStore(final GcsBackupConfig config) {
    this(config, buildClient(config));
  }

  public GcsBackupStore(final GcsBackupConfig config, final Storage client) {
    final var bucketInfo = BucketInfo.of(config.bucketName());
    prefix = Optional.ofNullable(config.basePath()).map(basePath -> basePath + "/").orElse("");
    executor = Executors.newWorkStealingPool(4);
    manifestManager = new ManifestManager(client, bucketInfo);
    fileSetManager = new FileSetManager(client, bucketInfo);
  }

  @Override
  public CompletableFuture<Void> save(final Backup backup) {
    return CompletableFuture.runAsync(
        () -> {
          final var manifest =
              manifestManager.createInitialManifest(manifestPrefix(backup.id()), backup);
          try {
            fileSetManager.save(contentPrefix(backup.id()) + "snapshot/", backup.snapshot());
            fileSetManager.save(contentPrefix(backup.id()) + "segments/", backup.segments());
            manifestManager.completeManifest(manifest);
          } catch (final Exception e) {
            manifestManager.markAsFailed(manifestPrefix(backup.id()), backup.id(), e.getMessage());
            throw e;
          }
        },
        executor);
  }

  @Override
  public CompletableFuture<BackupStatus> getStatus(final BackupIdentifier id) {
    return CompletableFuture.supplyAsync(
        () -> {
          final var manifest = manifestManager.getManifest(manifestPrefix(id));
          if (manifest == null) {
            return new BackupStatusImpl(
                id,
                Optional.empty(),
                BackupStatusCode.DOES_NOT_EXIST,
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
          } else {
            return Manifest.toStatus(manifest);
          }
        },
        executor);
  }

  @Override
  public CompletableFuture<Collection<BackupStatus>> list(final BackupIdentifierWildcard wildcard) {
    throw new UnsupportedOperationException();
  }

  @Override
  public CompletableFuture<Void> delete(final BackupIdentifier id) {
    throw new UnsupportedOperationException();
  }

  @Override
  public CompletableFuture<Backup> restore(final BackupIdentifier id, final Path targetFolder) {
    throw new UnsupportedOperationException();
  }

  @Override
  public CompletableFuture<BackupStatusCode> markFailed(
      final BackupIdentifier id, final String failureReason) {
    return CompletableFuture.supplyAsync(
        () -> {
          manifestManager.markAsFailed(manifestPrefix(id), id, failureReason);
          return BackupStatusCode.FAILED;
        },
        executor);
  }

  @Override
  public CompletableFuture<Void> closeAsync() {
    throw new UnsupportedOperationException();
  }

  private String manifestPrefix(final BackupIdentifier id) {
    return prefix + "manifests/" + backupPath(id);
  }

  private String contentPrefix(final BackupIdentifier id) {
    return prefix + "contents/" + backupPath(id);
  }

  private String backupPath(final BackupIdentifier id) {
    return "%s/%s/%s/".formatted(id.partitionId(), id.checkpointId(), id.nodeId());
  }

  public static Storage buildClient(final GcsBackupConfig config) {
    return StorageOptions.newBuilder()
        .setHost(config.connection().host())
        .setCredentials(config.connection().auth().credentials())
        .build()
        .getService();
  }

  public static void validateConfig(final GcsBackupConfig config) throws Exception {
    try (final var storage = buildClient(config)) {
      final var bucket = storage.get(config.bucketName());
      if (bucket == null) {
        throw new BucketDoesNotExistException(config.bucketName());
      }
    }
  }
}
