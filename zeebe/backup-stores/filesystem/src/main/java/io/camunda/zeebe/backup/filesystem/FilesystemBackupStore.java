/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.filesystem;

import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard;
import io.camunda.zeebe.backup.api.BackupIndexHandle;
import io.camunda.zeebe.backup.api.BackupIndexIdentifier;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupImpl;
import io.camunda.zeebe.backup.common.BackupStatusImpl;
import io.camunda.zeebe.backup.common.BackupStoreException.UnexpectedManifestState;
import io.camunda.zeebe.backup.common.Manifest;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link BackupStore} for local filesystem. Stores all backups in a given baseDir.
 *
 * <p>All created object keys are prefixed by the {@link BackupIdentifier}, with the following
 * scheme: {@code basePath/partitionId/checkpointId/nodeId}.
 */
public final class FilesystemBackupStore implements BackupStore {

  private static final Logger LOG = LoggerFactory.getLogger(FilesystemBackupStore.class);

  private static final String ERROR_MSG_BACKUP_NOT_FOUND =
      "Expected to restore from backup with id '%s', but does not exist.";
  private static final String ERROR_MSG_BACKUP_WRONG_STATE_TO_RESTORE =
      "Expected to restore from completed backup with id '%s', but was in state '%s'";
  private static final String SNAPSHOT_FILESET_NAME = "snapshot";
  private static final String SEGMENTS_FILESET_NAME = "segments";
  private static final String CONTENTS_PATH = "contents";
  private static final String MANIFESTS_PATH = "manifests";

  private final ExecutorService executor;
  private final FileSetManager fileSetManager;
  private final ManifestManager manifestManager;
  private final FilesystemIndexManager indexManager;

  FilesystemBackupStore(final FilesystemBackupConfig config) {
    this(config, Executors.newVirtualThreadPerTaskExecutor());
  }

  FilesystemBackupStore(final FilesystemBackupConfig config, final ExecutorService executor) {
    validateConfig(config);

    this.executor = executor;

    final var contentsDir = Path.of(config.basePath()).resolve(CONTENTS_PATH);
    final var manifestsDir = Path.of(config.basePath()).resolve(MANIFESTS_PATH);
    final var indexDir = Path.of(config.basePath()).resolve("index");
    try {
      FileUtil.ensureDirectoryExists(contentsDir);
      FileUtil.ensureDirectoryExists(manifestsDir);
      FileUtil.ensureDirectoryExists(indexDir);
    } catch (final IOException e) {
      throw new UncheckedIOException(
          "Unable to create backup directory structure; do you have the right permissions or configuration?",
          e);
    }

    fileSetManager = new FileSetManager(contentsDir);
    manifestManager = new ManifestManager(manifestsDir);
    indexManager = new FilesystemIndexManager(indexDir);
  }

  @Override
  public CompletableFuture<Void> save(final Backup backup) {
    return CompletableFuture.runAsync(
        () -> {
          final var manifest = manifestManager.createInitialManifest(backup);
          try {
            fileSetManager.save(backup.id(), SNAPSHOT_FILESET_NAME, backup.snapshot());
            fileSetManager.save(backup.id(), SEGMENTS_FILESET_NAME, backup.segments());
            manifestManager.completeManifest(manifest);
          } catch (final Exception e) {
            manifestManager.markAsFailed(manifest.id(), e.getMessage());
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
    if (!(indexHandle instanceof final FilesystemBackupIndexHandle filesystemIndexFile)) {
      throw new IllegalArgumentException(
          "Expected index file of type %s but got %s: %s"
              .formatted(
                  FilesystemBackupIndexHandle.class.getSimpleName(),
                  indexHandle.getClass().getSimpleName(),
                  indexHandle));
    }
    return CompletableFuture.supplyAsync(() -> indexManager.upload(filesystemIndexFile), executor);
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
              LOG.debug(
                  """
                Expected file system backup store executor to shutdown within a minute, but one \
                task is hanging; will forcefully shutdown, but some backup may not be written \
                properly""");
              executor.shutdownNow();
            }
          } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.debug(
                """
              Interrupted while awaiting shutdown of file system store executor, possible resource \
              leak""",
                e);
          }
        });
  }

  public static void validateConfig(final FilesystemBackupConfig config) {
    if (config.basePath() == null || config.basePath().isBlank()) {
      throw new IllegalArgumentException(
          "Expected a basePath to be provided, but got [%s]".formatted(config.basePath()));
    }
  }

  public static BackupStore of(final FilesystemBackupConfig storeConfig) {
    return new FilesystemBackupStore(storeConfig);
  }
}
