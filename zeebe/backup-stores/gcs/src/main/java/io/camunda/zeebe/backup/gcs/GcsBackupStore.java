/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.gcs;

import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Storage.BlobListOption;
import com.google.cloud.storage.StorageOptions;
import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard;
import io.camunda.zeebe.backup.api.BackupRangeMarker;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GcsBackupStore implements BackupStore {
  public static final String ERROR_MSG_BACKUP_NOT_FOUND =
      "Expected to restore from backup with id '%s', but does not exist.";
  public static final String ERROR_MSG_BACKUP_WRONG_STATE_TO_RESTORE =
      "Expected to restore from completed backup with id '%s', but was in state '%s'";
  public static final String ERROR_VALIDATION_FAILED =
      "Invalid configuration for GcsBackupStore: %s";
  private static final Logger LOG = LoggerFactory.getLogger(GcsBackupStore.class);
  private final ExecutorService executor;
  private final ManifestManager manifestManager;
  private final FileSetManager fileSetManager;
  private final Storage client;
  private final BucketInfo bucketInfo;
  private final String basePath;

  GcsBackupStore(final GcsBackupConfig config) {
    this(config, buildClient(config));
  }

  GcsBackupStore(final GcsBackupConfig config, final Storage client) {
    bucketInfo = BucketInfo.of(config.bucketName());
    basePath = Optional.ofNullable(config.basePath()).map(s -> s + "/").orElse("");
    this.client = client;
    executor = Executors.newVirtualThreadPerTaskExecutor();
    manifestManager = new ManifestManager(client, bucketInfo, basePath);
    fileSetManager =
        new FileSetManager(client, bucketInfo, basePath, executor, config.maxConcurrentTransfers());
  }

  public static BackupStore of(final GcsBackupConfig config) {
    return new GcsBackupStore(config);
  }

  @Override
  public CompletableFuture<Void> save(final Backup backup) {
    return CompletableFuture.supplyAsync(
            () -> manifestManager.createInitialManifest(backup), executor)
        .thenComposeAsync(
            persistedManifest -> {
              final var snapshotFuture =
                  CompletableFuture.runAsync(
                      () ->
                          fileSetManager.save(
                              backup.id(), FileSetManager.SNAPSHOT_FILESET_NAME, backup.snapshot()),
                      executor);
              final var segmentsFuture =
                  CompletableFuture.runAsync(
                      () ->
                          fileSetManager.save(
                              backup.id(), FileSetManager.SEGMENTS_FILESET_NAME, backup.segments()),
                      executor);

              return CompletableFuture.allOf(snapshotFuture, segmentsFuture)
                  .handleAsync(
                      (ignored, error) -> {
                        if (error != null) {
                          manifestManager.markAsFailed(
                              persistedManifest.manifest(), error.getMessage());
                          throw new GcsBackupStoreException.UploadException(
                              "Failed to save backup contents", error);
                        }
                        return persistedManifest;
                      },
                      executor);
            },
            executor)
        .thenAcceptAsync(
            persistedManifest -> {
              try {
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
          fileSetManager.delete(id, FileSetManager.SNAPSHOT_FILESET_NAME);
          fileSetManager.delete(id, FileSetManager.SEGMENTS_FILESET_NAME);
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
              final var snapshotFuture =
                  CompletableFuture.supplyAsync(
                      () ->
                          fileSetManager.restore(
                              id,
                              FileSetManager.SNAPSHOT_FILESET_NAME,
                              completed.snapshot(),
                              targetFolder),
                      executor);
              final var segmentsFuture =
                  CompletableFuture.supplyAsync(
                      () ->
                          fileSetManager.restore(
                              id,
                              FileSetManager.SEGMENTS_FILESET_NAME,
                              completed.segments(),
                              targetFolder),
                      executor);

              // Wait for both to complete
              CompletableFuture.allOf(snapshotFuture, segmentsFuture).join();

              yield new BackupImpl(
                  id, manifest.descriptor(), snapshotFuture.join(), segmentsFuture.join());
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
  public CompletableFuture<Collection<BackupRangeMarker>> rangeMarkers(final int partitionId) {
    return CompletableFuture.supplyAsync(
        () -> {
          final var prefix = rangeMarkersPrefix(partitionId);
          final var spliterator =
              Spliterators.spliteratorUnknownSize(
                  client
                      .list(bucketInfo.getName(), BlobListOption.prefix(prefix))
                      .iterateAll()
                      .iterator(),
                  Spliterator.IMMUTABLE);
          return StreamSupport.stream(spliterator, false)
              .map(blob -> blob.getName().substring(prefix.length()))
              .map(BackupRangeMarker::fromName)
              .filter(Objects::nonNull)
              .toList();
        },
        executor);
  }

  @Override
  public CompletableFuture<Void> storeRangeMarker(
      final int partitionId, final BackupRangeMarker marker) {
    return CompletableFuture.runAsync(
        () -> {
          final var blobInfo = rangeMarkerBlobInfo(partitionId, marker);
          client.create(blobInfo, new byte[0]);
        },
        executor);
  }

  @Override
  public CompletableFuture<Void> deleteRangeMarker(
      final int partitionId, final BackupRangeMarker marker) {
    return CompletableFuture.runAsync(
        () -> {
          final var blobInfo = rangeMarkerBlobInfo(partitionId, marker);
          client.delete(blobInfo.getBlobId());
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

  private String rangeMarkersPrefix(final int partitionId) {
    return basePath + "ranges/" + partitionId + "/";
  }

  private BlobInfo rangeMarkerBlobInfo(final int partitionId, final BackupRangeMarker marker) {
    return BlobInfo.newBuilder(
            bucketInfo, rangeMarkersPrefix(partitionId) + BackupRangeMarker.toName(marker))
        .build();
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
            "Unable to verify that the bucket {} exists, initialization will continue as it can be a transient network issue",
            config.bucketName(),
            e);
      }
    } catch (final Exception e) {
      throw new ConfigurationException(ERROR_VALIDATION_FAILED.formatted(config), e);
    }
  }
}
