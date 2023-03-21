/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.gcs;

import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Storage.BlobTargetOption;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;
import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupStatusImpl;
import io.camunda.zeebe.backup.gcs.GcsBackupStoreException.ConfigurationException.BucketDoesNotExistException;
import io.camunda.zeebe.backup.gcs.GcsBackupStoreException.UnexpectedManifestState;
import io.camunda.zeebe.backup.gcs.manifest.Manifest;
import io.camunda.zeebe.backup.gcs.manifest.Manifest.InProgressManifest;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public final class GcsBackupStore implements BackupStore {
  public static final ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new Jdk8Module())
          .registerModule(new JavaTimeModule())
          .disable(WRITE_DATES_AS_TIMESTAMPS)
          .setSerializationInclusion(Include.NON_ABSENT);

  private final Storage client;
  private final BucketInfo bucketInfo;
  private final String prefix;
  private final Executor executor;
  private final FileSetManager fileSetManager;

  public GcsBackupStore(final GcsBackupConfig config) {
    this(config, buildClient(config));
  }

  public GcsBackupStore(final GcsBackupConfig config, final Storage client) {
    this.client = client;
    bucketInfo = BucketInfo.of(config.bucketName());
    prefix = Optional.ofNullable(config.basePath()).map(basePath -> basePath + "/").orElse("");
    executor = Executors.newWorkStealingPool(4);
    fileSetManager = new FileSetManager(client, bucketInfo);
  }

  @Override
  public CompletableFuture<Void> save(final Backup backup) {
    return CompletableFuture.runAsync(() -> saveSync(backup), executor);
  }

  private void saveSync(final Backup backup) {
    final var manifest = createInitialManifest(backup);
    saveSnapshotFiles(backup);
    saveSegmentFiles(backup);
    completeManifest(manifest);
  }

  record CurrentManifest(Blob blob, InProgressManifest manifest) {}

  private CurrentManifest createInitialManifest(final Backup backup) {
    final var manifestBlobInfo = manifestBlobInfo(backup.id());
    final var manifest = Manifest.create(backup);
    try {
      final var blob =
          client.create(
              manifestBlobInfo,
              MAPPER.writeValueAsBytes(manifest),
              BlobTargetOption.doesNotExist());
      return new CurrentManifest(blob, manifest);
    } catch (final StorageException e) {
      if (e.getCode() == 412) { // 412 Precondition Failed, blob must already exist
        throw new UnexpectedManifestState(
            "Manifest for backup %s already exists".formatted(backup.id()));
      } else {
        throw e;
      }
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private void completeManifest(final CurrentManifest currentManifest) {
    final var blob = currentManifest.blob();
    final var completed = currentManifest.manifest().complete();
    try {
      client.create(
          blob.asBlobInfo(),
          MAPPER.writeValueAsBytes(completed),
          BlobTargetOption.generationMatch(blob.getGeneration()));
    } catch (final StorageException e) {
      if (e.getCode() == 412) { // 412 Precondition Failed, blob have changed
        throw new UnexpectedManifestState(
            "Manifest for backup %s was modified unexpectedly".formatted(completed.id()));
      }
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private void saveSnapshotFiles(final Backup backup) {
    fileSetManager.save(filePrefix(backup.id()) + "snapshot/", backup.snapshot());
  }

  private void saveSegmentFiles(final Backup backup) {
    fileSetManager.save(filePrefix(backup.id()) + "segments/", backup.segments());
  }

  @Override
  public CompletableFuture<BackupStatus> getStatus(final BackupIdentifier id) {
    return CompletableFuture.supplyAsync(() -> getStatusSync(id));
  }

  private BackupStatus getStatusSync(final BackupIdentifier id) {
    final var blob = client.get(manifestBlobInfo(id).getBlobId());
    if (blob == null) {
      return new BackupStatusImpl(
          id,
          Optional.empty(),
          BackupStatusCode.DOES_NOT_EXIST,
          Optional.empty(),
          Optional.empty(),
          Optional.empty());
    }

    final var content = blob.getContent();
    try {
      final var manifest = MAPPER.readValue(content, Manifest.class);
      return Manifest.toStatus(manifest);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
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
    throw new UnsupportedOperationException();
  }

  @Override
  public CompletableFuture<Void> closeAsync() {
    throw new UnsupportedOperationException();
  }

  private String manifestPrefix(BackupIdentifier id) {
    return prefix + "manifests/" + backupPath(id);
  }

  private String filePrefix(BackupIdentifier id) {
    return prefix + backupPath(id);
  }

  private String backupPath(BackupIdentifier id) {
    return "%s/%s/%s/".formatted(id.partitionId(), id.checkpointId(), id.nodeId());
  }

  private BlobInfo manifestBlobInfo(BackupIdentifier id) {
    return BlobInfo.newBuilder(bucketInfo, manifestPrefix(id) + "manifest.json")
        .setContentType("application/json")
        .build();
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
