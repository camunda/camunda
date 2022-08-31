/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.s3;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.api.NamedFileSet;
import io.camunda.zeebe.backup.common.BackupImpl;
import io.camunda.zeebe.backup.common.BackupStatusImpl;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import io.camunda.zeebe.backup.s3.S3BackupStoreException.BackupDeletionIncomplete;
import io.camunda.zeebe.backup.s3.S3BackupStoreException.BackupInInvalidStateException;
import io.camunda.zeebe.backup.s3.S3BackupStoreException.BackupReadException;
import io.camunda.zeebe.backup.s3.S3BackupStoreException.MetadataParseException;
import io.camunda.zeebe.backup.s3.S3BackupStoreException.StatusParseException;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Object;

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
 *       <pre>partitionId/checkpointId/nodeId/metadata.json</pre>
 *   <li>A 'status' object, containing the {@link Status}, for example
 *       <pre>partitionId/checkpointId/nodeId/status.json</pre>
 *   <li>Objects for snapshot files, additionally prefixed with 'snapshot', for example
 *       <pre>partitionId/checkpointId/nodeId/snapshots/snapshot-file-1</pre>
 *   <li>Objects for segment files, additionally prefixed with 'segments', for example
 *       <pre>partitionId/checkpointId/nodeId/segments/segment-file-1</pre>
 * </ol>
 */
