/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.s3;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStore;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

/**
 * {@link BackupStore} for S3. Stores all backups in a given bucket.
 *
 * <p>All created object keys are prefixed by the {@link BackupIdentifier}, with the following
 * scheme: {@code partitionId/checkpointId/nodeId}
 *
 * <p>Each backup contains:
 *
 * <ol>
 *   <li>A 'metadata' object, containing {@link Metadata} serialized as JSON, for example
 *       <pre>partitionId/checkpointId/nodeId/metadata</pre>
 *   <li>A 'status' object, containing the {@link BackupStatus}, for example
 *       <pre>partitionId/checkpointId/nodeId/status</pre>
 *   <li>Objects for snapshot files, additionally prefixed with 'snapshot', for example
 *       <pre>partitionId/checkpointId/nodeId/snapshots/snapshot-file-1</pre>
 *   <li>Objects for segment files, additionally prefixed with 'segments', for example
 *       <pre>partitionId/checkpointId/nodeId/segments/segment-file-1</pre>
 * </ol>
 */
public final class S3BackupStore implements BackupStore {

  public static final String SNAPSHOT_PREFIX = "snapshot/";

  static final ObjectMapper MAPPER = new ObjectMapper();

  private final S3BackupConfig config;
  private final S3AsyncClient client;

  public S3BackupStore(final S3BackupConfig config, final S3AsyncClient client) {
    this.config = config;
    this.client = client;
  }

  @Override
  public CompletableFuture<Void> save(final Backup backup) {
    final var metadata = saveMetadata(backup);
    final var snapshot = saveSnapshotFiles(backup);
    return CompletableFuture.allOf(metadata, snapshot);
  }

  @Override
  public CompletableFuture<BackupStatus> getStatus(final BackupIdentifier id) {
    throw new UnsupportedOperationException();
  }

  @Override
  public CompletableFuture<Void> delete(final BackupIdentifier id) {
    throw new UnsupportedOperationException();
  }

  @Override
  public CompletableFuture<Backup> restore(final BackupIdentifier id) {
    throw new UnsupportedOperationException();
  }

  @Override
  public CompletableFuture<Void> markFailed(final BackupIdentifier id) {
    throw new UnsupportedOperationException();
  }

  private CompletableFuture<PutObjectResponse> saveMetadata(Backup backup) {
    try {
      return client.putObject(
          request ->
              request
                  .bucket(config.bucketName())
                  .key(objectPrefix(backup.id()) + Metadata.OBJECT_KEY)
                  .contentType("application/json")
                  .build(),
          AsyncRequestBody.fromBytes(MAPPER.writeValueAsBytes(Metadata.of(backup))));
    } catch (JsonProcessingException e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  private CompletableFuture<Void> saveSnapshotFiles(Backup backup) {
    final var prefix = objectPrefix(backup.id()) + SNAPSHOT_PREFIX;

    final var futures =
        backup.snapshot().namedFiles().entrySet().stream()
            .map(
                snapshotFile ->
                    saveNamedFile(prefix, snapshotFile.getKey(), snapshotFile.getValue()))
            .toList();

    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[] {}));
  }

  private CompletableFuture<PutObjectResponse> saveNamedFile(
      final String prefix, String fileName, Path filePath) {

    return client.putObject(
        put -> put.bucket(config.bucketName()).key(prefix + fileName),
        AsyncRequestBody.fromFile(filePath));
  }

  public static String objectPrefix(BackupIdentifier id) {
    return "%s/%s/%s/".formatted(id.partitionId(), id.checkpointId(), id.nodeId());
  }
}
