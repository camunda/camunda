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

  private final GcsBackupConfig config;
  private final Storage client;
  private final BucketInfo bucketInfo;
  private final Executor executor;

  public GcsBackupStore(final GcsBackupConfig config) {
    this(config, buildClient(config));
  }

  public GcsBackupStore(final GcsBackupConfig config, final Storage client) {
    this.config = config;
    this.client = client;
    bucketInfo = BucketInfo.of(config.bucketName());
    executor = Executors.newWorkStealingPool(4);
  }

  @Override
  public CompletableFuture<Void> save(final Backup backup) {
    return CompletableFuture.runAsync(() -> saveSync(backup), executor);
  }

  private void saveSync(final Backup backup) {
    final var inProgress = Manifest.create(backup);
    final var blobInfo = manifestBlobInfo(backup.id());
    final Blob initial;

    try {
      initial =
          client.create(
              blobInfo, MAPPER.writeValueAsBytes(inProgress), BlobTargetOption.doesNotExist());
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

    final var completed = inProgress.complete();
    try {
      client.create(
          blobInfo,
          MAPPER.writeValueAsBytes(completed),
          BlobTargetOption.generationMatch(initial.getGeneration()));
    } catch (final StorageException e) {
      throw new UnexpectedManifestState(
          "Manifest for backup %s was modified unexpectedly".formatted(backup.id()));
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
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

  public static Storage buildClient(GcsBackupConfig config) {
    return StorageOptions.newBuilder()
        .setHost(config.connection().host())
        .setCredentials(config.connection().auth().credentials())
        .build()
        .getService();
  }

  private BlobInfo manifestBlobInfo(BackupIdentifier id) {
    final var base =
        Optional.ofNullable(config.basePath()).map(basePath -> basePath + "/").orElse("");
    final var name =
        "manifests/%s/%s/%s/manifest.json"
            .formatted(id.partitionId(), id.checkpointId(), id.nodeId());

    return BlobInfo.newBuilder(bucketInfo, base + name).setContentType("application/json").build();
  }

  public static void validateConfig(GcsBackupConfig config) throws Exception {
    try (final var storage = buildClient(config)) {
      final var bucket = storage.get(config.bucketName());
      if (bucket == null) {
        throw new BucketDoesNotExistException(config.bucketName());
      }
    }
  }
}