public final class S3BackupStore implements BackupStore {
  static final String SNAPSHOT_PREFIX = "snapshot/";
  static final String SEGMENTS_PREFIX = "segments/";
  static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new Jdk8Module());

  private static final Logger LOG = LoggerFactory.getLogger(S3BackupStore.class);
  private final S3BackupConfig config;
  private final S3AsyncClient client;

  public S3BackupStore(final S3BackupConfig config) {
    this(config, buildClient(config));
  }

  public S3BackupStore(final S3BackupConfig config, final S3AsyncClient client) {
    this.config = config;
    this.client = client;
  }

  public static String objectPrefix(final BackupIdentifier id) {
    return "%s/%s/%s/".formatted(id.partitionId(), id.checkpointId(), id.nodeId());
  }

  @Override
  public CompletableFuture<Void> save(final Backup backup) {
    LOG.info("Saving {}", backup.id());
    return requireBackupStatus(backup.id(), EnumSet.of(BackupStatusCode.DOES_NOT_EXIST))
        .thenComposeAsync(id -> setStatus(id, Status.inProgress()))
        .thenComposeAsync(
            status -> {
              final var metadata = saveMetadata(backup);
              final var snapshot = saveSnapshotFiles(backup);
              final var segments = saveSegmentFiles(backup);
              return CompletableFuture.allOf(metadata, snapshot, segments);
            })
        .thenComposeAsync(
            ignored -> requireBackupStatus(backup.id(), EnumSet.of(BackupStatusCode.IN_PROGRESS)))
        .thenComposeAsync(content -> setStatus(backup.id(), Status.complete()))
        .exceptionallyComposeAsync(
            throwable ->
                setStatus(backup.id(), Status.failed(throwable))
                    // Mark the returned future as failed.
                    .thenCompose(status -> CompletableFuture.failedStage(throwable)))
        // Discard status, it's either COMPLETED or the future is completed exceptionally
        .thenApply(status -> null);
  }

  @Override
  public CompletableFuture<BackupStatus> getStatus(final BackupIdentifier id) {
    LOG.info("Querying status of {}", id);
    return readStatusObject(id)
        .thenCombine(
            readMetadataObject(id),
            (status, metadata) ->
                (BackupStatus)
                    new BackupStatusImpl(
                        id,
                        Optional.of(metadata.descriptor()),
                        status.statusCode(),
                        status.failureReason()))
        .exceptionally(
            throwable -> {
              // throwable is a `CompletionException`, `getCause` to handle the underlying exception
              if (throwable.getCause() instanceof NoSuchKeyException) {
                // Couldn't find status or metadata, indicating that the backup doesn't exist
                return new BackupStatusImpl(
                    id, Optional.empty(), BackupStatusCode.DOES_NOT_EXIST, Optional.empty());
              } else if (throwable.getCause() instanceof S3BackupStoreException e) {
                // Exception was already wrapped, no need to re-wrap
                throw e;
              } else {
                // Something else happened, we don't know the status of the backup
                throw new BackupReadException("Failed to retrieve backup status", throwable);
              }
            });
  }

  @Override
  public CompletableFuture<Void> delete(final BackupIdentifier id) {
    LOG.info("Deleting {}", id);
    return requireBackupStatus(id, EnumSet.complementOf(EnumSet.of(BackupStatusCode.IN_PROGRESS)))
        .thenComposeAsync(this::listBackupObjects)
        .thenComposeAsync(this::deleteBackupObjects);
  }

  @Override
  public CompletableFuture<Backup> restore(final BackupIdentifier id, final Path targetFolder) {
    LOG.info("Restoring {} to {}", id, targetFolder);
    final var backupPrefix = objectPrefix(id);
    return requireBackupStatus(id, EnumSet.of(BackupStatusCode.COMPLETED))
        .thenComposeAsync(this::readMetadataObject)
        .thenComposeAsync(
            metadata ->
                downloadNamedFileSet(
                        backupPrefix + SEGMENTS_PREFIX, metadata.segmentFileNames(), targetFolder)
                    .thenCombineAsync(
                        downloadNamedFileSet(
                            backupPrefix + SNAPSHOT_PREFIX,
                            metadata.snapshotFileNames(),
                            targetFolder),
                        (segments, snapshot) ->
                            new BackupImpl(id, metadata.descriptor(), snapshot, segments)));
  }

  @Override
  public CompletableFuture<BackupStatusCode> markFailed(
      final BackupIdentifier id, final String failureReason) {
    LOG.info("Marking {} as failed", id);
    return setStatus(id, new Status(BackupStatusCode.FAILED, Optional.of(failureReason)));
  }

  private CompletableFuture<NamedFileSet> downloadNamedFileSet(
      final String sourcePrefix, final Set<String> fileNames, final Path targetFolder) {
    LOG.debug(
        "Downloading {} files from prefix {} to {}", fileNames.size(), sourcePrefix, targetFolder);
    final var downloadedFiles = new ConcurrentHashMap<String, Path>();
    final CompletableFuture<?>[] futures =
        fileNames.stream()
            .map(
                fileName -> {
                  final var path = targetFolder.resolve(fileName);
                  return client
                      .getObject(
                          req -> req.bucket(config.bucketName()).key(sourcePrefix + fileName), path)
                      .thenApply(response -> downloadedFiles.put(fileName, path));
                })
            .toArray(CompletableFuture[]::new);

    return CompletableFuture.allOf(futures)
        .thenApply(ignored -> new NamedFileSetImpl(downloadedFiles));
  }

  private CompletableFuture<BackupIdentifier> requireBackupStatus(
      final BackupIdentifier id, final EnumSet<BackupStatusCode> requiredStatus) {
    LOG.debug("Testing that status of {} is one of {}", id, requiredStatus);
    return getStatus(id)
        .thenApplyAsync(
            status -> {
              if (!requiredStatus.contains(status.statusCode())) {
                throw new BackupInInvalidStateException(
                    "Expected backup to have status "
                        + requiredStatus
                        + " but was "
                        + status.statusCode());
              }
              return id;
            });
  }

  private CompletableFuture<List<ObjectIdentifier>> listBackupObjects(final BackupIdentifier id) {
    LOG.debug("Listing objects of {}", id);
    return client
        .listObjectsV2(req -> req.bucket(config.bucketName()).prefix(objectPrefix(id)))
        .thenApplyAsync(
            objects ->
                objects.contents().stream()
                    .map(S3Object::key)
                    .map(key -> ObjectIdentifier.builder().key(key).build())
                    .toList());
  }

  private CompletableFuture<Void> deleteBackupObjects(
      final Collection<ObjectIdentifier> objectIdentifiers) {
    LOG.debug("Deleting {} objects", objectIdentifiers.size());
    if (objectIdentifiers.isEmpty()) {
      // Nothing to delete, which we must handle because the delete request would be invalid
      return CompletableFuture.completedFuture(null);
    }
    return client
        .deleteObjects(
            req ->
                req.bucket(config.bucketName())
                    .delete(delete -> delete.objects(objectIdentifiers).quiet(true)))
        .thenApplyAsync(
            response -> {
              if (!response.errors().isEmpty()) {
                throw new BackupDeletionIncomplete(
                    "Not all objects belonging to the backup were deleted successfully: "
                        + response.errors());
              }
              return null;
            });
  }

  private CompletableFuture<Status> readStatusObject(final BackupIdentifier id) {
    LOG.debug("Reading status object of {}", id);
    return client
        .getObject(
            req -> req.bucket(config.bucketName()).key(objectPrefix(id) + Status.OBJECT_KEY),
            AsyncResponseTransformer.toBytes())
        .thenApply(
            response -> {
              try {
                return MAPPER.readValue(response.asInputStream(), Status.class);
              } catch (final JsonParseException e) {
                throw new StatusParseException("Failed to parse status object", e);
              } catch (final IOException e) {
                throw new BackupReadException("Failed to read status object", e);
              }
            });
  }

  private CompletableFuture<Metadata> readMetadataObject(final BackupIdentifier id) {
    LOG.debug("Reading metadata object of {}", id);
    return client
        .getObject(
            req -> req.bucket(config.bucketName()).key(objectPrefix(id) + Metadata.OBJECT_KEY),
            AsyncResponseTransformer.toBytes())
        .thenApply(
            response -> {
              try {
                return MAPPER.readValue(response.asInputStream(), Metadata.class);
              } catch (final JsonParseException e) {
                throw new MetadataParseException("Failed to parse metadata object", e);
              } catch (final IOException e) {
                throw new BackupReadException("Failed to read metadata object", e);
              }
            });
  }

  public CompletableFuture<BackupStatusCode> setStatus(
      final BackupIdentifier id, final Status status) {
    LOG.debug("Setting status of {} to {}", id, status);
    final AsyncRequestBody body;
    try {
      body = AsyncRequestBody.fromBytes(MAPPER.writeValueAsBytes(status));
    } catch (final JsonProcessingException e) {
      return CompletableFuture.failedFuture(e);
    }

    return client
        .putObject(
            request ->
                request
                    .bucket(config.bucketName())
                    .key(objectPrefix(id) + Status.OBJECT_KEY)
                    .build(),
            body)
        .thenApply(resp -> status.statusCode());
  }

  private CompletableFuture<PutObjectResponse> saveMetadata(final Backup backup) {
    LOG.debug("Saving metadata for {}", backup.id());
    try {
      return client.putObject(
          request ->
              request
                  .bucket(config.bucketName())
                  .key(objectPrefix(backup.id()) + Metadata.OBJECT_KEY)
                  .contentType("application/json")
                  .build(),
          AsyncRequestBody.fromBytes(MAPPER.writeValueAsBytes(Metadata.of(backup))));
    } catch (final JsonProcessingException e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  private CompletableFuture<Void> saveSnapshotFiles(final Backup backup) {
    LOG.debug("Saving snapshot files for {}", backup.id());
    final var prefix = objectPrefix(backup.id()) + SNAPSHOT_PREFIX;

    final var futures =
        backup.snapshot().namedFiles().entrySet().stream()
            .map(
                snapshotFile ->
                    saveNamedFile(prefix, snapshotFile.getKey(), snapshotFile.getValue()))
            .toList();

    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[] {}));
  }

  private CompletableFuture<Void> saveSegmentFiles(final Backup backup) {
    LOG.debug("Saving segment files for {}", backup.id());
    final var prefix = objectPrefix(backup.id()) + SEGMENTS_PREFIX;
    final var futures =
        backup.segments().namedFiles().entrySet().stream()
            .map(segmentFile -> saveNamedFile(prefix, segmentFile.getKey(), segmentFile.getValue()))
            .toList();

    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[] {}));
  }

  private CompletableFuture<PutObjectResponse> saveNamedFile(
      final String prefix, final String fileName, final Path filePath) {
    LOG.trace("Saving file {}({}) in prefix {}", fileName, filePath, prefix);
    return client.putObject(
        put -> put.bucket(config.bucketName()).key(prefix + fileName),
        AsyncRequestBody.fromFile(filePath));
  }

  private static S3AsyncClient buildClient(final S3BackupConfig config) {
    final var builder = S3AsyncClient.builder();
    config.endpoint().ifPresent(endpoint -> builder.endpointOverride(URI.create(endpoint)));
    config.region().ifPresent(region -> builder.region(Region.of(region)));
    config
        .credentials()
        .ifPresent(
            credentials ->
                builder.credentialsProvider(
                    StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                            credentials.accessKey(), credentials.secretKey()))));
    return builder.build();
  }
}
